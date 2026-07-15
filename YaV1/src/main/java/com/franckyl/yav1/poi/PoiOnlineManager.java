package com.franckyl.yav1.poi;

import android.util.Log;

import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.events.GpsEvent;
import com.squareup.otto.Subscribe;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [P2-POI] Network-driven camera data: keeps the POI store stocked with
 * cameras around the vehicle without any manual file import.
 *
 * While POI alerts and the online option are enabled and the vehicle is
 * GPS-moving, checks the ~4.4 km tile the vehicle is in; when that tile
 * was never fetched (or its 7-day TTL expired) it fetches all enabled
 * {@link PoiOnlineSource}s around the position on a background thread
 * (FETCH_RADIUS_M, currently one Overpass union query), merges the
 * result into the {@link PoiOnlineCache}, writes the accumulated set as
 * a generated file in the {@link PoiStore} ("OSM online cameras" -
 * visible and toggleable like any imported file) and rebuilds the alert
 * index. Everything fails soft; offline the store simply keeps serving
 * the last fetched data (offline-first).
 *
 * Rate limits follow the PSL provider pattern: one request per
 * MIN_REQUEST_INTERVAL_MS at most, single-flight, plus the tile TTL.
 * At highway speed a 4.4 km tile boundary is crossed every ~2 minutes,
 * so steady-state load on Overpass is well under one request/minute.
 *
 * Preferences (pref_poi.xml):
 *   poi_online_enable - master toggle for network fetching, default off
 *   poi_online_cams   - speed / red-light cameras (OSM), default on
 *   poi_online_alpr   - ALPR / license plate readers (OSM), default on
 */
public class PoiOnlineManager
{
    public static final String LOG_TAG = "Valentine POI";

    public static final String GENERATED_NAME = "OSM online cameras";
    public static final String META_FILE_NAME = "online_meta.json";

    public static final int  FETCH_RADIUS_M          = 8000;
    public static final long MIN_REQUEST_INTERVAL_MS = 60 * 1000L;
    public static final double MIN_SPEED_MS          = 3.0;

    public static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS    = 15000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private static PoiOnlineManager sInstance = null;

    private final PoiOnlineSource mSource = new OverpassCameraSource();
    private final PoiOnlineCache  mCache  = new PoiOnlineCache();

    private final ExecutorService mExecutor;
    private final AtomicBoolean   mInFlight = new AtomicBoolean(false);
    private volatile long         mLastFetchMs = 0;
    private volatile boolean      mLoaded      = false;

    private PoiOnlineManager()
    {
        mExecutor = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "YaV1-POI-Online");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public static synchronized void init()
    {
        if(sInstance != null)
            return;

        sInstance = new PoiOnlineManager();
        YaV1.getEventBus().register(sInstance);
        Log.d(LOG_TAG, "PoiOnlineManager registered");
    }

    public static PoiOnlineManager getInstance()
    {
        return sInstance;
    }

    // ------------------------------------------------------------ bus event

