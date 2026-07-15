package com.valentine.esp.bluetooth;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.packets.ESPPacket;
import com.valentine.esp.packets.TestFrames;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Tests for the BTLE <-> SPP framing conversion done by V1connectionLE:
 * BLE notifications carry bare ESP frames (0xAA .. 0xAB) that the bridge wraps
 * into PACK framing for DataReaderThread, and outgoing PACK frames are stripped
 * back to bare frames for the GATT write.
 */
public class V1connectionLEFramingTest
{
	private static final int V1_NIBBLE  = 0x0A;
	private static final int GB_NIBBLE  = 0x08;

	private static final byte[] DISPLAY_PAYLOAD =
			{0x77, 0x00, 0x00, 0x22, 0x00, 0x41, 0x00, 0x00};

	@Before
	public void setUp()
	{
		ESPPacket.resetDecoderState();
	}

	@Test
	public void wrapMatchesIndependentPackFraming()
	{
		byte[] bare = TestFrames.bareFrame(GB_NIBBLE, V1_NIBBLE, 0x31, DISPLAY_PAYLOAD, true);

		byte[] wrapped  = V1connectionLE.wrapInPackFraming(bare);
		byte[] expected = TestFrames.escape(TestFrames.packFrame(bare));

		assertArrayEquals(expected, wrapped);
	}

	@Test
	public void wrapEscapesReservedBytesInFrame()
	{
		// payload deliberately containing 0x7F and 0x7D
		byte[] payload = {0x7F, 0x7D, 0x00, 0x22, 0x00, 0x41, 0x00, 0x00};
		byte[] bare = TestFrames.bareFrame(GB_NIBBLE, V1_NIBBLE, 0x31, payload, true);

		byte[] wrapped  = V1connectionLE.wrapInPackFraming(bare);
		byte[] expected = TestFrames.escape(TestFrames.packFrame(bare));

		assertArrayEquals(expected, wrapped);

		// no unescaped delimiter / escape byte between the two frame delimiters
		for (int i = 1; i < wrapped.length - 1; i++)
		{
			if (wrapped[i] == 0x7D)
			{
				// escape sequences are exactly 0x7D 0x5F or 0x7D 0x5D
				byte next = wrapped[i + 1];
				assertEquals(true, next == 0x5F || next == 0x5D);
				i++;
			}
			else
			{
				org.junit.Assert.assertTrue("unescaped 0x7F inside frame", wrapped[i] != 0x7F);
			}
		}
	}

	@Test
	public void wrappedFrameParsesInEspPacketDecoder()
	{
		byte[] bare = TestFrames.bareFrame(GB_NIBBLE, V1_NIBBLE, 0x31, DISPLAY_PAYLOAD, true);
		byte[] wrapped = V1connectionLE.wrapInPackFraming(bare);

		ESPPacket p = ESPPacket.makeFromBuffer(TestFrames.toList(wrapped));

		assertNotNull(p);
		assertEquals(PacketId.infDisplayData, p.getPacketIdentifier());
		assertEquals(Devices.VALENTINE1_WITH_CHECKSUM, p.getOrigin());
	}

	@Test
	public void stripInvertsWrap()
	{
		byte[] bare = TestFrames.bareFrame(GB_NIBBLE, V1_NIBBLE, 0x31,
				new byte[] {0x7F, 0x7D, 0x00, 0x22, 0x00, 0x41, 0x7F, 0x7D}, true);

		byte[] roundTrip = V1connectionLE.stripPackFraming(V1connectionLE.wrapInPackFraming(bare));

		assertArrayEquals(bare, roundTrip);
	}

	@Test
	public void stripHandlesWriterThreadOutput()
	{
		// what DataWriterThread actually produces for a mute request to a Gen1/Gen2
		ESPPacket.resetDecoderState();
		com.valentine.esp.packets.request.RequestMuteOn req =
				new com.valentine.esp.packets.request.RequestMuteOn(Devices.VALENTINE1_WITH_CHECKSUM);

		byte[] wire = com.valentine.esp.threads.DataWriterThread.escape(ESPPacket.makeByteStream(req));
		byte[] bare = V1connectionLE.stripPackFraming(wire);

		assertNotNull(bare);
		assertEquals((byte) 0xAA, bare[0]);
		assertEquals((byte) 0xAB, bare[bare.length - 1]);
		assertEquals(PacketId.reqMuteOn.toByteValue(), bare[3]);
		// length byte on the wire equals the bare frame length (7, needs no escaping)
		assertEquals(bare.length, wire[1] & 0xFF);
	}

	@Test
	public void stripRejectsMalformedBuffers()
	{
		assertNull(V1connectionLE.stripPackFraming(null));
		assertNull(V1connectionLE.stripPackFraming(new byte[] {0x7F, 0x7F}));
		assertNull(V1connectionLE.stripPackFraming(new byte[] {0x00, 0x01, 0x02, 0x03}));
		// missing trailing delimiter
		assertNull(V1connectionLE.stripPackFraming(new byte[] {0x7F, 0x05, (byte) 0xAA, (byte) 0xAB, 0x00}));
	}

	@Test
	public void notificationReassemblyFormatSurvivesChunking()
	{
		// Simulate a frame arriving over BLE in 20 byte chunks: wrap and parse the
		// concatenation of two frames, mirroring what handleNotification produces.
		byte[] frame1 = TestFrames.bareFrame(GB_NIBBLE, V1_NIBBLE, 0x31, DISPLAY_PAYLOAD, true);
		byte[] frame2 = TestFrames.bareFrame(0x06, V1_NIBBLE, 0x38, new byte[] {4, 4, 0}, true);

		ArrayList<Byte> stream = TestFrames.toList(V1connectionLE.wrapInPackFraming(frame1));
		stream.addAll(TestFrames.toList(V1connectionLE.wrapInPackFraming(frame2)));

		ESPPacket p1 = ESPPacket.makeFromBuffer(stream);
		assertNotNull(p1);
		assertEquals(PacketId.infDisplayData, p1.getPacketIdentifier());

		ESPPacket p2 = ESPPacket.makeFromBuffer(stream);
		assertNotNull(p2);
		assertEquals(PacketId.respCurrentVolume, p2.getPacketIdentifier());
	}
}
