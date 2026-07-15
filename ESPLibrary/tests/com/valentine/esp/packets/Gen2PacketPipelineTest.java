package com.valentine.esp.packets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.data.SweepDefinition;
import com.valentine.esp.packets.response.ResponseSweepDefinitions;
import com.valentine.esp.packets.response.ResponseVersion;
import com.valentine.esp.packets.response.ResponseVolume;
import com.valentine.esp.utilities.V1VersionSettingLookup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * V1 Gen2 packets through the full decode pipeline, both SPP-framed (classic
 * V1connection PACK stream) and LE bare-framed (V1connection LE bridge), plus the
 * version-string identification they drive.
 */
public class Gen2PacketPipelineTest
{
	private static final int V1_NIBBLE  = 0x0A;
	private static final int GB_NIBBLE  = 0x08;
	private static final int APP_NIBBLE = 0x06;

	private static final byte[] DISPLAY_PAYLOAD =
			{0x77, 0x00, 0x00, 0x22, 0x00, 0x41, 0x00, 0x00};

	private final V1VersionSettingLookup lookup = new V1VersionSettingLookup();

	@Before
	public void setUp()
	{
		ESPPacket.resetDecoderState();
		V1VersionSettingLookup.resetV1Version();
	}

	@After
	public void tearDown()
	{
		V1VersionSettingLookup.resetV1Version();
	}

