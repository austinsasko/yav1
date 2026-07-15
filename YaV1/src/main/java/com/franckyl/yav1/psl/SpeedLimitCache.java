package com.franckyl.yav1.psl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [P1-PSL] Tile cache for posted speed limits.
 *
 * The world is tiled on a ~150m grid; each tile stores the last fetched
 * limit (or "known unknown"), its fetch time, and the geometry of the road
 * that supplied a known limit. The provider revalidates that geometry before
 * reusing a value at another point in the tile.
 *
 * Thread safe. No Android dependencies (unit-testable on the JVM).
 */
public class SpeedLimitCache
{
    /** ~150m of latitude, in degrees */
    public static final double LAT_STEP_DEG = 0.00135;

    /** maximum number of tiles kept in memory */
    public static final int MAX_TILES = 512;

    /** how long a known limit stays valid */
    public static final long KNOWN_TTL_MS = 30L * 24 * 3600 * 1000;

    /** how long a "no limit found" answer suppresses refetching */
    public static final long UNKNOWN_TTL_MS = 15L * 60 * 1000;

    private static final int PERSIST_VERSION = 2;

    /** one cached tile */
    public static class Entry
    {
        public final Integer limitKph;  // null = fetched, no limit found
        public final long    fetchedAt; // wall clock ms
        public final double[] roadLats; // selected road geometry, known values only
        public final double[] roadLons;

        public Entry(Integer limitKph, long fetchedAt)
        {
            this(limitKph, fetchedAt, null, null);
        }

        public Entry(Integer limitKph, long fetchedAt, double[] roadLats, double[] roadLons)
        {
            this.limitKph  = limitKph;
            this.fetchedAt = fetchedAt;
            this.roadLats  = (roadLats == null ? null : roadLats.clone());
            this.roadLons  = (roadLons == null ? null : roadLons.clone());
        }

        public boolean hasRoadGeometry()
        {
            return roadLats != null && roadLons != null
                && roadLats.length == roadLons.length && roadLats.length >= 2;
        }
    }

