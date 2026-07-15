package com.franckyl.yav1.psl;

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
 * limit (or "known unknown") with its fetch time. An in-memory LRU keeps
 * the working set small; tiles with a known limit are also persisted to a
 * small JSON file so limits survive app restarts.
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

    private static final int PERSIST_VERSION = 1;

    /** one cached tile */
    public static class Entry
    {
        public final Integer limitKph;  // null = fetched, no limit found
        public final long    fetchedAt; // wall clock ms

        public Entry(Integer limitKph, long fetchedAt)
        {
            this.limitKph  = limitKph;
            this.fetchedAt = fetchedAt;
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

                Entry e = new Entry(t.optInt("l"), t.optLong("t"));
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
