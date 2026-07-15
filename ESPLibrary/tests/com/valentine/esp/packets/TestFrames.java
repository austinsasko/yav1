package com.valentine.esp.packets;

import java.util.ArrayList;

/**
 * Test helper that builds raw ESP and PACK frames byte by byte, independently of
 * the production serializer, so the parser and serializer verify each other.
 */
public final class TestFrames
{
	public static final byte SOF   = (byte) 0xAA;
	public static final byte EOF   = (byte) 0xAB;
	public static final byte DELIM = 0x7F;
	public static final byte ESC   = 0x7D;

	private TestFrames()
	{
	}

	/**
	 * Build a bare ESP frame: SOF dest orig id payloadLen payload... [espChk] EOF.
	 *
	 * @param destNibble  low nibble of the destination id (0x0 - 0xF)
	 * @param origNibble  low nibble of the originator id (0x0 - 0xF)
	 * @param packetId    the ESP packet id byte
	 * @param payload     payload bytes WITHOUT the ESP checksum
	 * @param checksum    true to append the ESP checksum (V1 with checksum framing)
	 */
	public static byte[] bareFrame(int destNibble, int origNibble, int packetId, byte[] payload, boolean checksum)
	{
		int payloadLen = payload.length + (checksum ? 1 : 0);
		byte[] f = new byte[6 + payloadLen];
		int i = 0;
		f[i++] = SOF;
		f[i++] = (byte) (0xD0 | destNibble);
		f[i++] = (byte) (0xE0 | origNibble);
		f[i++] = (byte) packetId;
		f[i++] = (byte) payloadLen;
		for (byte b : payload)
		{
			f[i++] = b;
		}
		if (checksum)
		{
			int chk = 0;
			for (int j = 0; j < i; j++)
			{
				chk += (f[j] & 0xFF);
			}
			f[i++] = (byte) chk;
		}
		f[i] = EOF;
		return f;
	}

	/**
	 * Wrap a bare ESP frame in (unescaped) PACK framing:
	 * 0x7F length frame... wrapperChecksum 0x7F.
	 */
	public static byte[] packFrame(byte[] bare)
	{
		byte[] f = new byte[bare.length + 4];
		f[0] = DELIM;
		f[1] = (byte) bare.length;
		int chk = bare.length;
		for (int j = 0; j < bare.length; j++)
		{
			f[2 + j] = bare[j];
			chk += (bare[j] & 0xFF);
		}
		f[2 + bare.length] = (byte) chk;
		f[3 + bare.length] = DELIM;
		return f;
	}

	/**
	 * Escape every 0x7F / 0x7D between the two frame delimiters, the way the SPP
	 * link requires (0x7F -> 0x7D 0x5F, 0x7D -> 0x7D 0x5D).
	 */
	public static byte[] escape(byte[] frame)
	{
		ArrayList<Byte> out = new ArrayList<Byte>();
		out.add(frame[0]);
		for (int i = 1; i < frame.length - 1; i++)
		{
			byte b = frame[i];
			if (b == DELIM)
			{
				out.add(ESC);
				out.add((byte) 0x5F);
			}
			else if (b == ESC)
			{
				out.add(ESC);
				out.add((byte) 0x5D);
			}
			else
			{
				out.add(b);
			}
		}
		out.add(frame[frame.length - 1]);
		return toArray(out);
	}

	public static ArrayList<Byte> toList(byte[] bytes)
	{
		ArrayList<Byte> list = new ArrayList<Byte>();
		for (byte b : bytes)
		{
			list.add(b);
		}
		return list;
	}

	public static byte[] toArray(ArrayList<Byte> list)
	{
		byte[] out = new byte[list.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = list.get(i);
		}
		return out;
	}

	/** A complete, escaped PACK byte stream for the given bare frame pieces. */
	public static ArrayList<Byte> packStream(int destNibble, int origNibble, int packetId, byte[] payload, boolean checksum)
	{
		return toList(escape(packFrame(bareFrame(destNibble, origNibble, packetId, payload, checksum))));
	}
}
