package com.franckyl.yav1.aircraft;

import android.content.Context;
import android.util.Log;

import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.events.GpsEvent;
import com.franckyl.yav1.poi.Announcer;
import com.franckyl.yav1.poi.GeoMath;
import com.squareup.otto.Subscribe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [P2-ADSB] ADS-B aircraft-enforcement awareness.
 *
 * While the feature is enabled and the vehicle is GPS-moving, polls a free
 * no-key ADS-B point API (~30 s cadence) around the current position on a
 * background thread, matches the aircraft against the enforcement watchlist
 * (asset + user file) and applies the low/slow/loitering heuristic from
 * {@link AircraftTracker}. Alerts route through {@link Announcer} once per
 * aircraft per 10 minutes. Everything fails soft.
 *
 * Debug: preference "adsb_debug_fixture" replaces the HTTP call with the
 * canned asset assets/aircraft/fixture_point.json so the whole pipeline can
 * be validated offline (emulator).
 */
public class AircraftMonitor
{
    public static final String LOG_TAG = "Valentine ADSB";

    private static final long POLL_INTERVAL_MS = 30 * 1000L;
    private static final double MIN_SPEED_MS   = 2.0;
    private static final int  DEF_RADIUS_NM    = 10;

    private static AircraftMonitor sInstance = null;

    private final Context             mContext;
    private final AdsbAggregator      mAggregator = new AdsbAggregator();
    private final AircraftTracker     mTracker = new AircraftTracker();
    private EnforcementWatchlist      mWatchlist = null;

    private final ExecutorService mExecutor;
    private final AtomicBoolean   mInFlight = new AtomicBoolean(false);
    private volatile long         mLastPollMs = 0;

    // last-seen status rows for the debug/status surface
    private final List<String> mStatusLines = new ArrayList<String>();
    private volatile long      mLastResultMs = 0;

    // fixture-mode poll counter (drives the synthetic loiter rotation)
    private int mFixturePolls = 0;

