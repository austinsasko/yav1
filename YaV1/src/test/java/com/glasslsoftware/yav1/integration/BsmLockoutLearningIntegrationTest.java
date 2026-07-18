package com.glasslsoftware.yav1.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.BsmFilterTestHook;
import com.glasslsoftware.yav1.YaV1BsmFilter;
import com.glasslsoftware.yav1.lockout.Lockout;
import com.glasslsoftware.yav1.lockout.LockoutParam;
import com.glasslsoftware.yav1lib.YaV1Alert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * [QA][integration] K-band alert lifecycle across the BSM filter and the
 * lockout learning logic, simulating the alert service's call sequence
 * with real collaborators (YaV1BsmFilter + Lockout + LockoutParam):
 *
 *  - a short-lived BSM false is held its whole life and never voiced
 *  - a persistent stationary source (door opener) survives the BSM hold,
 *    then accumulates visit-separated sightings until it auto-locks,
 *    tracking the source's oscillator drift on the way
 *  - once the source disappears, consecutive misses unlock it again
 */
public class BsmLockoutLearningIntegrationTest
{
    private int savedMaxUnseen;
    private int savedMaxManualUnseen;
    private int savedMaxWhiteUnseen;
    private int savedMinSeen;
    private int savedVisitGap;

    @Before
    public void setUp()
    {
        savedMaxUnseen       = LockoutParam.mMaxUnseen;
        savedMaxManualUnseen = LockoutParam.mMaxManualUnseen;
        savedMaxWhiteUnseen  = LockoutParam.mMaxWhiteUnseen;
        savedMinSeen         = LockoutParam.mMinSeen;
        savedVisitGap        = LockoutParam.mVisitGapSec;

        LockoutParam.mMaxUnseen       = 2;
        LockoutParam.mMaxManualUnseen = 4;
        LockoutParam.mMaxWhiteUnseen  = 0;
        LockoutParam.mMinSeen         = 5;
        LockoutParam.mVisitGapSec     = 1800;

        BsmFilterTestHook.setEnabled(true);
        YaV1BsmFilter.sHoldMs     = 1500;
        YaV1BsmFilter.sRampHoldMs = 3500;
        YaV1BsmFilter.sRampLeds   = 3;
    }

    @After
    public void tearDown()
    {
        LockoutParam.mMaxUnseen       = savedMaxUnseen;
        LockoutParam.mMaxManualUnseen = savedMaxManualUnseen;
        LockoutParam.mMaxWhiteUnseen  = savedMaxWhiteUnseen;
        LockoutParam.mMinSeen         = savedMinSeen;
        LockoutParam.mVisitGapSec     = savedVisitGap;

        BsmFilterTestHook.setEnabled(false);
    }

    // ------------------------------------------------------------- BSM side

    @Test
    public void bsmFalseDiesInsideTheHoldAndIsNeverHeard()
    {
        // new K-band alert pops up
        assertTrue(YaV1BsmFilter.shouldHoldNew(YaV1Alert.BAND_K));

        // passing BSM car: signal ramps 1 -> 6 LEDs within two seconds
        assertTrue(YaV1BsmFilter.shouldStayHeld(500, 1, 3, false));
        assertTrue(YaV1BsmFilter.shouldStayHeld(1400, 1, 5, false));
        // past the base hold, but the rapid ramp extends it
        assertTrue(YaV1BsmFilter.shouldStayHeld(2200, 1, 6, false));
        assertTrue(YaV1BsmFilter.shouldStayHeld(3400, 1, 6, false));

        // the alert dies at ~3.4s while still held: the false was filtered
        // (nothing more to assert - the alert never left the filter)
    }

    @Test
    public void persistentSourceOutlivesTheHoldAndStartsLearning()
    {
        // a real stationary source: steady signal, no BSM-style ramp
        assertTrue(YaV1BsmFilter.shouldHoldNew(YaV1Alert.BAND_K));
        assertTrue(YaV1BsmFilter.shouldStayHeld(800, 3, 4, false));

        // survives the 1.5s persistence window -> released to the driver
        assertFalse(YaV1BsmFilter.shouldStayHeld(1600, 3, 4, false));

        // ...and the released alert is what the lockout learning then sees
        long nowSec = 1_700_000_000L;
        Lockout l = new Lockout(1, 0, 45.0, -75.0, 90, 24198, 20f, 0, 0, 55, 0, nowSec);

        assertEquals("visit tracking is seeded from the DB timestamp",
                     nowSec, l.mLastSeenTime);
        assertTrue("first sighting is a fresh visit",
                   Lockout.isNewVisit(0, nowSec, LockoutParam.mVisitGapSec));
    }

