package com.glasslsoftware.yav1.lockout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * [QA] Component tests for Lockout state transitions beyond
 * LockoutLogicTest: signal encoding helpers, manual/white forcing, flag
 * reset semantics, white-list unseen behavior, the UPDATE short-circuit in
 * checkForUpdate and the area enter/leave bookkeeping.
 */
public class LockoutBehaviorTest
{
    private int savedMaxUnseen;
    private int savedMaxManualUnseen;
    private int savedMaxWhiteUnseen;

    @Before
    public void setUp()
    {
        savedMaxUnseen       = LockoutParam.mMaxUnseen;
        savedMaxManualUnseen = LockoutParam.mMaxManualUnseen;
        savedMaxWhiteUnseen  = LockoutParam.mMaxWhiteUnseen;
    }

    @After
    public void tearDown()
    {
        LockoutParam.mMaxUnseen       = savedMaxUnseen;
        LockoutParam.mMaxManualUnseen = savedMaxManualUnseen;
        LockoutParam.mMaxWhiteUnseen  = savedMaxWhiteUnseen;
    }

    private static Lockout dbLockout(int flag, int seen, int missed)
    {
        return new Lockout(7, flag, 45.0, -75.0, 90, 24150, 20f, seen, missed, 55, 0, 1000000);
    }

    // -- signal encoding ---------------------------------------------------

    @Test
    public void signalParamEncodesFrontAndRearLeds()
    {
        // param1 = (front + 1) * 10 + (rear + 1)
        assertEquals(4, Lockout.getFrontSignal(57));
        assertEquals(6, Lockout.getRearSignal(57));

        assertEquals(0, Lockout.getFrontSignal(11));
        assertEquals(0, Lockout.getRearSignal(11));
    }

    @Test
    public void reverseParamSignalSwapsFrontAndRear()
    {
        assertEquals(75, Lockout.reverseParamSignal(57));
        assertEquals(57, Lockout.reverseParamSignal(75));
        // reversing twice is the identity
        assertEquals(42, Lockout.reverseParamSignal(Lockout.reverseParamSignal(42)));
    }

    @Test
    public void maxSignalUsesTheStrongerSide()
    {
        Lockout l = dbLockout(0, 5, 0);          // param1 = 55: front 4, rear 4
        assertEquals(4, l.getMaxSignal());

        l.mParam1 = 38;                           // front 2, rear 7
        assertEquals(7, l.getMaxSignal());
    }

    // -- manual / white forcing -------------------------------------------

    @Test
    public void forceLockoutClearsWhiteAndSetsManual()
    {
        Lockout l = dbLockout(Lockout.LOCKOUT_WHITE, 3, 2);

        l.forceLockout();

        assertEquals(Lockout.MANUAL_LOCKOUT_COUNT, l.mSeen);
        assertEquals(0, l.mMissed);
        assertTrue((l.mFlag & Lockout.LOCKOUT_MANUAL) > 0);
        assertTrue((l.mFlag & Lockout.LOCKOUT_UPDATE) > 0);
        assertFalse((l.mFlag & Lockout.LOCKOUT_WHITE) > 0);
    }

    @Test
    public void forceWhiteClearsManualAndSetsWhite()
    {
        Lockout l = dbLockout(Lockout.LOCKOUT_MANUAL, 3, 2);

        l.forceWhite();

        assertEquals(Lockout.MANUAL_LOCKOUT_COUNT, l.mSeen);
        assertEquals(0, l.mMissed);
        assertTrue((l.mFlag & Lockout.LOCKOUT_WHITE) > 0);
        assertFalse((l.mFlag & Lockout.LOCKOUT_MANUAL) > 0);
    }

    @Test
    public void resetFlagClearsTransientBitsKeepsSticky()
    {
        Lockout l = dbLockout(Lockout.LOCKOUT_MANUAL | Lockout.LOCKOUT_NEW
                              | Lockout.LOCKOUT_UPDATE | Lockout.LOCKOUT_REMOVE, 5, 0);

        l.resetFlag();

        assertTrue((l.mFlag & Lockout.LOCKOUT_MANUAL) > 0);
        assertFalse((l.mFlag & Lockout.LOCKOUT_NEW) > 0);
        assertFalse((l.mFlag & Lockout.LOCKOUT_UPDATE) > 0);
        assertFalse((l.mFlag & Lockout.LOCKOUT_REMOVE) > 0);
    }