    @Subscribe
    public void onGpsEvent(GpsEvent event)
    {
        try
        {
            if(event == null || event.getType() != GpsEvent.Type.UPDATE)
                return;

            if(YaV1.sPrefs == null
               || !YaV1.sPrefs.getBoolean("poi_enable", false)
               || !YaV1.sPrefs.getBoolean("poi_online_enable", false))
                return;

            if(!YaV1CurrentPosition.isValid || YaV1CurrentPosition.speed < MIN_SPEED_MS)
                return;

            final boolean wantCams = YaV1.sPrefs.getBoolean("poi_online_cams", true);
            final boolean wantAlpr = YaV1.sPrefs.getBoolean("poi_online_alpr", true);
            if(!wantCams && !wantAlpr)
                return;

            final double lat = YaV1CurrentPosition.lat;
            final double lon = YaV1CurrentPosition.lon;
            long now = System.currentTimeMillis();

            // tile freshness + rate limit + single flight
            String tile = PoiOnlineCache.tileKey(lat, lon);
            if(mLoaded && mCache.isTileFresh(tile, now))
                return;
            if(now - mLastFetchMs < MIN_REQUEST_INTERVAL_MS)
                return;
            if(!mInFlight.compareAndSet(false, true))
                return;

            mLastFetchMs = now;

            mExecutor.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        ensureLoaded();
                        fetch(lat, lon, wantCams, wantAlpr);
                    }
                    catch(Exception e)
                    {
                        Log.d(LOG_TAG, "online fetch failed soft: " + e);
                    }
                    finally
                    {
                        mInFlight.set(false);
                    }
                }
            });
        }
        catch(Exception exc)
        {
            // never let this reach the bus
        }
    }

    // -------------------------------------------------------------- fetching

    private void ensureLoaded()
    {
        if(mLoaded)
            return;

        long now = System.currentTimeMillis();
        File dir = storeDir();

        if(dir != null)
            mCache.load(new File(dir, META_FILE_NAME), now);

        // seed accumulated POIs from the previously generated store file
        PoiAlertManager pam = PoiAlertManager.getInstance();
        if(pam != null)
        {
            for(PoiFile pf : pam.getStore().getFiles())
            {
                if(GENERATED_NAME.equals(pf.name) && pf.pois != null)
                    mCache.seedFromStore(pf.pois);
            }
        }

        mLoaded = true;
        Log.d(LOG_TAG, "online cache loaded: " + mCache.poiCount() + " POIs, "
                        + mCache.tileCount() + " fresh tile(s)");
    }

    private void fetch(double lat, double lon, boolean wantCams, boolean wantAlpr)
    {
        long now = System.currentTimeMillis();

        String query = mSource.buildQuery(lat, lon, FETCH_RADIUS_M, wantCams, wantAlpr);
        String body  = httpPost(query);
        if(body == null)
            return;

        List<Poi> pois  = mSource.parse(body, wantCams, wantAlpr);
        int       added = mCache.merge(pois);

        // every tile the fetch radius fully covers is now fresh; marking
        // just the vehicle tile keeps it simple and correct (neighbors
        // refetch when actually entered, and the dedupe absorbs overlap)
        mCache.markTileFetched(PoiOnlineCache.tileKey(lat, lon), now);

        File dir = storeDir();
        if(dir != null)
            mCache.save(new File(dir, META_FILE_NAME));

        Log.d(LOG_TAG, "online fetch " + String.format(Locale.US, "%.5f,%.5f", lat, lon)
                       + " got=" + pois.size() + " new=" + added
                       + " total=" + mCache.poiCount());

        // persist + reindex only when something changed
        if(added > 0)
        {
            PoiAlertManager pam = PoiAlertManager.getInstance();
            if(pam != null)
            {
                pam.getStore().putGenerated(GENERATED_NAME, mCache.snapshot(),
                                            mSource.name() + " (overpass)");
                pam.rebuildIndexNow();
            }
        }
    }

    private File storeDir()
    {
        PoiAlertManager pam = PoiAlertManager.getInstance();
        return pam != null ? pam.getStore().getDir() : null;
    }

    // ------------------------------------------------------------------ http

    private String httpPost(String query)
    {
        HttpURLConnection conn = null;

        try
        {
            conn = (HttpURLConnection) new URL(OVERPASS_URL).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent", "YaV1/2.1 (+https://github.com/austinsasko/yav1; poi-cameras)");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            byte out[] = ("data=" + URLEncoder.encode(query, "UTF-8")).getBytes(Charset.forName("UTF-8"));
            OutputStream os = conn.getOutputStream();
            try
            {
                os.write(out);
            }
            finally
            {
                os.close();
            }

            if(conn.getResponseCode() != 200)
            {
                Log.d(LOG_TAG, "online fetch HTTP " + conn.getResponseCode());
                return null;
            }

            InputStream in = conn.getInputStream();
            try
            {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte chunk[] = new byte[8192];
                int  n;
                while((n = in.read(chunk)) > 0)
                {
                    buf.write(chunk, 0, n);
                    if(buf.size() > MAX_RESPONSE_BYTES)
                    {
                        Log.d(LOG_TAG, "online fetch response too large, dropping");
                        return null;
                    }
                }

                return new String(buf.toByteArray(), Charset.forName("UTF-8"));
            }
            finally
            {
                in.close();
            }
        }
        catch(Exception exc)
        {
            Log.d(LOG_TAG, "online fetch failed soft: " + exc);
            return null;
        }
        finally
        {
            if(conn != null)
                conn.disconnect();
        }
    }
}