    // -------------------------------------------------------- learning side

    @Test
    public void doorOpenerLocksAfterFiveSeparateCommutesDespiteDrift()
    {
        long baseSec = 1_700_000_000L;
        int  source  = 24150;               // the door opener's true frequency

        // freshly created lockout row after the first sighting
        Lockout l = new Lockout(1, 0, 45.0, -75.0, 90, source, 20f, 1, 0, 55, 0, baseSec);
        l.mLastSeenTime = baseSec;

        int drift = LockoutParam.mDrift[YaV1Alert.BAND_K];   // 10 MHz for K

        // commutes 2..5: one sighting each, hours apart, source drifting
        for(int visit = 2; visit <= 5; visit++)
        {
            long visitSec = baseSec + (visit - 1) * 3600L;
            source += 2;                    // slow oscillator drift

            // the alert only matches the lockout while inside the window
            assertTrue("visit " + visit + ": source drifted out of the match window",
                       Math.abs(source - l.mFrequency) <= drift);

            boolean newVisit = Lockout.isNewVisit(l.mLastSeenTime, visitSec,
                                                  LockoutParam.mVisitGapSec);
            assertTrue("hours apart must count as a new visit", newVisit);

            l.mFrequency = Lockout.trackFrequencyForVisit(l.mFrequency, source, newVisit);
            if(newVisit)
                l.mSeen++;
            l.mLastSeenTime = visitSec;

            // repeated packets during the same pass never double-count
            boolean samePass = Lockout.isNewVisit(l.mLastSeenTime, visitSec + 10,
                                                  LockoutParam.mVisitGapSec);
            assertFalse(samePass);
            l.mFrequency = Lockout.trackFrequencyForVisit(l.mFrequency, source, samePass);
        }

        assertEquals("five separate visits accumulated", 5, l.mSeen);
        assertTrue("auto-lock threshold reached", l.mSeen >= LockoutParam.mMinSeen);
        assertTrue("stored frequency followed the drift",
                   l.mFrequency > 24150 && Math.abs(l.mFrequency - source) <= drift);
    }

    @Test
    public void removedDoorOpenerUnlocksAfterConsecutiveMisses()
    {
        long baseSec = 1_700_000_000L;

        // an established lockout (5 sightings)
        Lockout l = new Lockout(1, 0, 45.0, -75.0, 90, 24158, 20f, 5, 0, 55, 0, baseSec);

        // the shop closes, the door opener is gone: two passes through the
        // area where the signal was expected but absent
        l.resetOnEnterCurrentArea(1, 30, 140);
        assertTrue(l.checkForUpdate());
        assertEquals(1, l.mMissed);
        assertTrue((l.mFlag & Lockout.LOCKOUT_UPDATE) > 0);
        assertFalse((l.mFlag & Lockout.LOCKOUT_REMOVE) > 0);

        l.resetFlag();
        l.resetBusyNotInArea();
        l.resetOnEnterCurrentArea(1, 30, 140);
        assertTrue(l.checkForUpdate());
        assertEquals(2, l.mMissed);
        assertTrue("second consecutive miss reaches mMaxUnseen -> auto unlock",
                   (l.mFlag & Lockout.LOCKOUT_REMOVE) > 0);
    }

    @Test
    public void manualLockoutSurvivesLongerAndExpiryIsIndependent()
    {
        long baseSec = 1_700_000_000L;

        Lockout manual = new Lockout(2, Lockout.LOCKOUT_MANUAL, 45.0, -75.0,
                                     90, 24150, 20f, 20, 2, 55, 0, baseSec);

        // misses 3 and 4: a learned lockout would be gone at 2
        for(int miss = 3; miss <= 4; miss++)
        {
            manual.resetFlag();
            manual.resetOnEnterCurrentArea(1, 30, 140);
            assertTrue(manual.checkForUpdate());
            assertEquals(miss, manual.mMissed);
        }

        assertTrue("manual limit (4) reached", (manual.mFlag & Lockout.LOCKOUT_REMOVE) > 0);

        // age-based expiry is a separate, orthogonal unlock path
        assertFalse(Lockout.isExpired(baseSec, baseSec + 89L * 86400L, 90));
        assertTrue(Lockout.isExpired(baseSec, baseSec + 90L * 86400L, 90));
    }
}