    private final LinkedHashMap<String, Entry> mTiles =
        new LinkedHashMap<String, Entry>(64, 0.75f, true)
        {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SpeedLimitCache.Entry> eldest)
            {
                return size() > MAX_TILES;
            }
        };

    /**
     * Compute the tile key for a location. The longitude step is scaled
     * from the tile-row center latitude so tiles stay ~150m wide at any
     * latitude while remaining deterministic within a row.
     */
    public static String tileKey(double lat, double lon)
    {
        long latIdx      = (long) Math.floor(lat / LAT_STEP_DEG);
        double centerLat = (latIdx + 0.5) * LAT_STEP_DEG;
        double lonStep   = LAT_STEP_DEG / Math.max(0.01, Math.cos(Math.toRadians(centerLat)));
        long lonIdx      = (long) Math.floor(lon / lonStep);

        return latIdx + "_" + lonIdx;
    }

    public synchronized Entry get(String key)
    {
        return mTiles.get(key);
    }

    public synchronized void put(String key, Integer limitKph, long nowMs)
    {
        mTiles.put(key, new Entry(limitKph, nowMs));
    }

    public synchronized void put(String key, Integer limitKph, long nowMs,
                                 double[] roadLats, double[] roadLons)
    {
        mTiles.put(key, new Entry(limitKph, nowMs, roadLats, roadLons));
    }

    public synchronized int size()
    {
        return mTiles.size();
    }

    /** true when the entry can still be used without a refetch */
    public static boolean isFresh(Entry e, long nowMs)
    {
        if(e == null)
            return false;

        long ttl = (e.limitKph != null ? KNOWN_TTL_MS : UNKNOWN_TTL_MS);
        return nowMs - e.fetchedAt <= ttl;
    }

    // -- persistence (known limits only) --------------------------------

    public synchronized String toJson()
    {
        try
        {
            JSONObject tiles = new JSONObject();

            for(Map.Entry<String, Entry> me : mTiles.entrySet())
            {
                Entry e = me.getValue();
                if(e.limitKph == null)
                    continue;

                JSONObject t = new JSONObject();
                t.put("l", e.limitKph.intValue());
                t.put("t", e.fetchedAt);

                if(e.hasRoadGeometry())
                {
                    JSONArray geometry = new JSONArray();
                    for(int i = 0; i < e.roadLats.length; i++)
                    {
                        JSONArray point = new JSONArray();
                        point.put(e.roadLats[i]);
                        point.put(e.roadLons[i]);
                        geometry.put(point);
                    }
                    t.put("g", geometry);
                }

                tiles.put(me.getKey(), t);
            }

            JSONObject root = new JSONObject();
            root.put("v", PERSIST_VERSION);
            root.put("tiles", tiles);

            return root.toString();
        }
        catch(Exception exc)
        {
            return null;
        }
    }

    /** merge persisted tiles in (expired ones are dropped); fail soft */
    public synchronized void fromJson(String json, long nowMs)
    {
        if(json == null || json.isEmpty())
            return;

        try
        {
            JSONObject root = new JSONObject(json);
            if(root.optInt("v", -1) != PERSIST_VERSION)
                return;

            JSONObject tiles = root.optJSONObject("tiles");
            if(tiles == null)
                return;

            Iterator<String> it = tiles.keys();
            while(it.hasNext())
            {
                String key   = it.next();
                JSONObject t = tiles.optJSONObject(key);
                if(t == null || !t.has("l"))
                    continue;

                double lats[] = null;
                double lons[] = null;
                JSONArray geometry = t.optJSONArray("g");

                if(geometry != null && geometry.length() >= 2)
                {
                    lats = new double[geometry.length()];
                    lons = new double[geometry.length()];
                    boolean valid = true;

                    for(int i = 0; i < geometry.length(); i++)
                    {
                        JSONArray point = geometry.optJSONArray(i);
                        if(point == null || point.length() < 2)
                        {
                            valid = false;
                            break;
                        }
                        lats[i] = point.optDouble(0, Double.NaN);
                        lons[i] = point.optDouble(1, Double.NaN);
                        if(Double.isNaN(lats[i]) || Double.isNaN(lons[i]))
                        {
                            valid = false;
                            break;
                        }
                    }

                    if(!valid)
                    {
                        lats = null;
                        lons = null;
                    }
                }

                Entry e = new Entry(t.optInt("l"), t.optLong("t"), lats, lons);
                if(isFresh(e, nowMs) && !mTiles.containsKey(key))
                    mTiles.put(key, e);
            }
        }
        catch(Exception exc)
        {
            // corrupted cache file: start empty
        }
    }

    /** load from a file, fail soft (missing / unreadable / corrupt) */
    public void load(File file, long nowMs)
    {
        if(file == null || !file.isFile())
            return;

        try
        {
            InputStream in = new FileInputStream(file);
            try
            {
                byte buf[]  = new byte[(int) Math.min(file.length(), 4 * 1024 * 1024)];
                int  off    = 0;
                int  n;
                while(off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0)
                    off += n;

                fromJson(new String(buf, 0, off, Charset.forName("UTF-8")), nowMs);
            }
            finally
            {
                in.close();
            }
        }
        catch(Exception exc)
        {
            // fail soft
        }
    }

    /** save to a file (atomic-ish: temp + rename), fail soft */
    public void save(File file)
    {
        if(file == null)
            return;

        String json = toJson();
        if(json == null)
            return;

        try
        {
            File tmp = new File(file.getPath() + ".tmp");
            OutputStream out = new FileOutputStream(tmp);
            try
            {
                out.write(json.getBytes(Charset.forName("UTF-8")));
            }
            finally
            {
                out.close();
            }

            if(!tmp.renameTo(file))
            {
                file.delete();
                tmp.renameTo(file);
            }
        }
        catch(Exception exc)
        {
            // fail soft
        }
    }
}
