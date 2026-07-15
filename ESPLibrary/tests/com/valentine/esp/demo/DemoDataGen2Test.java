package com.valentine.esp.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.data.AlertData;
import com.valentine.esp.packets.ESPPacket;
import com.valentine.esp.utilities.V1VersionSettingLookup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Demo mode data through the real pipeline: hex lines exactly as they appear in the
 * assets/demo files, parsed by ESPPacket.makeFromBuffer and handled by DemoData, must
 * drive the V1 Gen2 recognition just like a live connection.
 */
public class DemoDataGen2Test
{
	/** V1 version response line from assets/demo/demo_gen2.dat (V4.1035). */
	private static final String GEN2_VERSION_LINE =
			"7F 0E AA D6 EA 02 08 56 34 2E 31 30 33 35 F5 AB A3 7F";

	/** V1 version response line from assets/demo/demo.dat (V3.8930). */
	private static final String GEN1_VERSION_LINE =
			"7F 0E AA D6 EA 02 08 56 33 2E 38 39 33 30 FF AB B7 7F";

	/** K band alert from demo_gen2.dat with the Gen2 junk/BSM bit set (aux 0xC1). */
	private static final String GEN2_JUNK_K_ALERT_LINE =
			"7F 0E AA D6 EA 43 08 22 5E 6B A8 3F 24 C1 6C AB 91 7F";

	private DemoData demoData;

	@Before
	public void setUp()
	{
		// what ValentineESP.startDemo does before playing a file
		ESPPacket.resetDecoderState();
		ESPPacket.presetV1Type(Devices.VALENTINE1_WITH_CHECKSUM);
		V1VersionSettingLookup.resetV1Version();
		demoData = new DemoData();
	}

	@After
	public void tearDown()
	{
		ESPPacket.resetDecoderState();
		V1VersionSettingLookup.resetV1Version();
	}

	/** Convert a space separated hex line (demo file format) into a packet. */
	private static ESPPacket parseLine(String line)
	{
		ArrayList<Byte> buffer = new ArrayList<Byte>();
		for (String tok : line.split(" "))
		{
			buffer.add((byte) Integer.parseInt(tok, 16));
		}
		return ESPPacket.makeFromBuffer(buffer);
	}

	@Test
	public void gen2DemoVersionLineTurnsOnGen2Detection()
	{
		ESPPacket p = parseLine(GEN2_VERSION_LINE);
		assertNotNull("the version line at the top of the demo file must parse", p);
		assertEquals(PacketId.respVersion, p.getPacketIdentifier());
		assertEquals(Devices.VALENTINE1_WITH_CHECKSUM, p.getOrigin());

		assertFalse(V1VersionSettingLookup.isGen2());
		demoData.handleDemoPacket(p);

		assertTrue("playing the Gen2 demo version response must flip Gen2 detection",
				V1VersionSettingLookup.isGen2());
		assertTrue(V1VersionSettingLookup.isJunkAlertReported());
		assertEquals(4.1035, V1VersionSettingLookup.getV1Version(), 1e-9);
	}

	@Test
	public void gen1DemoVersionLineKeepsGen2Off()
	{
		ESPPacket p = parseLine(GEN1_VERSION_LINE);
		assertNotNull(p);

		demoData.handleDemoPacket(p);

		assertFalse(V1VersionSettingLookup.isGen2());
		assertEquals(3.8930, V1VersionSettingLookup.getV1Version(), 1e-9);
	}

	@Test
	public void accessoryVersionResponsesDoNotAffectGen2Detection()
	{
		// concealed display version line from the demo files (origin 0xE0)
		ESPPacket p = parseLine("7F 0E AA D6 E0 02 08 43 32 2E 31 33 30 30 D1 AB 5B 7F");
		assertNotNull(p);
		assertEquals(Devices.CONCEALED_DISPAY, p.getOrigin());

		demoData.handleDemoPacket(p);

		assertFalse(V1VersionSettingLookup.isGen2());
	}

	@Test
	public void gen2JunkAlertLineCarriesTheJunkBitThroughTheRealPipeline()
	{
		ESPPacket p = parseLine(GEN2_JUNK_K_ALERT_LINE);
		assertNotNull(p);
		assertEquals(PacketId.respAlertData, p.getPacketIdentifier());

		AlertData alert = (AlertData) p.getResponseData();
		assertEquals(24171, alert.getFrequency());
		assertTrue("K band alert from the Gen2 demo must be flagged junk", alert.isJunkAlert());
		assertTrue(alert.getPriorityAlert());

		// and DemoData must accept it without throwing (it goes onto the input queue)
		demoData.handleDemoPacket(p);
	}

	@Test
	public void demoPreambleParsesOnlyBecauseOfTheV1TypePreset()
	{
		// without the preset (fresh app start, no V1 seen) the version response at the
		// top of the demo file is discarded by the unknown-V1-type gating
		ESPPacket.resetDecoderState();
		assertEquals(Devices.UNKNOWN, ESPPacket.getLastKnownV1Type());
		ESPPacket dropped = parseLine(GEN2_VERSION_LINE);
		assertEquals(null, dropped);

		// with the preset (what startDemo now applies) the same line parses
		ESPPacket.presetV1Type(Devices.VALENTINE1_WITH_CHECKSUM);
		ESPPacket parsed = parseLine(GEN2_VERSION_LINE);
		assertNotNull(parsed);
	}
}
