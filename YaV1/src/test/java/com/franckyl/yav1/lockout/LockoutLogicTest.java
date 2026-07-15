package com.franckyl.yav1.lockout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the lockout learning logic: visit separated sighting counting,
 * frequency drift tracking, and the automatic unlock paths (consecutive misses
 * and age based expiry).
 */
public class LockoutLogicTest
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

	// ---------------------------------------------------------------- visits

	@Test
	public void firstSightingIsAlwaysANewVisit()
	{
		assertTrue(Lockout.isNewVisit(0, 1000000, 1800));
		assertTrue(Lockout.isNewVisit(-1, 1000000, 1800));
	}

	@Test
	public void sightingWithinGapIsSameVisit()
	{
		long lastSeen = 1000000;
		assertFalse(Lockout.isNewVisit(lastSeen, lastSeen + 1, 1800));
		assertFalse(Lockout.isNewVisit(lastSeen, lastSeen + 1799, 1800));
	}

	@Test
	public void sightingAfterGapIsANewVisit()
	{
		long lastSeen = 1000000;
		assertTrue(Lockout.isNewVisit(lastSeen, lastSeen + 1800, 1800));
		assertTrue(Lockout.isNewVisit(lastSeen, lastSeen + 86400, 1800));
	}

	@Test
	public void zeroGapRestoresLegacyBehavior()
	{
		long lastSeen = 1000000;
		assertTrue(Lockout.isNewVisit(lastSeen, lastSeen + 1, 0));
		assertTrue(Lockout.isNewVisit(lastSeen, lastSeen, 0));
	}

	// ------------------------------------------------------ frequency drift

	@Test
	public void trackFrequencyConvergesTowardObservation()
	{
		// stored 24150, observed 24158: moves halfway
		assertEquals(24154, Lockout.trackFrequency(24150, 24158));
		// and keeps converging on repeated observations
		assertEquals(24156, Lockout.trackFrequency(24154, 24158));
		assertEquals(24157, Lockout.trackFrequency(24156, 24158));
		assertEquals(24158, Lockout.trackFrequency(24157, 24158));
	}

	@Test
	public void trackFrequencyWorksDownward()
	{
		assertEquals(24146, Lockout.trackFrequency(24150, 24142));
		assertEquals(24149, Lockout.trackFrequency(24150, 24148));
		assertEquals(24149, Lockout.trackFrequency(24150, 24149));
	}

	@Test
	public void trackFrequencyIsStableOnExactMatch()
	{
		assertEquals(24150, Lockout.trackFrequency(24150, 24150));
	}

	@Test
	public void trackFrequencyFollowsSlowDriftOutOfTheOriginalWindow()
	{
		// a source drifting 2 MHz per visit, with a 10 MHz match window: the
		// stored frequency follows, so the source never escapes the window
		int stored = 24150;
		int source = 24150;
		for(int visit = 0; visit < 10; visit++)
		{
			source += 2;
			assertTrue("visit " + visit + " drifted outside the window",
					Math.abs(source - stored) <= 10);
			stored = Lockout.trackFrequency(stored, source);
		}
		assertTrue(stored >= 24165);
	}

	// ------------------------------------------------------------- expiry

	@Test
	public void expiryDisabledNeverExpires()
	{
		assertFalse(Lockout.isExpired(1000, 1000 + 365L * 86400L, 0));
	}

	@Test
	public void expiresAfterConfiguredDays()
	{
		long now = 2000000000L;
		long fresh = now - 89L * 86400L;
		long stale = now - 90L * 86400L;

		assertFalse(Lockout.isExpired(fresh, now, 90));
		assertTrue(Lockout.isExpired(stale, now, 90));
		assertTrue(Lockout.isExpired(now - 200L * 86400L, now, 90));
	}

	@Test
	public void zeroTimestampNeverExpires()
	{
		// legacy rows without a timestamp are kept
		assertFalse(Lockout.isExpired(0, 2000000000L, 90));
	}

	// -------------------------------------------- unseen based auto unlock

	private Lockout dbLockout(int flag, int seen, int missed)
	{
		return new Lockout(7, flag, 45.0, -75.0, 90, 24150, 20f, seen, missed, 55, 0, 1000000);
	}

	@Test
	public void lockedSignalThatStopsAppearingIsRemoved()
	{
		LockoutParam.mMaxUnseen = 2;

		Lockout l = dbLockout(0, 5, 0);

		// the lockout was expected (visible) but not seen on this pass
		l.mShouldBe  = 1;
		l.mLocalSeen = 0;
		l.mNbCheck   = 1;

		assertTrue(l.checkForUpdate());
		assertEquals(1, l.mMissed);
		assertTrue((l.mFlag & Lockout.LOCKOUT_UPDATE) > 0);
		assertFalse((l.mFlag & Lockout.LOCKOUT_REMOVE) > 0);

		// second consecutive miss reaches the limit: automatic unlock
		l.resetFlag();
		l.mShouldBe  = 1;
		l.mLocalSeen = 0;

		assertTrue(l.checkForUpdate());
		assertEquals(2, l.mMissed);
		assertTrue((l.mFlag & Lockout.LOCKOUT_REMOVE) > 0);
	}

	@Test
	public void manualLockoutGetsExtraMisses()
	{
		LockoutParam.mMaxUnseen       = 2;
		LockoutParam.mMaxManualUnseen = 4;

		Lockout l = dbLockout(Lockout.LOCKOUT_MANUAL, 20, 2);
		l.mShouldBe  = 1;
		l.mLocalSeen = 0;

		assertTrue(l.checkForUpdate());
		assertEquals(3, l.mMissed);
		// 3 misses would remove a learned lockout but not a manual one
		assertFalse((l.mFlag & Lockout.LOCKOUT_REMOVE) > 0);

		l.resetFlag();
		l.mShouldBe  = 1;
		l.mLocalSeen = 0;
		assertTrue(l.checkForUpdate());
		assertEquals(4, l.mMissed);
		assertTrue((l.mFlag & Lockout.LOCKOUT_REMOVE) > 0);
	}

	@Test
	public void unexpectedLockoutIsNotCountedMissing()
	{
		LockoutParam.mMaxUnseen = 2;

		Lockout l = dbLockout(0, 5, 1);
		// not expected to be visible on this pass
		l.mShouldBe  = 0;
		l.mLocalSeen = 0;

		assertFalse(l.checkForUpdate());
		assertEquals(1, l.mMissed);
	}

	@Test
	public void dbConstructorSeedsVisitTrackingFromTimestamp()
	{
		Lockout l = dbLockout(0, 5, 0);
		assertEquals(1000000, l.mLastSeenTime);
	}
}
