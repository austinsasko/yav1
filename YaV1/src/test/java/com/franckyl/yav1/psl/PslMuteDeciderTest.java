package com.franckyl.yav1.psl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * [P1-PSL] Mute decision matrix, hysteresis and unit conversion.
 *
 * Base scenario: limit 50 kph, offset 8 kph -> mute threshold 58 kph,
 * hysteresis band up to 60 kph, exit after >2s above the band.
 */
public class PslMuteDeciderTest
{
	private static final Integer LIMIT  = 50;
	private static final double  OFFSET = 8.0;

	private PslMuteDecider mDecider;

	@Before
	public void setUp()
	{
		mDecider = new PslMuteDecider();
	}

	private boolean decide(long now, double speed)
	{
		return mDecider.decide(now, speed, LIMIT, OFFSET, false);
	}

	// -- basic matrix ----------------------------------------------------

	@Test
	public void mutesWellBelowTheLimit()
	{
		assertTrue(decide(0, 40.0));
	}

	@Test
	public void mutesExactlyAtLimitPlusOffset()
	{
		assertTrue(decide(0, 58.0));
	}

	@Test
	public void staysAudibleWhenAlreadySpeeding()
	{
		// never muted, so no hysteresis applies
		assertFalse(decide(0, 100.0));
		assertFalse(decide(1000, 58.5));
	}

	@Test
	public void mutesImmediatelyWhenDroppingToThreshold()
	{
		assertFalse(decide(0, 100.0));
		assertTrue(decide(1000, 58.0));
	}

	// -- hysteresis --------------------------------------------------------

	@Test
	public void staysMutedInsideTheHysteresisBand()
	{
		assertTrue(decide(0, 40.0));
		// 59-60 kph is above threshold but within threshold + 2
		assertTrue(decide(1000, 59.0));
		assertTrue(decide(60000, 60.0));
	}

	@Test
	public void staysMutedUntilTwoSecondsAboveTheBand()
	{
		assertTrue(decide(0, 40.0));
		assertTrue(decide(1000, 61.0));   // timer starts
		assertTrue(decide(2500, 61.0));   // 1500ms: still muted
		assertTrue(decide(3000, 61.0));   // exactly 2000ms: still muted
		assertFalse(decide(3002, 61.0));  // >2000ms: unmuted
	}

	@Test
	public void dippingBackIntoTheBandResetsTheExitTimer()
	{
		assertTrue(decide(0, 40.0));
		assertTrue(decide(1000, 61.0));   // timer starts
		assertTrue(decide(2000, 59.5));   // back in band: reset
		assertTrue(decide(3000, 61.0));   // timer restarts
		assertTrue(decide(4900, 61.0));   // 1900ms: still muted
		assertFalse(decide(5100, 61.0));  // 2100ms: unmuted
	}

	@Test
	public void remutesImmediatelyAfterAnUnmute()
	{
		assertTrue(decide(0, 40.0));
		assertTrue(decide(0, 65.0));
		assertFalse(decide(2500, 65.0));
		assertTrue(decide(3000, 57.0));
	}

	// -- unknown limit -----------------------------------------------------

	@Test
	public void unknownLimitStaysAudibleByDefault()
	{
		assertFalse(mDecider.decide(0, 40.0, null, OFFSET, false));
	}

	@Test
	public void unknownLimitMutesWhenConfigured()
	{
		assertTrue(mDecider.decide(0, 40.0, null, OFFSET, true));
	}

	@Test
	public void unknownLimitClearsHysteresisState()
	{
		assertTrue(decide(0, 40.0));
		// limit disappears, behavior says stay audible
		assertFalse(mDecider.decide(1000, 40.0, null, OFFSET, false));
		// limit comes back while speeding: not muted, no leftover state
		assertFalse(decide(2000, 100.0));
	}

	// -- invalid speed -----------------------------------------------------

	@Test
	public void negativeSpeedNeverMutesAndResets()
	{
		assertTrue(decide(0, 40.0));
		assertFalse(decide(1000, -10.0));
		// state was dropped: high speed does not re-enter via hysteresis
		assertFalse(decide(2000, 100.0));
		assertFalse(mDecider.isMuted());
	}

	// -- unit conversion ---------------------------------------------------

	@Test
	public void convertsMphToKph()
	{
		assertEquals(88.51392, PslMuteDecider.toKph(55.0, PslMuteDecider.UNIT_MPH), 1e-6);
		assertEquals(8.04672, PslMuteDecider.toKph(5.0, PslMuteDecider.UNIT_MPH), 1e-6);
	}

	@Test
	public void kphPassesThroughUnchanged()
	{
		assertEquals(50.0, PslMuteDecider.toKph(50.0, PslMuteDecider.UNIT_KPH), 1e-9);
		// unknown unit index behaves as kph
		assertEquals(50.0, PslMuteDecider.toKph(50.0, 7), 1e-9);
	}

	@Test
	public void mphScenarioMatrix()
	{
		// US driver: limit 55 mph (Overpass returns 89 kph), offset 5 mph
		Integer limitKph  = 89;
		double  offsetKph = PslMuteDecider.toKph(5.0, PslMuteDecider.UNIT_MPH); // 8.05

		// 55 mph -> 88.5 kph: muted
		double v55 = PslMuteDecider.toKph(55.0, PslMuteDecider.UNIT_MPH);
		assertTrue(mDecider.decide(0, v55, limitKph, offsetKph, false));

		// 65 mph -> 104.6 kph, above 89 + 8.05 + 2: unmutes after 2s
		double v65 = PslMuteDecider.toKph(65.0, PslMuteDecider.UNIT_MPH);
		assertTrue(mDecider.decide(1000, v65, limitKph, offsetKph, false));
		assertFalse(mDecider.decide(3500, v65, limitKph, offsetKph, false));
	}

	@Test
	public void resetClearsState()
	{
		assertTrue(decide(0, 40.0));
		mDecider.reset();
		assertFalse(mDecider.isMuted());
		assertFalse(decide(1000, 100.0));
	}
}
