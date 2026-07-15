package com.franckyl.yav1.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * [P3-GEO] Tests for the offline US state resolver: point-in-polygon and
 * bbox correctness against the real shipped dataset (well known city
 * coordinates, including a cross-river border pair), plus the 2 km
 * re-resolve hysteresis.
 */
public class StateResolverTest
{
    private static StateResolver sResolver;

    @BeforeClass
    public static void loadDataset() throws IOException
    {
        // unit tests run with the module directory as working dir; keep a
        // fallback for runners started from the repository root
        String[] candidates = {
            "src/main/assets/geo/us_states.json",
            "YaV1/src/main/assets/geo/us_states.json",
        };

        for(String c: candidates)
        {
            File f = new File(c);

            if(f.isFile())
            {
                FileInputStream in = new FileInputStream(f);
                try
                {
                    sResolver = StateResolver.fromStream(in);
                }
                finally
                {
                    in.close();
                }
                return;
            }
        }

        throw new IOException("us_states.json asset not found from " + new File(".").getAbsolutePath());
    }

    private static StateResolver fresh()
    {
        return new StateResolver(sResolver.getStates());
    }

    // ------------------------------------------------------ dataset sanity

    @Test
    public void datasetHasAllStates()
    {
        // 50 states + DC + Puerto Rico
        assertEquals(52, sResolver.getStateCount());
    }

    @Test
    public void namesResolve()
    {
        assertEquals("Ohio", sResolver.nameOf("OH"));
        assertEquals("Kentucky", sResolver.nameOf("KY"));
        assertNull(sResolver.nameOf(null));
    }

    // ------------------------------------------- resolution of real cities

    @Test
    public void columbusIsOhio()
    {
        assertEquals("OH", fresh().resolve(39.9612, -82.9988));
    }

    @Test
    public void newYorkCityIsNewYork()
    {
        assertEquals("NY", fresh().resolve(40.7128, -74.0060));
    }

    @Test
    public void losAngelesIsCalifornia()
    {
        assertEquals("CA", fresh().resolve(34.0522, -118.2437));
    }

    @Test
    public void lakeEriePointMatchesNoState()
    {
        assertNull(fresh().resolve(42.2000, -81.5000));
    }

    @Test
    public void atlanticOffshoreMatchesNoState()
    {
        assertNull(fresh().resolve(35.0000, -70.0000));
    }

    // border town pair separated by the Ohio River (about 2 km apart)

    @Test
    public void cincinnatiIsOhioButCovingtonIsKentucky()
    {
        assertEquals("OH", fresh().resolveUncached(39.1031, -84.5120));
        assertEquals("KY", fresh().resolveUncached(39.0837, -84.5086));
    }

    // straight-line border pair (Kansas City)

    @Test
    public void kansasCityPairResolvesToBothStates()
    {
        assertEquals("MO", fresh().resolveUncached(39.0997, -94.5786));
        assertEquals("KS", fresh().resolveUncached(39.1141, -94.6275));
    }

    @Test
    public void nonContiguousStatesResolve()
    {
        assertEquals("AK", fresh().resolveUncached(61.2181, -149.9003)); // Anchorage
        assertEquals("HI", fresh().resolveUncached(21.3069, -157.8583)); // Honolulu
        assertEquals("MI", fresh().resolveUncached(46.5436, -87.3954));  // Marquette (Upper Peninsula)
    }

    // --------------------------------------------------- hysteresis / cache

    @Test
    public void smallMovementUsesCachedState()
    {
        StateResolver r = fresh();

        assertEquals("OH", r.resolve(39.9612, -82.9988));

        int count = r.getResolveCount();

        // ~500 m north, still Columbus
        assertEquals("OH", r.resolve(39.9657, -82.9988));
        // ~1.9 km east of the original fix
        assertEquals("OH", r.resolve(39.9612, -82.9765));

        assertEquals("cached results must not re-resolve", count, r.getResolveCount());
    }

    @Test
    public void movementBeyondThresholdReResolves()
    {
        StateResolver r = fresh();

        assertEquals("OH", r.resolve(39.9612, -82.9988));

        int count = r.getResolveCount();

        // ~5 km away: must re-resolve (same state, new anchor point)
        assertEquals("OH", r.resolve(39.9612, -82.9402));
        assertEquals(count + 1, r.getResolveCount());
    }

    @Test
    public void borderCrossingDetectedAfterThreshold()
    {
        StateResolver r = fresh();

        // Cincinnati riverfront
        assertEquals("OH", r.resolve(39.1031, -84.5120));

        // Covington is ~2.2 km away, beyond the 2 km hysteresis
        assertEquals("KY", r.resolve(39.0837, -84.5086));
    }

    @Test
    public void customThresholdIsHonored()
    {
        StateResolver r = fresh();

        r.setReResolveDistanceMeters(100.0);

        assertEquals("OH", r.resolve(39.9612, -82.9988));

        int count = r.getResolveCount();

        // ~500 m: beyond the 100 m custom threshold
        assertEquals("OH", r.resolve(39.9657, -82.9988));
        assertEquals(count + 1, r.getResolveCount());
    }

    @Test
    public void resetCacheForcesReResolve()
    {
        StateResolver r = fresh();

        assertEquals("OH", r.resolve(39.9612, -82.9988));

        int count = r.getResolveCount();

        r.resetCache();

        assertEquals("OH", r.resolve(39.9612, -82.9988));
        assertEquals(count + 1, r.getResolveCount());
    }

    // ------------------------------------------------------------ geometry

    @Test
    public void pointInRingSquare()
    {
        // unit square, ring as [lon, lat] pairs
        double[][] square = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

        assertTrue(GeoState.pointInRing(0.5, 0.5, square));
        assertFalse(GeoState.pointInRing(1.5, 0.5, square));
        assertFalse(GeoState.pointInRing(-0.1, 0.5, square));
        assertFalse(GeoState.pointInRing(0.5, 1.5, square));
    }

    @Test
    public void pointInRingConcave()
    {
        // U shaped polygon: the notch (0.5, 0.8) is outside
        double[][] u = {{0, 0}, {1, 0}, {1, 1}, {0.7, 1}, {0.7, 0.3}, {0.3, 0.3}, {0.3, 1}, {0, 1}};

        assertTrue(GeoState.pointInRing(0.1, 0.15, u));
        assertFalse(GeoState.pointInRing(0.8, 0.5, u));  // in the notch
        assertTrue(GeoState.pointInRing(0.1, 0.85, u));  // left arm
    }

    @Test
    public void haversineSanity()
    {
        // one degree of latitude is about 111.2 km
        double d = StateResolver.haversineMeters(39.0, -84.0, 40.0, -84.0);
        assertTrue("expected ~111 km, got " + d, Math.abs(d - 111200) < 1000);

        // zero distance
        assertEquals(0.0, StateResolver.haversineMeters(39.0, -84.0, 39.0, -84.0), 0.001);
    }
}
