package com.glasslsoftware.yav1.poi;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * [P2-POI] Fetch bookkeeping for the online camera sources, following the
 * PSL SpeedLimitCache pattern: the world is tiled on a coarse grid
 * (~4.4 km), each tile remembers when it was last fetched, and fetched
 * POIs are deduped by rounded position + type so refetches don't
 * duplicate. Persisted to a small JSON file so coverage survives
 * restarts (offline-first: the merged POIs themselves live in the
 * regular PoiStore and are indexed without any network).
 *
 * Thread safe, no Android dependencies, unit tested.
 */
public class PoiOnlineCache
{
    /** ~4.4km of latitude per tile */
    public static final double TILE_DEG = 0.04;

    /** a fetched tile stays fresh this long (cameras move rarely) */
    public static final long TILE_TTL_MS = 7L * 24 * 3600 * 1000;

    /** hard cap on accumulated POIs (bounds file size / memory) */
    public static final int MAX_POIS = 8000;

    private static final int PERSIST_VERSION = 1;

    private final LinkedHashMap<String, Long> mTiles = new LinkedHashMap<String, Long>();
    private final LinkedHashMap<String, Poi>  mPois  = new LinkedHashMap<String, Poi>();

    public static String tileKey(double lat, double lon)
    {
        long latIdx      = (long) Math.floor(lat / TILE_DEG);
        double centerLat = (latIdx + 0.5) * TILE_DEG;
        double lonStep   = TILE_DEG / Math.max(0.01, Math.cos(Math.toRadians(centerLat)));
        long lonIdx      = (long) Math.floor(lon / lonStep);

        return latIdx + "_" + lonIdx;
    }

    /** dedupe key: ~1m position resolution + type */
    public static String poiKey(Poi p)
    {
        return String.format(Locale.US, "%d_%d_%s",
            Math.round(p.lat * 1e5), Math.round(p.lon * 1e5), p.type);
    }

    public synchronized boolean isTileFresh(String key, long nowMs)
    {
        Long t = mTiles.get(key);
        return t != null && nowMs - t <= TILE_TTL_MS;
    }

    public synchronized void markTileFetched(String key, long nowMs)
    {
        mTiles.put(key, Long.valueOf(nowMs));
    }

    /**
     * Merge fetched POIs in (dedupe by position + type; existing entries
     * are refreshed in place). Returns how many were new. Once MAX_POIS
     * is reached the oldest entries are evicted first.
     */
    public synchronized int merge(List<Poi> pois)
    {
        int added = 0;

        for(Poi p : pois)
        {
            String k = poiKey(p);
            if(!mPois.containsKey(k))
                added++;
            mPois.put(k, p);
        }

        Iterator<Map.Entry<String, Poi>> it = mPois.entrySet().iterator();
        while(mPois.size() > MAX_POIS && it.hasNext())
        {
            it.next();
            it.remove();
        }

        return added;
    }

    /** all accumulated POIs (for the PoiStore generated file) */
    public synchronized List<Poi> snapshot()
    {
        return new ArrayList<Poi>(mPois.values());
    }

    public synchronized int poiCount()
    {
        return mPois.size();
    }

    public synchronized int tileCount()
    {
        return mTiles.size();
    }

    // -- persistence (tile bookkeeping only; POIs live in the PoiStore) ----

    public synchronized String toJson()
    {
        try
        {
            JSONObject tiles = new JSONObject();
            for(Map.Entry<String, Long> e : mTiles.entrySet())
                tiles.put(e.getKey(), e.getValue().longValue());

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

    /** merge persisted tile stamps (expired ones are dropped); fail soft */
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
                String key = it.next();
                long   ts  = tiles.optLong(key, 0);
                if(ts > 0 && nowMs - ts <= TILE_TTL_MS && !mTiles.containsKey(key))
                    mTiles.put(key, Long.valueOf(ts));
            }
        }
        catch(Exception exc)
        {
            // corrupted meta: start empty
        }
    }

    /** seed accumulated POIs from a previously generated store file */
    public synchronized void seedFromStore(List<Poi> pois)
    {
        for(Poi p : pois)
        {
            if(mPois.size() >= MAX_POIS)
                break;
            mPois.put(poiKey(p), p);
        }
    }

    public void load(File file, long nowMs)
    {
        if(file == null || !file.isFile())
            return;

        try
        {
            InputStream in = new FileInputStream(file);
            try
            {
                byte buf[] = new byte[(int) Math.min(file.length(), 4 * 1024 * 1024)];
                int  off   = 0;
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
