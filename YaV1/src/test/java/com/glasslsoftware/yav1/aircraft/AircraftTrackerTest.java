package com.glasslsoftware.yav1.aircraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AircraftTrackerTest
{
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

    @Test
    public void watchlistMatchIsFlaggedWhenAirborne()
    {
        AircraftTracker tracker = new AircraftTracker();

        AircraftTracker.Assessment as = tracker.assess(plane("a255e2", 1800, 95, 140), true, 0);
        assertEquals(AircraftTracker.CAT_WATCHLIST, as.category);
    }

    @Test
    public void watchlistMatchOnGroundStaysSilent()
    {
        AircraftTracker tracker = new AircraftTracker();

        Aircraft parked = plane("a255e2", Integer.MIN_VALUE, 0, 0);
        parked.onGround = true;

        AircraftTracker.Assessment as = tracker.assess(parked, true, 0);
        assertEquals(AircraftTracker.CAT_NONE, as.category);
    }

    @Test
    public void loiteringLowSlowAircraftBecomesHeuristicSuspect()
    {
        AircraftTracker tracker = new AircraftTracker();

        // circling: track advances 90 degrees per 30 s poll
        long t = 0;
        AircraftTracker.Assessment as = null;
        double[] tracks = {0, 90, 180, 270, 0};

        for(double trk: tracks)
        {
            as = tracker.assess(plane("abc123", 1200, 88, trk), false, t);
            t += 30_000;
        }

        assertTrue(as.lowSlow);
        assertTrue(as.loiter);
        assertEquals(AircraftTracker.CAT_HEURISTIC, as.category);
    }

    @Test
    public void straightFlightIsNotLoitering()
    {
        AircraftTracker tracker = new AircraftTracker();

        AircraftTracker.Assessment as = null;
        long t = 0;
        for(int i = 0; i < 6; i++)
        {
            as = tracker.assess(plane("abc124", 1200, 88, 45), false, t);
            t += 30_000;
        }

        assertTrue(as.lowSlow);
        assertFalse(as.loiter);
        assertEquals(AircraftTracker.CAT_NONE, as.category);
    }

    @Test
    public void airlinerNeverSuspectEvenWhenTurning()
    {
        AircraftTracker tracker = new AircraftTracker();

        AircraftTracker.Assessment as = null;
        long t = 0;
        double[] tracks = {0, 90, 180, 270, 0};
        for(double trk: tracks)
        {
            as = tracker.assess(plane("a00001", 35000, 450, trk), false, t);
            t += 30_000;
        }

        assertFalse(as.lowSlow);
        assertEquals(AircraftTracker.CAT_NONE, as.category);
    }

    @Test
    public void oldSamplesFallOutOfLoiterWindow()
    {
        AircraftTracker tracker = new AircraftTracker();

        // two turns now, then a long gap, then two more: window keeps only recent ones
        tracker.assess(plane("abc125", 1200, 88, 0), false, 0);
        tracker.assess(plane("abc125", 1200, 88, 90), false, 30_000);

        long later = AircraftTracker.LOITER_WINDOW_MS + 120_000;
        tracker.assess(plane("abc125", 1200, 88, 180), false, later);
        AircraftTracker.Assessment as =
                tracker.assess(plane("abc125", 1200, 88, 270), false, later + 30_000);

        // only 2 samples remain in the window: not enough for loiter
        assertFalse(as.loiter);
    }

    @Test
    public void alertCooldownTenMinutesPerAircraft()
    {
        AircraftTracker tracker = new AircraftTracker();

        Aircraft ac = plane("a255e2", 1800, 95, 140);
        tracker.assess(ac, true, 0);

        assertTrue(tracker.shouldAlert(ac, 0));
        assertFalse(tracker.shouldAlert(ac, 60_000));
        assertFalse(tracker.shouldAlert(ac, AircraftTracker.ALERT_COOLDOWN_MS - 1));
        assertTrue(tracker.shouldAlert(ac, AircraftTracker.ALERT_COOLDOWN_MS + 1));

        // a different aircraft has its own cooldown
        Aircraft other = plane("a11791", 2000, 100, 90);
        tracker.assess(other, true, AircraftTracker.ALERT_COOLDOWN_MS + 1);
        assertTrue(tracker.shouldAlert(other, AircraftTracker.ALERT_COOLDOWN_MS + 1));
    }

    @Test
    public void pruneDropsStaleAircraft()
    {
        AircraftTracker tracker = new AircraftTracker();

        tracker.assess(plane("abc126", 1200, 88, 0), false, 0);
        assertEquals(1, tracker.trackedCount());

        tracker.prune(AircraftTracker.STALE_MS + 1);
        assertEquals(0, tracker.trackedCount());
    }
}
