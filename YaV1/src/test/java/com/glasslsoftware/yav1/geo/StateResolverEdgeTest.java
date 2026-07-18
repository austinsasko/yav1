package com.glasslsoftware.yav1.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * [QA] Component tests for StateResolver / GeoState hardening beyond
 * StateResolverTest: invalid datasets, empty state lists, null-safe
 * geometry accessors and caching of null (no-state) resolutions.
 * Uses synthetic square states, no asset file required.
 */
public class StateResolverEdgeTest
{
    /** a 10x10 degree square "state" with corners (lat 0..10, lon 0..10) */
    private static GeoState square(String code, String name)
    {
        GeoState s = new GeoState();
        s.c = code;
        s.n = name;
        s.b = new double[] {0, 0, 10, 10};              // minLon,minLat,maxLon,maxLat
        s.p = new double[][][] {{{0, 0}, {10, 0}, {10, 10}, {0, 10}}};   // [lon,lat]
        return s;
    }

    @Test
    public void datasetWithoutStatesThrowsIoException() throws IOException
    {
        InputStream in = new ByteArrayInputStream("{\"source\":\"x\"}".getBytes("UTF-8"));
        try
        {
            StateResolver.fromStream(in);
            fail("expected IOException for a dataset without states");
        }
        catch(IOException expected)
        {
            // ok
        }
    }

    @Test
    public void nullOrEmptyStateListResolvesToNothing()
    {
        StateResolver r = new StateResolver(null);
        assertEquals(0, r.getStateCount());
        assertNull(r.resolve(5, 5));

        r = new StateResolver(new ArrayList<GeoState>());
        assertNull(r.resolveUncached(5, 5));
        assertEquals(1, r.getResolveCount());
    }

    @Test
    public void nameOfUnknownCodeFallsBackToTheCode()
    {
        StateResolver r = new StateResolver(Arrays.asList(square("SQ", "Square")));

        assertEquals("Square", r.nameOf("SQ"));
        assertEquals("ZZ", r.nameOf("ZZ"));
        assertNull(r.nameOf(null));
    }

    @Test
    public void nullNoStateResolutionIsCachedToo()
    {
        StateResolver r = new StateResolver(Arrays.asList(square("SQ", "Square")));

        assertNull(r.resolve(20.0, 20.0));       // offshore of the square
        int count = r.getResolveCount();

        // ~1km away: cached, no recount
        assertNull(r.resolve(20.009, 20.0));
        assertEquals(count, r.getResolveCount());

        // far away: re-resolves
        assertNull(r.resolve(21.0, 21.0));
        assertEquals(count + 1, r.getResolveCount());
    }

    @Test
    public void resolvePicksTheFirstMatchingState()
    {
        List<GeoState> overlapping = Arrays.asList(square("AA", "First"), square("BB", "Second"));
        StateResolver r = new StateResolver(overlapping);

        assertEquals("AA", r.resolveUncached(5, 5));
    }

    // -- GeoState geometry hardening --------------------------------------

    @Test
    public void bboxContainsRejectsMissingOrMalformedBoxes()
    {
        GeoState s = new GeoState();
        assertFalse(s.bboxContains(5, 5));               // b null

        s.b = new double[] {0, 0, 10};                    // wrong length
        assertFalse(s.bboxContains(5, 5));

        s.b = new double[] {0, 0, 10, 10};
        assertTrue(s.bboxContains(5, 5));
        assertFalse(s.bboxContains(11, 5));
    }

    @Test
    public void containsRejectsMissingRings()
    {
        GeoState s = square("SQ", "Square");
        s.p = null;
        assertFalse(s.contains(5, 5));
    }

    @Test
    public void degenerateRingsAreNeverInside()
    {
        assertFalse(GeoState.pointInRing(5, 5, null));
        assertFalse(GeoState.pointInRing(5, 5, new double[][] {{0, 0}, {10, 10}}));
    }

    @Test
    public void multiRingStateMatchesAnyRing()
    {
        // a "state" with two islands: the square plus a far-away square
        GeoState s = square("IS", "Islands");
        s.b = new double[] {0, 0, 40, 40};
        s.p = new double[][][] {
            {{0, 0}, {10, 0}, {10, 10}, {0, 10}},
            {{30, 30}, {40, 30}, {40, 40}, {30, 40}},
        };

        assertTrue(s.contains(5, 5));
        assertTrue(s.contains(35, 35));
        assertFalse(s.contains(20, 20));                 // the water in between
    }
}
