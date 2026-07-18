package com.glasslsoftware.yav1.aircraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * [QA] Component tests for AircraftTracker heuristic gate boundaries beyond
 * AircraftTrackerTest: exact altitude/speed limits, missing track data,
 * the exact loiter-turn threshold, category precedence and unknown-aircraft
 * alert gating.
 */
public class AircraftTrackerBoundaryTest
{
    private AircraftTracker mTracker;

    @Before
    public void setUp()
    {
        mTracker = new AircraftTracker();
    }

    private static Aircraft plane(String hex, int altFt, double gsKt, double track)
    {
        Aircraft ac = new Aircraft();
        ac.hex      = hex;
        ac.altFt    = altFt;
        ac.gsKt     = gsKt;
        ac.trackDeg = track;
        ac.lat      = 28.55;
        ac.lon      = -81.36;
        return ac;
    }

    // -- lowSlow gate boundaries -----------------------------------------

    @Test
    public void altitudeGateIsInclusiveAtTheCeiling()
    {
        assertTrue(mTracker.assess(plane("a1", AircraftTracker.MAX_ALT_FT, 100, 0), false, 0).lowSlow);
        assertFalse(mTracker.assess(plane("a2", AircraftTracker.MAX_ALT_FT + 1, 100, 0), false, 0).lowSlow);
    }

    @Test
    public void zeroOrUnknownAltitudeIsNotLowSlow()
    {
        assertFalse(mTracker.assess(plane("a3", 0, 100, 0), false, 0).lowSlow);
        assertFalse(mTracker.assess(plane("a4", Integer.MIN_VALUE, 100, 0), false, 0).lowSlow);
    }

    @Test
    public void speedGateIsInclusiveAtBothEnds()
    {
        assertTrue(mTracker.assess(plane("a5", 1200, AircraftTracker.MIN_GS_KT, 0), false, 0).lowSlow);
        assertTrue(mTracker.assess(plane("a6", 1200, AircraftTracker.MAX_GS_KT, 0), false, 0).lowSlow);
        assertFalse(mTracker.assess(plane("a7", 1200, AircraftTracker.MIN_GS_KT - 0.5, 0), false, 0).lowSlow);
        assertFalse(mTracker.assess(plane("a8", 1200, AircraftTracker.MAX_GS_KT + 0.5, 0), false, 0).lowSlow);
    }

    @Test
    public void groundedAircraftIsNeverLowSlow()
    {
        Aircraft taxi = plane("a9", 100, 40, 0);
        taxi.onGround = true;
        assertFalse(mTracker.assess(taxi, false, 0).lowSlow);
    }

    // -- loiter ------------------------------------------------------------

    @Test
    public void missingTrackNeverAccumulatesLoiter()
    {
        AircraftTracker.Assessment as = null;
        long t = 0;
        for(int i = 0; i < 8; i++)
        {
            as = mTracker.assess(plane("b1", 1200, 88, Double.NaN), false, t);
            t += 30_000;
        }

        assertTrue(as.lowSlow);
        assertFalse("NaN tracks must not count as samples", as.loiter);
        assertEquals(AircraftTracker.CAT_NONE, as.category);
    }

    @Test
    public void exactTurnThresholdTriggersLoiter()
    {
        // 4 samples, 90 deg apart: summed turn is exactly 270
        double tracks[] = {0, 90, 180, 270};
        AircraftTracker.Assessment as = null;
        long t = 0;
        for(double trk : tracks)
        {
            as = mTracker.assess(plane("b2", 1200, 88, trk), false, t);
            t += 30_000;
        }

        assertTrue(as.loiter);
        assertEquals(AircraftTracker.CAT_HEURISTIC, as.category);
    }

    @Test
    public void justUnderTheTurnThresholdStaysQuiet()
    {
        double tracks[] = {0, 89, 178, 267};   // sum 267 < 270
        AircraftTracker.Assessment as = null;
        long t = 0;
        for(double trk : tracks)
        {
            as = mTracker.assess(plane("b3", 1200, 88, trk), false, t);
            t += 30_000;
        }

        assertFalse(as.loiter);
        assertEquals(AircraftTracker.CAT_NONE, as.category);
    }

    // -- category precedence ------------------------------------------------

    @Test
    public void watchlistBeatsHeuristicWhenBothApply()
    {
        double tracks[] = {0, 90, 180, 270, 0};
        AircraftTracker.Assessment as = null;
        long t = 0;
        for(double trk : tracks)
        {
            as = mTracker.assess(plane("c1", 1200, 88, trk), true, t);
            t += 30_000;
        }

        assertTrue(as.lowSlow);
        assertTrue(as.loiter);
        assertEquals(AircraftTracker.CAT_WATCHLIST, as.category);
    }

    // -- alert gating --------------------------------------------------------

    @Test
    public void neverAssessedAircraftNeverAlerts()
    {
        assertFalse(mTracker.shouldAlert(plane("d1", 1200, 88, 0), 0));
    }

    @Test
    public void pruneKeepsRecentlySeenAircraft()
    {
        mTracker.assess(plane("e1", 1200, 88, 0), false, 0);
        mTracker.assess(plane("e2", 1200, 88, 0), false, AircraftTracker.STALE_MS);

        mTracker.prune(AircraftTracker.STALE_MS + 1);

        assertEquals("only the stale aircraft may be dropped", 1, mTracker.trackedCount());
    }

    @Test
    public void hexKeyIsCaseInsensitive()
    {
        mTracker.assess(plane("AB12CD", 1200, 88, 0), true, 0);

        // same airframe reported lower-case later: same history entry
        assertEquals(1, mTracker.trackedCount());
        mTracker.assess(plane("ab12cd", 1200, 88, 90), true, 30_000);
        assertEquals(1, mTracker.trackedCount());

        // and the cooldown is shared across the casings
        assertTrue(mTracker.shouldAlert(plane("AB12CD", 1200, 88, 0), 30_000));
        assertFalse(mTracker.shouldAlert(plane("ab12cd", 1200, 88, 0), 31_000));
    }
}
