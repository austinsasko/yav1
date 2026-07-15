package com.franckyl.yav1.psl;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * [P1-PSL] Speed limit provider backed by the Overpass API (OpenStreetMap).
 *
 * Queries maxspeed-tagged ways within ~75m of the location and picks the
 * way best aligned with the travel bearing. Results are cached per ~150m
 * tile (see SpeedLimitCache), in memory and on disk.
 *
 * getSpeedLimitKph() never blocks: it answers from the cache and, when the
 * tile is missing or stale, schedules a background fetch subject to a hard
 * rate limit (1 request / 20s, and only after moving >100m). All failures
 * are soft: no answer just means "unknown".
 */
public class OverpassSpeedLimitProvider implements SpeedLimitProvider
{
    private static final String TAG = "Valentine PSL";

    public static final String OVERPASS_URL   = "https://overpass-api.de/api/interpreter";
    public static final int    SEARCH_RADIUS_M = 75;

    /** minimum time between two Overpass requests */
    public static final long   MIN_REQUEST_INTERVAL_MS = 20000;

    /** minimum movement between two Overpass requests */
    public static final double MIN_REQUEST_MOVE_M      = 100.0;

    /** A cached limit is usable only while the vehicle still matches its road. */
    public static final double MAX_CACHED_ROAD_DISTANCE_M = 25.0;
    public static final double MAX_CACHED_BEARING_DIFF_DEG = 30.0;

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS    = 10000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    /** one maxspeed-tagged way from the Overpass response */
    public static class Way
    {
        public final Integer  limitKph;   // parsed, null when unusable
        public final double[] lats;
        public final double[] lons;

        public Way(Integer limitKph, double[] lats, double[] lons)
        {
            this.limitKph = limitKph;
            this.lats     = lats;
            this.lons     = lons;
        }
    }

    private final SpeedLimitCache mCache = new SpeedLimitCache();
    private final File            mCacheFile;
    private final ExecutorService mExecutor;

    private boolean mLoaded        = false;
    private boolean mInFlight      = false;
    private long    mLastRequestMs = 0;
    private double  mLastRequestLat;
    private double  mLastRequestLon;
    private boolean mHasRequested  = false;

