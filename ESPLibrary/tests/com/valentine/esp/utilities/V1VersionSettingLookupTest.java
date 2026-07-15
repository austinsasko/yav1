package com.valentine.esp.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * V1 Gen2 recognition is version based: any V1 reporting V4.1000 or later is a
 * second generation unit.
 */
public class V1VersionSettingLookupTest
{
	private final V1VersionSettingLookup lookup = new V1VersionSettingLookup();

	@Before
	public void setUp()
	{
		V1VersionSettingLookup.resetV1Version();
	}

	@After
	public void tearDown()
	{
		V1VersionSettingLookup.resetV1Version();
	}

	@Test
	public void defaultIsNotGen2()
	{
		assertFalse(V1VersionSettingLookup.isGen2());
	}

	@Test
	public void gen1VersionsAreNotGen2()
	{
		lookup.setV1Version("V3.8952");
		assertFalse(V1VersionSettingLookup.isGen2());
		assertEquals(3.8952, V1VersionSettingLookup.getV1Version(), 1e-9);

		lookup.setV1Version("V3.9999");
		assertFalse(V1VersionSettingLookup.isGen2());
	}

	@Test
	public void gen2VersionsAreRecognized()
	{
		lookup.setV1Version("V4.1000");
		assertTrue(V1VersionSettingLookup.isGen2());

		lookup.setV1Version("V4.1037");
		assertTrue(V1VersionSettingLookup.isGen2());
		assertEquals(4.1037, V1VersionSettingLookup.getV1Version(), 1e-9);
	}

	@Test
	public void junkAlertReportingStartsAt4_1032()
	{
		lookup.setV1Version("V4.1031");
		assertTrue(V1VersionSettingLookup.isGen2());
		assertFalse(V1VersionSettingLookup.isJunkAlertReported());

		lookup.setV1Version("V4.1032");
		assertTrue(V1VersionSettingLookup.isJunkAlertReported());
	}

	@Test
	public void malformedVersionsFallBackToDefault()
	{
		lookup.setV1Version("V4.1032");
		assertTrue(V1VersionSettingLookup.isGen2());

		lookup.setV1Version("Vgarbage");
		assertFalse(V1VersionSettingLookup.isGen2());

		// strings not starting with V (e.g. a Concealed Display "C1.0018") are ignored
		lookup.setV1Version("V4.1032");
		lookup.setV1Version("C1.0018");
		assertTrue(V1VersionSettingLookup.isGen2());

		// null / empty must not throw
		lookup.setV1Version(null);
		lookup.setV1Version("");
		assertTrue(V1VersionSettingLookup.isGen2());
	}

	@Test
	public void resetForgetsTheVersion()
	{
		lookup.setV1Version("V4.1032");
		assertTrue(V1VersionSettingLookup.isGen2());

		V1VersionSettingLookup.resetV1Version();
		assertFalse(V1VersionSettingLookup.isGen2());
	}
}