	private void learnV1Type()
	{
		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.packStream(GB_NIBBLE, V1_NIBBLE, 0x31, DISPLAY_PAYLOAD, true));
		assertNotNull(p);
	}

	private static byte[] versionPayload(String version)
	{
		byte[] payload = new byte[version.length()];
		for (int i = 0; i < version.length(); i++)
		{
			payload[i] = (byte) version.charAt(i);
		}
		return payload;
	}

	@Test
	public void gen2VersionResponseSppFramedDrivesGen2Detection()
	{
		learnV1Type();

		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.packStream(APP_NIBBLE, V1_NIBBLE, 0x02, versionPayload("V4.1035"), true));

		assertNotNull(p);
		assertTrue(p instanceof ResponseVersion);
		assertEquals("V4.1035", (String) p.getResponseData());

		lookup.setV1Version((String) p.getResponseData());
		assertTrue(V1VersionSettingLookup.isGen2());
		assertTrue(V1VersionSettingLookup.isJunkAlertReported());
	}

	@Test
	public void gen2VersionResponseLeBareFramedDrivesGen2Detection()
	{
		learnV1Type();

		// the LE dongle delivers bare ESP frames; the bridge wraps them in PACK framing
		// (TestFrames.escape(packFrame(...)) is byte-identical to V1connectionLE.wrapInPackFraming,
		// proven by V1connectionLEFramingTest.wrapMatchesIndependentPackFraming)
		byte[] bare = TestFrames.bareFrame(APP_NIBBLE, V1_NIBBLE, 0x02, versionPayload("V4.1035"), true);
		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.toList(TestFrames.escape(TestFrames.packFrame(bare))));

		assertNotNull(p);
		assertTrue(p instanceof ResponseVersion);
		assertEquals("V4.1035", (String) p.getResponseData());

		lookup.setV1Version((String) p.getResponseData());
		assertTrue(V1VersionSettingLookup.isGen2());
	}

	@Test
	public void gen1VersionResponseStaysGen1()
	{
		learnV1Type();

		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.packStream(APP_NIBBLE, V1_NIBBLE, 0x02, versionPayload("V3.8952"), true));

		assertNotNull(p);
		lookup.setV1Version((String) p.getResponseData());
		assertFalse(V1VersionSettingLookup.isGen2());
		assertFalse(V1VersionSettingLookup.isJunkAlertReported());
	}

	@Test
	public void defaultSweepDefinitionResponseParsesSppFramed()
	{
		learnV1Type();

		// same payload layout as respSweepDefinition: index, upper MSB/LSB, lower MSB/LSB
		// (values from the stock demo data: 34106 / 33900)
		byte[] payload = {(byte) 0x80, (byte) 0x85, 0x3A, (byte) 0x84, 0x6C};
		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.packStream(APP_NIBBLE, V1_NIBBLE, 0x25, payload, true));

		assertNotNull(p);
		assertEquals(PacketId.respDefaultSweepDefinition, p.getPacketIdentifier());
		assertTrue(p instanceof ResponseSweepDefinitions);

		SweepDefinition def = (SweepDefinition) p.getResponseData();
		assertEquals(0, def.getIndex());
		assertEquals(34106, def.getUpperFrequencyEdge().intValue());
		assertEquals(33900, def.getLowerFrequencyEdge().intValue());
	}

	@Test
	public void defaultSweepDefinitionResponseParsesLeBareFramed()
	{
		learnV1Type();

		byte[] payload = {(byte) 0x81, (byte) 0x86, (byte) 0xAB, (byte) 0x85, (byte) 0x84};
		byte[] bare = TestFrames.bareFrame(APP_NIBBLE, V1_NIBBLE, 0x25, payload, true);
		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.toList(TestFrames.escape(TestFrames.packFrame(bare))));

		assertNotNull(p);
		assertEquals(PacketId.respDefaultSweepDefinition, p.getPacketIdentifier());
		SweepDefinition def = (SweepDefinition) p.getResponseData();
		assertEquals(1, def.getIndex());
		assertEquals(34475, def.getUpperFrequencyEdge().intValue());
		assertEquals(34180, def.getLowerFrequencyEdge().intValue());
	}

	@Test
	public void gen2VolumeResponseParsesLeBareFramed()
	{
		learnV1Type();

		byte[] bare = TestFrames.bareFrame(APP_NIBBLE, V1_NIBBLE, 0x38, new byte[] {6, 3, 0}, true);
		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.toList(TestFrames.escape(TestFrames.packFrame(bare))));

		assertNotNull(p);
		assertEquals(PacketId.respCurrentVolume, p.getPacketIdentifier());
		assertTrue(p instanceof ResponseVolume);
		byte[] data = (byte[]) p.getResponseData();
		assertEquals(6, data[0]);
		assertEquals(3, data[1]);
	}

	@Test
	public void allUnassignedOriginNibblesLeaveV1TypeUnknown()
	{
		// A Gen2 identifies as 0x0A; any unassigned origin nibble (e.g. corrupted by a
		// noisy link) must map to UNKNOWN instead of crashing the decoder.
		for (int nibble = 0x0B; nibble <= 0x0F; nibble++)
		{
			ESPPacket.resetDecoderState();
			ESPPacket p = ESPPacket.makeFromBuffer(
					TestFrames.packStream(GB_NIBBLE, nibble, 0x31, DISPLAY_PAYLOAD, false));
			assertNotNull("nibble " + nibble, p);
			assertEquals("nibble " + nibble, Devices.UNKNOWN, ESPPacket.getLastKnownV1Type());
		}
	}

	@Test
	public void presetV1TypeLetsResponsesParseBeforeAnyDisplayData()
	{
		// Demo mode presets the decoder to a V1 with checksum; without the preset a
		// version response ahead of the first infDisplayData is discarded.
		ESPPacket dropped = ESPPacket.makeFromBuffer(
				TestFrames.packStream(APP_NIBBLE, V1_NIBBLE, 0x02, versionPayload("V4.1035"), true));
		assertNull("responses must be gated while the V1 type is unknown", dropped);

		ESPPacket.presetV1Type(Devices.VALENTINE1_WITH_CHECKSUM);
		ESPPacket parsed = ESPPacket.makeFromBuffer(
				TestFrames.packStream(APP_NIBBLE, V1_NIBBLE, 0x02, versionPayload("V4.1035"), true));
		assertNotNull(parsed);
		assertEquals("V4.1035", (String) parsed.getResponseData());
	}

	@Test
	public void turnOnMainDisplayIsNotConfusedWithMuteOn()
	{
		// Regression: a missing break in PacketFactory used to make reqTurnOnMainDisplay
		// (0x33) parse as a mute request (0x34).
		learnV1Type();

		ESPPacket on = ESPPacket.makeFromBuffer(
				TestFrames.packStream(V1_NIBBLE, APP_NIBBLE, 0x33, new byte[0], true));
		ESPPacket mute = ESPPacket.makeFromBuffer(
				TestFrames.packStream(V1_NIBBLE, APP_NIBBLE, 0x34, new byte[0], true));

		assertNotNull(on);
		assertNotNull(mute);
		assertEquals(PacketId.reqTurnOnMainDisplay, on.getPacketIdentifier());
		assertEquals(PacketId.reqMuteOn, mute.getPacketIdentifier());
		assertFalse("factory must build distinct packet types",
				on.getClass().equals(mute.getClass()));
	}
}
