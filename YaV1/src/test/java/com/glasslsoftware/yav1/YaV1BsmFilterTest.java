package com.glasslsoftware.yav1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1lib.YaV1Alert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class YaV1BsmFilterTest
{
	@Before
	public void setUp()
	{
		YaV1BsmFilter.setEnabled(true);
		YaV1BsmFilter.sHoldMs     = 1500;
		YaV1BsmFilter.sRampHoldMs = 3500;
		YaV1BsmFilter.sRampLeds   = 3;
	}

	@After
	public void tearDown()
	{
		YaV1BsmFilter.setEnabled(false);
	}

	@Test
	public void disabledFilterNeverHolds()
	{
		YaV1BsmFilter.setEnabled(false);

		assertFalse(YaV1BsmFilter.shouldHoldNew(YaV1Alert.BAND_K));
		assertFalse(YaV1BsmFilter.shouldStayHeld(0, 1, 8, false));
		assertFalse(YaV1BsmFilter.shouldStayHeld(0, 1, 8, true));
	}

	@Test
	public void onlyKBandAlertsEnterTheFilter()
	{
		assertTrue(YaV1BsmFilter.shouldHoldNew(YaV1Alert.BAND_K));

		assertFalse(YaV1BsmFilter.shouldHoldNew(YaV1Alert.BAND_KA));
		assertFalse(YaV1BsmFilter.shouldHoldNew(YaV1Alert.BAND_X));
		assertFalse(YaV1BsmFilter.shouldHoldNew(YaV1Alert.BAND_KU));
		assertFalse(YaV1BsmFilter.shouldHoldNew(YaV1Alert.BAND_LASER));
	}

	@Test
	public void youngAlertsAreHeld()
	{
		assertTrue(YaV1BsmFilter.shouldStayHeld(0, 3, 3, false));
		assertTrue(YaV1BsmFilter.shouldStayHeld(1499, 3, 3, false));
	}

	@Test
	public void steadyAlertIsReleasedAfterHold()
	{
		// signal did not ramp: released as soon as the base hold expires
		assertFalse(YaV1BsmFilter.shouldStayHeld(1500, 3, 4, false));
		assertFalse(YaV1BsmFilter.shouldStayHeld(2000, 3, 5, false));
	}

	@Test
	public void rapidRampExtendsTheHold()
	{
		// jumped from 1 to 6 LEDs while young: typical BSM pass-by
		assertTrue(YaV1BsmFilter.shouldStayHeld(2000, 1, 6, false));
		assertTrue(YaV1BsmFilter.shouldStayHeld(3499, 1, 6, false));

		// but even a ramping signal is eventually believed
		assertFalse(YaV1BsmFilter.shouldStayHeld(3500, 1, 6, false));
		assertFalse(YaV1BsmFilter.shouldStayHeld(10000, 1, 8, false));
	}

	@Test
	public void smallRampDoesNotExtendTheHold()
	{
		// 2 LED increase is normal approach behavior, not a BSM signature
		assertFalse(YaV1BsmFilter.shouldStayHeld(1500, 3, 5, false));
	}

	@Test
	public void junkFlaggedAlertsStayHeld()
	{
		// the V1 Gen2 flagged it as junk: keep holding no matter the age
		assertTrue(YaV1BsmFilter.shouldStayHeld(60000, 3, 3, true));
	}
}