    private AircraftMonitor(Context context)
    {
        mContext = context;
        mExecutor = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "YaV1Adsb");
                t.setDaemon(true);
                return t;
            }
        });
    }

    /** Create the singleton and register it on the event bus. */
    public static synchronized void init(Context context)
    {
        if(sInstance != null)
            return;

        sInstance = new AircraftMonitor(context);
        YaV1.getEventBus().register(sInstance);
        Log.d(LOG_TAG, "AircraftMonitor registered");
    }

    public static AircraftMonitor getInstance()
    {
        return sInstance;
    }

    /** Snapshot of the last poll results for the status preference. */
    public List<String> getStatusLines()
    {
        synchronized(mStatusLines)
        {
            return new ArrayList<String>(mStatusLines);
        }
    }

    public long getLastResultAge()
    {
        return mLastResultMs == 0 ? -1 : System.currentTimeMillis() - mLastResultMs;
    }

    // ------------------------------------------------------------ bus event

    @Subscribe
    public void onGpsEvent(GpsEvent event)
    {
        if(event.getType() != GpsEvent.Type.UPDATE)
            return;

        if(YaV1.sPrefs == null || !YaV1.sPrefs.getBoolean("adsb_enable", false))
            return;

        if(!YaV1CurrentPosition.isValid || YaV1CurrentPosition.speed < MIN_SPEED_MS)
            return;

        // fixture mode polls faster so the pipeline can be demonstrated quickly
        long interval = (YaV1.sPrefs.getBoolean("adsb_debug_fixture", false)
                            ? 10 * 1000L : POLL_INTERVAL_MS);

        long now = System.currentTimeMillis();
        if(now - mLastPollMs < interval)
            return;

        if(!mInFlight.compareAndSet(false, true))
            return;

        mLastPollMs = now;

        final double lat = YaV1CurrentPosition.lat;
        final double lon = YaV1CurrentPosition.lon;

        mExecutor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    poll(lat, lon);
                }
                catch(Exception e)
                {
                    Log.d(LOG_TAG, "poll failed: " + e);
                }
                finally
                {
                    mInFlight.set(false);
                }
            }
        });
    }

    // -------------------------------------------------------------- polling

    private void poll(double lat, double lon)
    {
        int radiusNm = DEF_RADIUS_NM;
        try
        {
            radiusNm = Integer.parseInt(YaV1.sPrefs.getString("adsb_radius_nm",
                                        String.valueOf(DEF_RADIUS_NM)));
        }
        catch(NumberFormatException ignored)
        {
        }

        boolean heuristicOn = YaV1.sPrefs.getBoolean("adsb_heuristic", true);
        boolean fixture     = YaV1.sPrefs.getBoolean("adsb_debug_fixture", false);

        long now = System.currentTimeMillis();
        List<Aircraft> list;

        if(fixture)
        {
            String json = readFixture();
            if(json == null)
            {
                Log.d(LOG_TAG, "no fixture data");
                return;
            }
            list = AdsbParser.parse(json);
        }
        else
        {
            // multi-source aggregation: one feed per poll (rotation),
            // merged across the freshness window
            list = mAggregator.poll(lat, lon, radiusNm, now);

            if(list.isEmpty())
                Log.d(LOG_TAG, "no ADS-B data (feeds: " + mAggregator.healthLine() + ")");
        }

        if(fixture)
        {
            // synthetic loiter: rotate every track by 90 degrees per poll so
            // the heuristic path can be exercised with static canned data
            mFixturePolls++;
            for(Aircraft ac: list)
            {
                if(!Double.isNaN(ac.trackDeg))
                    ac.trackDeg = (ac.trackDeg + mFixturePolls * 90.0) % 360.0;
            }
        }

        ensureWatchlist();

        List<String> status = new ArrayList<String>();
        int alerts = 0;

        for(Aircraft ac: list)
        {
            double distM = GeoMath.distanceMeters(lat, lon, ac.lat, ac.lon);

            EnforcementWatchlist.Entry wl = (mWatchlist == null ? null : mWatchlist.match(ac));
            AircraftTracker.Assessment as = mTracker.assess(ac, wl != null, now);

            String line = String.format(Locale.US, "%s %s %s alt=%s gs=%s%s %s%s",
                    ac.bestIdent(),
                    ac.type.isEmpty() ? "?" : ac.type,
                    distPhrase(distM),
                    ac.onGround ? "ground" : (ac.altFt == Integer.MIN_VALUE ? "?" : ac.altFt + "ft"),
                    Double.isNaN(ac.gsKt) ? "?" : (int) ac.gsKt + "kt",
                    ac.sourceCount >= 2 ? " x" + ac.sourceCount : "",
                    wl != null ? "[WATCHLIST" + (wl.lowConfidence() ? "(low)" : "")
                                 + ": " + wl.agency + "]" : "",
                    as.category == AircraftTracker.CAT_HEURISTIC ? "[heuristic suspect]" : "");
            status.add(line);

            boolean alertWorthy =
                    (as.category == AircraftTracker.CAT_WATCHLIST)
                 || (heuristicOn && as.category == AircraftTracker.CAT_HEURISTIC);

            if(alertWorthy && mTracker.shouldAlert(ac, now))
            {
                alerts++;
                String phrase;
                String banner;

                if(as.category == AircraftTracker.CAT_WATCHLIST)
                {
                    // low-confidence entries (patrol rotorcraft etc.) are
                    // announced as tentative, like the heuristic path
                    String lead = wl.lowConfidence() ? "Possible aerial enforcement aircraft, "
                                                     : "Aerial enforcement aircraft, ";
                    phrase = lead + wl.agency + ", " + distPhrase(distM);
                    banner = (wl.lowConfidence() ? "Possible enforcement aircraft: "
                                                 : "Enforcement aircraft: ")
                             + wl.agency + " " + ac.bestIdent()
                             + ", " + distPhrase(distM);
                }
                else
                {
                    phrase = "Possible aerial enforcement aircraft, " + distPhrase(distM);
                    banner = "Possible aerial enforcement (heuristic): " + ac.bestIdent()
                             + " " + (ac.altFt != Integer.MIN_VALUE ? ac.altFt + " ft, " : "")
                             + distPhrase(distM);
                }

                Log.d(LOG_TAG, "ALERT " + banner);
                Announcer.announce(mContext, phrase, banner, styleFromPref());
            }
        }

        mTracker.prune(now);

        synchronized(mStatusLines)
        {
            mStatusLines.clear();
            mStatusLines.addAll(status);
        }
        mLastResultMs = now;

        Log.d(LOG_TAG, "poll done: " + list.size() + " aircraft, " + alerts + " alert(s), "
                        + (fixture ? "fixture" : radiusNm + " nm"));
    }

    // ------------------------------------------------------------ watchlist

    private synchronized void ensureWatchlist()
    {
        if(mWatchlist != null)
            return;

        EnforcementWatchlist wl = new EnforcementWatchlist();

        // curated asset
        try
        {
            InputStream in = mContext.getAssets().open("aircraft/enforcement_hex.csv");
            int n = wl.load(new BufferedReader(new InputStreamReader(in, "UTF-8")));
            in.close();
            Log.d(LOG_TAG, "watchlist asset: " + n + " entries");
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "watchlist asset failed: " + e);
        }

        // user extension file (merged, wins on duplicate hex)
        try
        {
            File dir = new File(YaV1.getStorageRootDir(), "aircraft");
            File user = new File(dir, "enforcement_user.csv");

            if(!user.exists() && (dir.isDirectory() || dir.mkdirs()))
                writeUserTemplate(user);

            if(user.exists())
            {
                InputStreamReader in = new InputStreamReader(new FileInputStream(user), "UTF-8");
                int n = wl.load(new BufferedReader(in));
                in.close();
                Log.d(LOG_TAG, "watchlist user file: " + n + " entries");
            }
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "watchlist user file failed: " + e);
        }

        mWatchlist = wl;
    }

    private void writeUserTemplate(File f)
    {
        try
        {
            Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            w.write("# YaV1 user watchlist extension\n");
            w.write("# One aircraft per line: icao_hex,registration,agency[,model]\n");
            w.write("# Example:\n");
            w.write("# A12345,N123AB,My County Sheriff,Cessna 182T\n");
            w.close();
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "user template failed: " + e);
        }
    }

    // -------------------------------------------------------------- helpers

    private String readFixture()
    {
        try
        {
            InputStream in = mContext.getAssets().open("aircraft/fixture_point.json");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine()) != null)
                sb.append(line);
            br.close();
            return sb.toString();
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "fixture read failed: " + e);
            return null;
        }
    }

    private String distPhrase(double meters)
    {
        if(YaV1.sCurrUnit == 1)
        {
            double miles = meters / 1609.344;
            if(miles < 0.2)
                return "overhead";
            return String.format(Locale.US, "%.1f miles", miles);
        }

        double km = meters / 1000.0;
        if(km < 0.3)
            return "overhead";
        return String.format(Locale.US, "%.1f kilometers", km);
    }

    private int styleFromPref()
    {
        String s = YaV1.sPrefs.getString("adsb_alert_style", "0");
        if("1".equals(s))
            return Announcer.STYLE_TTS_ONLY;
        if("2".equals(s))
            return Announcer.STYLE_SOUND_ONLY;
        return Announcer.STYLE_TTS_AND_SOUND;
    }
}
