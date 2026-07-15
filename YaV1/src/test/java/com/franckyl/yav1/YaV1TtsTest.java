package com.franckyl.yav1;

import static org.junit.Assert.assertEquals;

import com.franckyl.yav1lib.YaV1Alert;

import org.junit.Test;

public class YaV1TtsTest
{
	@Test
	public void formatsBandDirectionAndFrequency()
	{
		assertEquals("Ka front, 35.5",
				YaV1Tts.buildPhrase(YaV1Alert.BAND_KA, YaV1Alert.ALERT_FRONT, 35500));
		assertEquals("K rear, 24.2",
				YaV1Tts.buildPhrase(YaV1Alert.BAND_K, YaV1Alert.ALERT_REAR, 24150));
		assertEquals("X side, 10.5",
				YaV1Tts.buildPhrase(YaV1Alert.BAND_X, YaV1Alert.ALERT_SIDE, 10520));
		assertEquals("Ku front, 13.5",
				YaV1Tts.buildPhrase(YaV1Alert.BAND_KU, YaV1Alert.ALERT_FRONT, 13450));
	}

	@Test
	public void laserOmitsFrequency()
	{
		assertEquals("Laser front",
				YaV1Tts.buildPhrase(YaV1Alert.BAND_LASER, YaV1Alert.ALERT_FRONT, 0));
		assertEquals("Laser rear",
				YaV1Tts.buildPhrase(YaV1Alert.BAND_LASER, YaV1Alert.ALERT_REAR, 0));
	}

	@Test
	public void unknownBandProducesNothing()
	{
		assertEquals("", YaV1Tts.buildPhrase(-1, YaV1Alert.ALERT_FRONT, 35500));
		assertEquals("", YaV1Tts.buildPhrase(99, YaV1Alert.ALERT_FRONT, 35500));
	}

	@Test
	public void frequencyUsesDotDecimalRegardlessOfLocale()
	{
		java.util.Locale saved = java.util.Locale.getDefault();
		try
		{
			java.util.Locale.setDefault(java.util.Locale.GERMANY);
			assertEquals("Ka front, 34.7",
					YaV1Tts.buildPhrase(YaV1Alert.BAND_KA, YaV1Alert.ALERT_FRONT, 34700));
		}
		finally
		{
			java.util.Locale.setDefault(saved);
		}
	}
}
