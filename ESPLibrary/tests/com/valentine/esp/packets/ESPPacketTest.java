package com.valentine.esp.packets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.packets.request.RequestVersion;
import com.valentine.esp.threads.DataWriterThread;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * PACK frame parse / serialize tests for ESPPacket, including the 0x7F / 0x7D
 * escaping edge cases.
 */
public class ESPPacketTest
{
	private static final int V1_NIBBLE  = 0x0A; // V1 with checksum
	private static final int GB_NIBBLE  = 0x08; // general broadcast
	private static final int APP_NIBBLE = 0x06; // V1connection

	private static final byte[] DISPLAY_PAYLOAD =
			{0x77, 0x00, 0x00, 0x22, 0x00, 0x41, 0x00, 0x00};

	@Before
	public void setUp()
	{
		ESPPacket.resetDecoderState();
	}

	/** Feed an infDisplayData frame so the decoder learns the V1 type. */
	private void learnV1Type()
	{
		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.packStream(GB_NIBBLE, V1_NIBBLE, 0x31, DISPLAY_PAYLOAD, true));
		assertNotNull(p);
		assertEquals(Devices.VALENTINE1_WITH_CHECKSUM, ESPPacket.getLastKnownV1Type());
	}

	@Test
	public void parsesInfDisplayDataAndLearnsV1Type()
	{
		ArrayList<Byte> stream = TestFrames.packStream(GB_NIBBLE, V1_NIBBLE, 0x31, DISPLAY_PAYLOAD, true);
		ESPPacket p = ESPPacket.makeFromBuffer(stream);

		assertNotNull(p);
		assertEquals(PacketId.infDisplayData, p.getPacketIdentifier());
		assertEquals(Devices.GENERAL_BROADCAST, p.getDestination());
		assertEquals(Devices.VALENTINE1_WITH_CHECKSUM, p.getOrigin());
		assertEquals(Devices.VALENTINE1_WITH_CHECKSUM, ESPPacket.getLastKnownV1Type());
		// payload includes the ESP checksum byte for a V1 with checksum
		assertEquals(DISPLAY_PAYLOAD.length + 1, p.getPayloadLength());
		// buffer fully consumed
		assertEquals(0, stream.size());
	}

	@Test
	public void parsesPayloadContaining7Fand7DWhenEscaped()
	{
		learnV1Type();

		// aux bytes carry the two reserved values that must be escaped on the wire
		byte[] payload = {0x77, 0x00, 0x00, 0x22, 0x00, 0x41, 0x7F, 0x7D};
		ArrayList<Byte> stream = TestFrames.packStream(GB_NIBBLE, V1_NIBBLE, 0x31, payload, true);

		// the escaped stream must be longer than the unescaped frame
		assertTrue(stream.size() > payload.length + 11);

		ESPPacket p = ESPPacket.makeFromBuffer(stream);
		assertNotNull(p);

		byte[] parsed = p.getPayload();
		assertEquals(0x7F, parsed[6] & 0xFF);
		assertEquals(0x7D, parsed[7] & 0xFF);
	}

	@Test
	public void serializeParseRoundTripWithEscaping()
	{
		learnV1Type();

		// a version request to the V1 carries an ESP checksum byte
		RequestVersion req = new RequestVersion(Devices.VALENTINE1_WITH_CHECKSUM,
				Devices.VALENTINE1_WITH_CHECKSUM);

		byte[] wire = DataWriterThread.escape(ESPPacket.makeByteStream(req));

		ESPPacket parsed = ESPPacket.makeFromBuffer(TestFrames.toList(wire));

		// Note: the parser only returns packets, it does not filter by destination
		assertNotNull(parsed);
		assertEquals(PacketId.reqVersion, parsed.getPacketIdentifier());
		assertEquals(Devices.VALENTINE1_WITH_CHECKSUM, parsed.getDestination());
		assertEquals(Devices.V1CONNECT, parsed.getOrigin());
	}

	@Test
	public void escapeCoversLengthAndChecksumBytes()
	{
		// craft a frame whose PACK length byte itself is 0x7D (125):
		// bare frame length = 6 + payload + checksum -> payload of 118 bytes
		learnV1Type();
		byte[] payload = new byte[118];
		for (int i = 0; i < payload.length; i++)
		{
			payload[i] = (byte) (i & 0x3F);
		}
		ArrayList<Byte> stream = TestFrames.packStream(GB_NIBBLE, V1_NIBBLE, 0x31, payload, true);

		ESPPacket p = ESPPacket.makeFromBuffer(stream);
		assertNotNull(p);
		assertEquals(payload.length + 1, p.getPayloadLength());
		byte[] parsed = p.getPayload();
		for (int i = 0; i < payload.length; i++)
		{
			assertEquals(payload[i], parsed[i]);
		}
	}

	@Test
	public void incompleteFrameReturnsNullAndKeepsBuffer()
	{
		learnV1Type();

		ArrayList<Byte> stream = TestFrames.packStream(GB_NIBBLE, V1_NIBBLE, 0x31, DISPLAY_PAYLOAD, true);
		// remove the trailing delimiter -> not a complete PACK frame yet
		stream.remove(stream.size() - 1);
		int len = stream.size();

		assertNull(ESPPacket.makeFromBuffer(stream));
		// nothing consumed while waiting for the rest of the frame
		assertEquals(len, stream.size());
	}

	@Test
	public void badEspChecksumIsRejected()
	{
		learnV1Type();

		byte[] bare = TestFrames.bareFrame(GB_NIBBLE, V1_NIBBLE, 0x31, DISPLAY_PAYLOAD, true);
		// corrupt the ESP checksum (second to last byte) and refresh the wrapper
		bare[bare.length - 2] ^= 0x55;
		ArrayList<Byte> stream = TestFrames.toList(TestFrames.escape(TestFrames.packFrame(bare)));

		assertNull(ESPPacket.makeFromBuffer(stream));
	}

	@Test
	public void unknownPacketIdParsesAsUnknownPacket()
	{
		learnV1Type();

		// 0x3D (respAllVolume) exists; use a really unassigned id like 0x5E
		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.packStream(APP_NIBBLE, V1_NIBBLE, 0x5E, new byte[] {0x01}, true));

		assertNotNull(p);
		assertEquals(PacketId.unknownPacketType, p.getPacketIdentifier());
		assertTrue(p instanceof UnknownPacket);
	}

	@Test
	public void gen2VolumeResponseParses()
	{
		learnV1Type();

		// respCurrentVolume (0x38, payload: main, muted, flags)
		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.packStream(APP_NIBBLE, V1_NIBBLE, 0x38, new byte[] {6, 3, 0}, true));

		assertNotNull(p);
		assertEquals(PacketId.respCurrentVolume, p.getPacketIdentifier());
		byte[] data = (byte[]) p.getResponseData();
		assertArrayEquals(new byte[] {6, 3, 0}, data);
	}

	@Test
	public void corruptedOriginNibbleDoesNotCrash()
	{
		// An infDisplayData with a bogus origin nibble (0x0F) must not throw and
		// must leave the decoder in the UNKNOWN state (payload has no checksum here).
		ESPPacket p = ESPPacket.makeFromBuffer(
				TestFrames.packStream(GB_NIBBLE, 0x0F, 0x31, DISPLAY_PAYLOAD, false));

		assertNotNull(p);
		assertEquals(Devices.UNKNOWN, ESPPacket.getLastKnownV1Type());
	}

	@Test
	public void isSamePacketDetectsPayloadDifference()
	{
		learnV1Type();

		byte[] payloadA = {0x77, 0x00, 0x00, 0x22, 0x00, 0x41, 0x00, 0x00};
		byte[] payloadB = {0x77, 0x00, 0x00, 0x22, 0x00, 0x41, 0x00, 0x01};

		ESPPacket a = ESPPacket.makeFromBuffer(TestFrames.packStream(GB_NIBBLE, V1_NIBBLE, 0x31, payloadA, true));
		ESPPacket b = ESPPacket.makeFromBuffer(TestFrames.packStream(GB_NIBBLE, V1_NIBBLE, 0x31, payloadB, true));

		assertNotNull(a);
		assertNotNull(b);
		assertTrue(a.isSamePacket(a));
		assertFalse("packets with different payloads must not be considered the same",
				a.isSamePacket(b));
	}

	@Test
	public void twoFramesBackToBackParseSequentially()
	{
		learnV1Type();

		ArrayList<Byte> stream = TestFrames.packStream(GB_NIBBLE, V1_NIBBLE, 0x31, DISPLAY_PAYLOAD, true);
		stream.addAll(TestFrames.packStream(APP_NIBBLE, V1_NIBBLE, 0x38, new byte[] {1, 2, 3}, true));

		ESPPacket first = ESPPacket.makeFromBuffer(stream);
		assertNotNull(first);
		assertEquals(PacketId.infDisplayData, first.getPacketIdentifier());

		ESPPacket second = ESPPacket.makeFromBuffer(stream);
		assertNotNull(second);
		assertEquals(PacketId.respCurrentVolume, second.getPacketIdentifier());
		assertEquals(0, stream.size());
	}
}
