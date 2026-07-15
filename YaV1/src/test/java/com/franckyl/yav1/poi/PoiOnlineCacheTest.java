package com.franckyl.yav1.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * [P2-POI] Tile bookkeeping + dedupe for the online camera sources.
 */
public class PoiOnlineCacheTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Poi poi(double lat, double lon, String type)
    {
        return new Poi(lat, lon, type, 0, "", "osm:" + type);
    }

    @Test
    public void tileKeysAreCoarserThanPslTiles()
    {
        // ~4.4km apart: different tiles
        assertFalse(PoiOnlineCache.tileKey(30.00, -95.44)
                        .equals(PoiOnlineCache.tileKey(30.05, -95.44)));

        // ~1km apart: same tile
        assertEquals(PoiOnlineCache.tileKey(30.010, -95.440),
                     PoiOnlineCache.tileKey(30.014, -95.440));
    }

    @Test
    public void tileFreshnessFollowsTtl()
    {
        PoiOnlineCache c = new PoiOnlineCache();
        long now = 1_000_000L;

        String k = PoiOnlineCache.tileKey(30.0, -95.0);
        assertFalse(c.isTileFresh(k, now));

        c.markTileFetched(k, now);
        assertTrue(c.isTileFresh(k, now + PoiOnlineCache.TILE_TTL_MS - 1));
        assertFalse(c.isTileFresh(k, now + PoiOnlineCache.TILE_TTL_MS + 1));
    }

    @Test
    public void mergeDedupesByPositionAndType()
    {
        PoiOnlineCache c = new PoiOnlineCache();

        List<Poi> batch1 = new ArrayList<Poi>();
        batch1.add(poi(30.0, -95.0, "alpr"));
        batch1.add(poi(30.1, -95.1, "speed_camera"));
        assertEquals(2, c.merge(batch1));

        // refetch of an overlapping area: same cameras again + one new
        List<Poi> batch2 = new ArrayList<Poi>();
        batch2.add(poi(30.0, -95.0, "alpr"));
        batch2.add(poi(30.1, -95.1, "speed_camera"));
        batch2.add(poi(30.2, -95.2, "redlight"));
        assertEquals(1, c.merge(batch2));

        assertEquals(3, c.poiCount());

        // same position but different type is a distinct POI
        // (red-light camera colocated with an ALPR)
        List<Poi> batch3 = new ArrayList<Poi>();
        batch3.add(poi(30.0, -95.0, "redlight"));
        assertEquals(1, c.merge(batch3));
        assertEquals(4, c.poiCount());
    }

    @Test
    public void capEvictsOldestEntries()
    {
        PoiOnlineCache c = new PoiOnlineCache();

        List<Poi> batch = new ArrayList<Poi>();
        for(int i = 0; i < PoiOnlineCache.MAX_POIS + 100; i++)
            batch.add(poi(30.0 + i * 1e-4, -95.0, "alpr"));

        c.merge(batch);
        assertEquals(PoiOnlineCache.MAX_POIS, c.poiCount());

        // the oldest (lowest-latitude) entries were evicted
        List<Poi> snap = c.snapshot();
        double minLat = Double.MAX_VALUE;
        for(Poi p : snap)
            minLat = Math.min(minLat, p.lat);
        assertTrue(minLat > 30.0 + 99 * 1e-4 - 1e-9);
    }

    @Test
    public void persistenceRoundTripsFreshTilesOnly()
    {
        PoiOnlineCache c = new PoiOnlineCache();
        long now = 1_000_000L;

        c.markTileFetched("10_20", now);
        c.markTileFetched("11_21", now - PoiOnlineCache.TILE_TTL_MS - 1);   // expired

        String json = c.toJson();
        assertTrue(json != null && json.contains("10_20"));

        PoiOnlineCache c2 = new PoiOnlineCache();
        c2.fromJson(json, now);

        assertTrue(c2.isTileFresh("10_20", now));
        assertFalse(c2.isTileFresh("11_21", now));
        assertEquals(1, c2.tileCount());
    }

    @Test
    public void fileRoundTripFailsSoft() throws Exception
    {
        PoiOnlineCache c = new PoiOnlineCache();
        long now = 1_000_000L;
        c.markTileFetched("10_20", now);

        File f = new File(tmp.getRoot(), "online_meta.json");
        c.save(f);
        assertTrue(f.isFile());

        PoiOnlineCache c2 = new PoiOnlineCache();
        c2.load(f, now);
        assertTrue(c2.isTileFresh("10_20", now));

        // corrupted / missing files never throw
        java.io.FileOutputStream out = new java.io.FileOutputStream(f);
        out.write("not json".getBytes("UTF-8"));
        out.close();

        PoiOnlineCache c3 = new PoiOnlineCache();
        c3.load(f, now);
        assertEquals(0, c3.tileCount());
        c3.load(new File(tmp.getRoot(), "missing.json"), now);

        c3.fromJson(null, now);
        c3.fromJson("{\"v\":99,\"tiles\":{\"a\":1}}", now);   // wrong version
        assertEquals(0, c3.tileCount());
    }

    @Test
    public void seedFromStoreRestoresDedupeState()
    {
        PoiOnlineCache c = new PoiOnlineCache();

        List<Poi> stored = new ArrayList<Poi>();
        stored.add(poi(30.0, -95.0, "alpr"));
        stored.add(poi(30.1, -95.1, "speed_camera"));
        c.seedFromStore(stored);

        assertEquals(2, c.poiCount());

        // a refetch of the same cameras adds nothing new
        List<Poi> refetch = new ArrayList<Poi>();
        refetch.add(poi(30.0, -95.0, "alpr"));
        assertEquals(0, c.merge(refetch));
    }
}
