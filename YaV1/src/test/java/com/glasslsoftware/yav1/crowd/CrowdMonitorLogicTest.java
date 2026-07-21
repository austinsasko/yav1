package com.glasslsoftware.yav1.crowd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [CSA] CrowdMonitor's pure decision logic: poll-failure backoff, the
 * one-tap report cooldown, and announce selection (once per report id,
 * relevance-gated, re-announce only after the report expires and returns).
 */
public class CrowdMonitorLogicTest
{
    // ------------------------------------------------------------ backoff

    @Test
    public void healthyPollKeepsBaseInterval()
    {
        assertEquals(60_000L, CrowdMonitor.nextPollDelayMs(0));
        assertEquals(60_000L, CrowdMonitor.nextPollDelayMs(-1));
    }

    @Test
    public void delayDoublesPerFailureUpToCap()
    {
        assertEquals(120_000L, CrowdMonitor.nextPollDelayMs(1));
        assertEquals(240_000L, CrowdMonitor.nextPollDelayMs(2));
        assertEquals(480_000L, CrowdMonitor.nextPollDelayMs(3));
        assertEquals(480_000L, CrowdMonitor.nextPollDelayMs(4));   // capped
        assertEquals(480_000L, CrowdMonitor.nextPollDelayMs(100)); // no overflow
    }

    // ------------------------------------------------------------ cooldown

    @Test
    public void firstReportIsAlwaysAllowed()
    {
        assertTrue(CrowdMonitor.cooldownElapsed(123L, 0L));
    }

    @Test
    public void repeatTapInsideCooldownIsBlocked()
    {
        long sent = 1_000_000L;
        assertFalse(CrowdMonitor.cooldownElapsed(sent + 1, sent));
        assertFalse(CrowdMonitor.cooldownElapsed(sent + CrowdMonitor.REPORT_COOLDOWN_MS - 1, sent));
        assertTrue(CrowdMonitor.cooldownElapsed(sent + CrowdMonitor.REPORT_COOLDOWN_MS, sent));
    }

    // ------------------------------------------------------------ announce selection

    private static CrowdAlert police(String id, double lat, double lon)
    {
        return new CrowdAlert(id, CrowdAlert.KIND_POLICE, lat, lon, 0, 0, "waze");
    }

    // vehicle at Denver heading north
    private static final double LAT = 39.73;
    private static final double LON = -104.99;
    private static final int    BRG = 0;

    @Test
    public void policeAnnouncedOnceThenDeduped()
    {
        CrowdAlert  near  = police("p-1", LAT + 0.005, LON); // ~550 m
        Set<String> heard = new HashSet<String>();

        List<CrowdAlert> first = CrowdMonitor.selectAnnouncements(
                Arrays.asList(near), heard, LAT, LON, BRG);
        assertEquals(1, first.size());
        assertEquals("p-1", first.get(0).id);

        // same report on the next cycle: silent
        List<CrowdAlert> second = CrowdMonitor.selectAnnouncements(
                Arrays.asList(near), heard, LAT, LON, BRG);
        assertTrue(second.isEmpty());
    }

    @Test
    public void expiredReportCanAnnounceAgainWhenItReturns()
    {
        CrowdAlert  near  = police("p-1", LAT + 0.005, LON);
        Set<String> heard = new HashSet<String>();

        CrowdMonitor.selectAnnouncements(Arrays.asList(near), heard, LAT, LON, BRG);
        assertTrue(heard.contains("p-1"));

        // report drops out of the feed: pruned from the announced set
        CrowdMonitor.selectAnnouncements(new ArrayList<CrowdAlert>(), heard, LAT, LON, BRG);
        assertTrue(heard.isEmpty());

        // and announces again on return
        List<CrowdAlert> again = CrowdMonitor.selectAnnouncements(
                Arrays.asList(near), heard, LAT, LON, BRG);
        assertEquals(1, again.size());
    }

    @Test
    public void nonPoliceKindsAreNeverAnnounced()
    {
        CrowdAlert crash  = new CrowdAlert("c-1", CrowdAlert.KIND_ACCIDENT,
                                           LAT + 0.005, LON, 0, 0, "waze");
        CrowdAlert hazard = new CrowdAlert("h-1", CrowdAlert.KIND_HAZARD,
                                           LAT + 0.005, LON, 0, 0, "waze");
        Set<String> heard = new HashSet<String>();

        List<CrowdAlert> selected = CrowdMonitor.selectAnnouncements(
                Arrays.asList(crash, hazard), heard, LAT, LON, BRG);
        assertTrue(selected.isEmpty());
    }

    @Test
    public void irrelevantPoliceIsNotAnnouncedAndNotMarked()
    {
        // ~3.3 km due north while heading south: inside 5 km but outside the cone
        CrowdAlert behind = police("p-2", LAT + 0.03, LON);
        Set<String> heard = new HashSet<String>();

        List<CrowdAlert> selected = CrowdMonitor.selectAnnouncements(
                Arrays.asList(behind), heard, LAT, LON, 180);
        assertTrue(selected.isEmpty());
        // not marked announced: it can still announce later once relevant
        assertFalse(heard.contains("p-2"));
    }

    @Test
    public void aheadInConeIsAnnounced()
    {
        CrowdAlert ahead = police("p-3", LAT + 0.03, LON); // ~3.3 km due north
        Set<String> heard = new HashSet<String>();

        List<CrowdAlert> selected = CrowdMonitor.selectAnnouncements(
                Arrays.asList(ahead), heard, LAT, LON, BRG);
        assertEquals(1, selected.size());
    }
}