    // -- white-list unseen behavior ---------------------------------------

    @Test
    public void whiteListedLockoutIsNeverAutoRemovedByDefault()
    {
        LockoutParam.mMaxUnseen      = 2;
        LockoutParam.mMaxWhiteUnseen = 0;      // default: no white expiry

        Lockout l = dbLockout(Lockout.LOCKOUT_WHITE, 10, 0);

        for(int pass = 0; pass < 6; pass++)
        {
            l.resetFlag();
            l.mShouldBe  = 1;
            l.mLocalSeen = 0;

            assertTrue(l.checkForUpdate());
            assertFalse("white lockout must never hit REMOVE (pass " + pass + ")",
                        (l.mFlag & Lockout.LOCKOUT_REMOVE) > 0);
        }

        assertEquals(6, l.mMissed);
    }

    @Test
    public void whiteListedLockoutHonorsAConfiguredUnseenLimit()
    {
        LockoutParam.mMaxUnseen      = 2;
        LockoutParam.mMaxWhiteUnseen = 3;

        Lockout l = dbLockout(Lockout.LOCKOUT_WHITE, 10, 0);

        for(int pass = 1; pass <= 3; pass++)
        {
            l.resetFlag();
            l.mShouldBe  = 1;
            l.mLocalSeen = 0;
            assertTrue(l.checkForUpdate());
        }

        assertEquals(3, l.mMissed);
        assertTrue((l.mFlag & Lockout.LOCKOUT_REMOVE) > 0);
    }

    // -- checkForUpdate short-circuit --------------------------------------

    @Test
    public void pendingUpdateFlagReportsTrueWithoutCountingAMiss()
    {
        Lockout l = dbLockout(0, 5, 1);
        l.mFlag |= Lockout.LOCKOUT_UPDATE;
        l.mShouldBe  = 1;
        l.mLocalSeen = 0;

        assertTrue(l.checkForUpdate());
        assertEquals("missed must not increment when UPDATE was already set", 1, l.mMissed);
    }

    @Test
    public void seenLockoutIsNotCountedMissing()
    {
        LockoutParam.mMaxUnseen = 2;

        Lockout l = dbLockout(0, 5, 1);
        l.mShouldBe  = 1;
        l.mLocalSeen = 1;                       // it WAS seen on this pass

        assertFalse(l.checkForUpdate());
        assertEquals(1, l.mMissed);
    }

    // -- area enter / leave bookkeeping -----------------------------------

    @Test
    public void enteringTheAreaSeedsCheckStateAndSignals()
    {
        Lockout l = dbLockout(0, 5, 0);        // param1 = 55

        l.resetOnEnterCurrentArea(1, 30, 140);

        assertEquals(1, l.mShouldBe);
        assertEquals(1, l.mNbCheck);
        assertEquals(0, l.mLocalSeen);
        assertEquals(30, l.mAngle);
        assertEquals(140, l.mDistance);
        assertEquals(4, l.mFs);
        assertEquals(4, l.mRs);
    }

    @Test
    public void reenteringWhileBusyAccumulatesChecks()
    {
        Lockout l = dbLockout(0, 5, 0);
        l.resetOnEnterCurrentArea(1, 30, 140);

        l.mBusy = 1;
        l.mLocalSeen = 1;
        l.resetOnEnterCurrentArea(1, 25, 100);

        assertEquals(2, l.mShouldBe);
        assertEquals(2, l.mNbCheck);
        assertEquals("busy re-entry must not clear the seen mark", 1, l.mLocalSeen);
    }

    @Test
    public void resetOnOutFlagClearsBusyStateDuringCheck()
    {
        Lockout l = dbLockout(0, 5, 0);
        l.mBusy      = 1;
        l.mShouldBe  = 1;
        l.mLocalSeen = 1;                       // seen: no miss accounting
        l.mNbCheck   = 1;

        l.setResetOnOut();
        assertFalse(l.checkForUpdate());

        assertEquals(0, l.mBusy);
        assertEquals(0, l.mShouldBe);
        assertEquals(0, l.mNbCheck);
        assertFalse((l.mFlag & Lockout.LOCKOUT_RESETFLAG) > 0);
    }
}