    public OverpassSpeedLimitProvider(File cacheFile)
    {
        mCacheFile = cacheFile;
        mExecutor  = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "YaV1-PSL-Overpass");
                t.setDaemon(true);
                return t;
            }
        });

        // warm the cache from disk off the caller's thread
        mExecutor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                ensureLoaded();
            }
        });
    }

    @Override
    public Integer getSpeedLimitKph(double lat, double lon, float bearing)
    {
        long now   = System.currentTimeMillis();
        String key = SpeedLimitCache.tileKey(lat, lon);

        SpeedLimitCache.Entry e = mCache.get(key);
        boolean roadMismatch = e != null && e.limitKph != null
                            && !cachedRoadMatches(e, lat, lon, bearing);

        // missing or stale: try to refresh in the background, but still
        // answer with what we have when it still belongs to the current road.
        // A mismatch fails safe (unknown => no PSL muting) while refreshing.

        if(!SpeedLimitCache.isFresh(e, now) || roadMismatch)
            maybeScheduleFetch(lat, lon, bearing, roadMismatch);

        return e != null && !roadMismatch ? e.limitKph : null;
    }

    // -- background fetch ------------------------------------------------

    private synchronized void maybeScheduleFetch(final double lat, final double lon,
                                                 final float bearing, boolean roadMismatch)
    {
        if(mInFlight)
            return;

        long now = System.currentTimeMillis();

        if(mHasRequested)
        {
            if(now - mLastRequestMs < MIN_REQUEST_INTERVAL_MS)
                return;
            // Ordinarily movement gates requests. A cached road mismatch may happen
            // at an intersection or on a frontage road without 100m of movement, so
            // allow a refresh after the time limit rather than leaving the tile stuck.
            if(!roadMismatch
                    && distanceM(mLastRequestLat, mLastRequestLon, lat, lon) <= MIN_REQUEST_MOVE_M)
                return;
        }

        mInFlight      = true;
        mHasRequested  = true;
        mLastRequestMs = now;
        mLastRequestLat = lat;
        mLastRequestLon = lon;

        mExecutor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    fetch(lat, lon, bearing);
                }
                catch(Throwable t)
                {
                    Log.d(TAG, "Overpass fetch failed soft: " + t);
                }
                finally
                {
                    synchronized(OverpassSpeedLimitProvider.this)
                    {
                        mInFlight = false;
                    }
                }
            }
        });
    }

    private void fetch(double lat, double lon, float bearing)
    {
        ensureLoaded();

        String body = httpPost(buildQuery(lat, lon));
        if(body == null)
            return;

        List<Way> ways = parseWays(body);
        Way selected = selectWay(ways, lat, lon, bearing);
        Integer limit = (selected == null ? null : selected.limitKph);

        mCache.put(SpeedLimitCache.tileKey(lat, lon), limit, System.currentTimeMillis(),
                   selected == null ? null : selected.lats,
                   selected == null ? null : selected.lons);
        Log.d(TAG, "Overpass result " + String.format(Locale.US, "%.5f,%.5f", lat, lon)
                   + " ways=" + ways.size() + " limitKph=" + limit);

        if(limit != null && mCacheFile != null)
            mCache.save(mCacheFile);
    }

    private synchronized void ensureLoaded()
    {
        if(mLoaded)
            return;

        mLoaded = true;
        if(mCacheFile != null)
            mCache.load(mCacheFile, System.currentTimeMillis());
    }

    /** visible for tests / debug */
    public SpeedLimitCache getCache()
    {
        return mCache;
    }

    // -- Overpass query / HTTP -------------------------------------------

    public static String buildQuery(double lat, double lon)
    {
        return String.format(Locale.US,
            "[out:json][timeout:8];way(around:%d,%.6f,%.6f)[\"highway\"][\"maxspeed\"];out tags geom;",
            SEARCH_RADIUS_M, lat, lon);
    }

    private String httpPost(String query)
    {
        HttpURLConnection conn = null;

        try
        {
            conn = (HttpURLConnection) new URL(OVERPASS_URL).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent", "YaV1/2.1 (+https://github.com/austinsasko/yav1; psl-muting)");
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
                Log.d(TAG, "Overpass HTTP " + conn.getResponseCode());
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
                        Log.d(TAG, "Overpass response too large, dropping");
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
            Log.d(TAG, "Overpass request failed soft: " + exc);
            return null;
        }
        finally
        {
            if(conn != null)
                conn.disconnect();
        }
    }

    // -- response parsing --------------------------------------------------

    /** parse an Overpass JSON response into ways; fail soft to empty */
    public static List<Way> parseWays(String json)
    {
        List<Way> ways = new ArrayList<Way>();

        try
        {
            JSONObject root    = new JSONObject(json);
            JSONArray elements = root.optJSONArray("elements");
            if(elements == null)
                return ways;

            for(int i = 0; i < elements.length(); i++)
            {
                JSONObject el = elements.optJSONObject(i);
                if(el == null || !"way".equals(el.optString("type")))
                    continue;

                JSONObject tags = el.optJSONObject("tags");
                JSONArray  geom = el.optJSONArray("geometry");
                if(tags == null || geom == null || geom.length() < 2)
                    continue;

                Integer limit = parseMaxspeed(tags.optString("maxspeed", null));

                double lats[] = new double[geom.length()];
                double lons[] = new double[geom.length()];
                boolean ok    = true;

                for(int j = 0; j < geom.length(); j++)
                {
                    JSONObject pt = geom.optJSONObject(j);
                    if(pt == null || !pt.has("lat") || !pt.has("lon"))
                    {
                        ok = false;
                        break;
                    }
                    lats[j] = pt.optDouble("lat");
                    lons[j] = pt.optDouble("lon");
                }

                if(ok)
                    ways.add(new Way(limit, lats, lons));
            }
        }
        catch(Exception exc)
        {
            // malformed response: no ways
        }

        return ways;
    }

    /**
     * Parse an OSM maxspeed value to km/h.
     *
     * "55 mph" / "55mph" -> 89, "80" -> 80, "50 km/h" -> 50,
     * "signals" / "none" / "walk" / "variable" / junk -> null.
     * Composite values ("40;30", "40 mph;school") use the first part.
     */
    public static Integer parseMaxspeed(String raw)
    {
        if(raw == null)
            return null;

        String s = raw.trim().toLowerCase(Locale.US);
        if(s.isEmpty())
            return null;

        // composite / conditional: keep the first component
        int semi = s.indexOf(';');
        if(semi >= 0)
            s = s.substring(0, semi).trim();

        if(s.isEmpty() || s.equals("none") || s.equals("signals")
                       || s.equals("variable") || s.equals("walk"))
            return null;

        // number + optional unit
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^(\\d+(?:\\.\\d+)?)\\s*(mph|km/h|kmh|kph)?$")
            .matcher(s);

        if(!m.matches())
            return null;

        double value;
        try
        {
            value = Double.parseDouble(m.group(1));
        }
        catch(NumberFormatException exc)
        {
            return null;
        }

        if(value <= 0)
            return null;

        String unit = m.group(2);
        if("mph".equals(unit))
            value *= PslMuteDecider.MPH_TO_KPH;

        return Integer.valueOf((int) Math.round(value));
    }

    // -- way selection -----------------------------------------------------

    /**
     * Pick the limit of the way best aligned with the travel bearing.
     * Score combines the (folded) bearing difference at the closest
     * segment with the distance to the way; lowest score wins. Ways with
     * an unusable maxspeed are skipped.
     */
    public static Integer selectLimitKph(List<Way> ways, double lat, double lon, float bearing)
    {
        Way selected = selectWay(ways, lat, lon, bearing);
        return selected == null ? null : selected.limitKph;
    }

    /** Return the selected way as well as its limit so its geometry can be cached. */
    public static Way selectWay(List<Way> ways, double lat, double lon, float bearing)
    {
        Way     best      = null;
        double  bestScore = Double.MAX_VALUE;

        for(Way w : ways)
        {
            if(w.limitKph == null)
                continue;

            double minDist     = Double.MAX_VALUE;
            double segBearing  = 0;

            for(int i = 0; i + 1 < w.lats.length; i++)
            {
                double d = distanceToSegmentM(lat, lon,
                                              w.lats[i], w.lons[i],
                                              w.lats[i + 1], w.lons[i + 1]);
                if(d < minDist)
                {
                    minDist    = d;
                    segBearing = segmentBearing(w.lats[i], w.lons[i],
                                                w.lats[i + 1], w.lons[i + 1]);
                }
            }

            if(minDist == Double.MAX_VALUE)
                continue;

            // 0-90 degrees misalignment + 0.5 point per meter away
            double score = foldedBearingDiff(bearing, segBearing) + minDist * 0.5;

            if(score < bestScore)
            {
                bestScore = score;
                best      = w;
            }
        }

        return best;
    }

    /**
     * Revalidate a cached known limit against the geometry of the way that
     * produced it. This prevents one tile's value from crossing an
     * intersection or being reused on a nearby parallel road.
     */
    public static boolean cachedRoadMatches(SpeedLimitCache.Entry entry,
                                            double lat, double lon, float bearing)
    {
        if(entry == null || !entry.hasRoadGeometry())
            return false;

        double minDist = Double.MAX_VALUE;
        double nearestBearing = 0;

        for(int i = 0; i + 1 < entry.roadLats.length; i++)
        {
            double d = distanceToSegmentM(lat, lon,
                                          entry.roadLats[i], entry.roadLons[i],
                                          entry.roadLats[i + 1], entry.roadLons[i + 1]);
            if(d < minDist)
            {
                minDist = d;
                nearestBearing = segmentBearing(entry.roadLats[i], entry.roadLons[i],
                                                entry.roadLats[i + 1], entry.roadLons[i + 1]);
            }
        }

        return minDist <= MAX_CACHED_ROAD_DISTANCE_M
            && foldedBearingDiff(bearing, nearestBearing) <= MAX_CACHED_BEARING_DIFF_DEG;
    }

    /**
     * Absolute bearing difference folded to [0, 90]: a road is equally
     * aligned when traveled in either direction.
     */
    public static double foldedBearingDiff(double a, double b)
    {
        double d = Math.abs(a - b) % 360.0;
        if(d > 180.0)
            d = 360.0 - d;
        if(d > 90.0)
            d = 180.0 - d;
        return d;
    }

    /** initial bearing of the segment (degrees 0-360, 0 = north) */
    public static double segmentBearing(double lat1, double lon1, double lat2, double lon2)
    {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);

        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);

        double deg = Math.toDegrees(Math.atan2(y, x));
        return (deg + 360.0) % 360.0;
    }

    /** great-circle distance between two points, meters (haversine) */
    public static double distanceM(double lat1, double lon1, double lat2, double lon2)
    {
        double r  = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);

        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Distance from a point to a segment, meters. Uses a local
     * equirectangular projection - plenty accurate at the ~100m scale
     * this provider works at.
     */
    public static double distanceToSegmentM(double lat, double lon,
                                            double lat1, double lon1,
                                            double lat2, double lon2)
    {
        double mPerDegLat = 111320.0;
        double mPerDegLon = 111320.0 * Math.cos(Math.toRadians(lat));

        double px = (lon  - lon1) * mPerDegLon;
        double py = (lat  - lat1) * mPerDegLat;
        double vx = (lon2 - lon1) * mPerDegLon;
        double vy = (lat2 - lat1) * mPerDegLat;

        double len2 = vx * vx + vy * vy;
        double t    = (len2 <= 0.0 ? 0.0 : (px * vx + py * vy) / len2);

        if(t < 0.0) t = 0.0;
        if(t > 1.0) t = 1.0;

        double dx = px - t * vx;
        double dy = py - t * vy;

        return Math.sqrt(dx * dx + dy * dy);
    }
}
