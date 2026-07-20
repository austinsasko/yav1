package com.glasslsoftware.yav1.psl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/**
 * [QA] Component tests for SpeedLimitCache edge cases not covered by
 * SpeedLimitCacheTest: malformed persisted geometry, merge semantics of
 * fromJson, defensive copies in Entry, and fail-soft file IO on bad paths.
 */
public class SpeedLimitCacheEdgeTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final long NOW = 1700000000000L;

    // -- malformed persisted geometry -----------------------------------

    @Test
    public void geometryPointWithTooFewCoordinatesDropsGeometryKeepsLimit()
    {
        String json = "{\"v\":2,\"tiles\":{\"k\":{\"l\":50,\"t\":" + NOW
                    + ",\"g\":[[28.0],[28.1,-81.0]]}}}";

        SpeedLimitCache c = new SpeedLimitCache();
        c.fromJson(json, NOW);

        SpeedLimitCache.Entry e = c.get("k");
        assertNotNull(e);
        assertEquals(Integer.valueOf(50), e.limitKph);
        assertFalse("truncated point must invalidate the geometry", e.hasRoadGeometry());
    }

    @Test
    public void geometryWithNonNumericCoordinateDropsGeometryKeepsLimit()
    {
        String json = "{\"v\":2,\"tiles\":{\"k\":{\"l\":50,\"t\":" + NOW
                    + ",\"g\":[[\"x\",-81.0],[28.1,-81.0]]}}}";

        SpeedLimitCache c = new SpeedLimitCache();
        c.fromJson(json, NOW);

        SpeedLimitCache.Entry e = c.get("k");
        assertNotNull(e);
        assertEquals(Integer.valueOf(50), e.limitKph);
        assertFalse(e.hasRoadGeometry());
    }

    @Test
    public void geometryThatIsNotAnArrayIsIgnored()
    {
        String json = "{\"v\":2,\"tiles\":{\"k\":{\"l\":72,\"t\":" + NOW
                    + ",\"g\":\"oops\"}}}";

        SpeedLimitCache c = new SpeedLimitCache();
        c.fromJson(json, NOW);

        SpeedLimitCache.Entry e = c.get("k");
        assertNotNull(e);
        assertEquals(Integer.valueOf(72), e.limitKph);
        assertFalse(e.hasRoadGeometry());
    }

    @Test
    public void tileWithoutLimitFieldIsSkipped()
    {
        String json = "{\"v\":2,\"tiles\":{\"k\":{\"t\":" + NOW + "}}}";

        SpeedLimitCache c = new SpeedLimitCache();
        c.fromJson(json, NOW);

        assertNull(c.get("k"));
        assertEquals(0, c.size());
    }

    // -- merge semantics -------------------------------------------------

    @Test
    public void fromJsonNeverOverwritesLiveTiles()
    {
        SpeedLimitCache c = new SpeedLimitCache();
        c.put("k", 30, NOW);

        // a persisted copy of the same tile with another limit
        String json = "{\"v\":2,\"tiles\":{\"k\":{\"l\":99,\"t\":" + NOW + "}}}";
        c.fromJson(json, NOW);

        assertEquals("in-memory value must win over the persisted copy",
                     Integer.valueOf(30), c.get("k").limitKph);
    }

    // -- Entry hygiene ---------------------------------------------------

    @Test
    public void entryCopiesGeometryArraysDefensively()
    {
        double lats[] = {28.0, 28.1};
        double lons[] = {-81.0, -81.1};

        SpeedLimitCache c = new SpeedLimitCache();
        c.put("k", 50, NOW, lats, lons);

        lats[0] = 0;
        lons[0] = 0;

        assertEquals(28.0, c.get("k").roadLats[0], 0.0);
        assertEquals(-81.0, c.get("k").roadLons[0], 0.0);
    }

    @Test
    public void hasRoadGeometryRejectsDegenerateShapes()
    {
        // no geometry at all
        assertFalse(new SpeedLimitCache.Entry(50, NOW).hasRoadGeometry());
        // single point is not a road
        assertFalse(new SpeedLimitCache.Entry(50, NOW,
            new double[] {28.0}, new double[] {-81.0}).hasRoadGeometry());
        // mismatched lengths
        assertFalse(new SpeedLimitCache.Entry(50, NOW,
            new double[] {28.0, 28.1}, new double[] {-81.0}).hasRoadGeometry());
        // two points are the minimum
        assertTrue(new SpeedLimitCache.Entry(50, NOW,
            new double[] {28.0, 28.1}, new double[] {-81.0, -81.1}).hasRoadGeometry());
    }

    // -- tile keys at extreme latitudes ---------------------------------

    @Test
    public void highLatitudeTilesWidenInsteadOfExploding()
    {
        // at 80N the longitude step is ~5.8x wider than at the equator, so
        // two points 0.003 deg apart share a tile there but not at 0N
        assertEquals(SpeedLimitCache.tileKey(80.0005, 0.0005),
                     SpeedLimitCache.tileKey(80.0005, 0.003));
        org.junit.Assert.assertNotEquals(SpeedLimitCache.tileKey(0.0005, 0.0005),
                                         SpeedLimitCache.tileKey(0.0005, 0.003));
    }

    // -- fail-soft file IO ----------------------------------------------

    @Test
    public void saveToUncreatablePathFailsSoft()
    {
        SpeedLimitCache c = new SpeedLimitCache();
        c.put("k", 50, NOW);

        // parent directory does not exist and is never created
        File bad = new File(tmp.getRoot(), "no/such/dir/cache.json");
        c.save(bad);                       // must not throw
        assertFalse(bad.exists());

        c.save(null);                      // must not throw either
    }

    @Test
    public void loadFromDirectoryOrNullFailsSoft()
    {
        SpeedLimitCache c = new SpeedLimitCache();
        c.load(tmp.getRoot(), NOW);        // a directory, not a file
        c.load(null, NOW);
        assertEquals(0, c.size());
    }
}
