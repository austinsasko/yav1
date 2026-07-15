package com.valentine.esp.constants;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DevicesTest
{
	@Test
	public void mapsAssignedDeviceIds()
	{
		assertEquals(Devices.CONCEALED_DISPAY, Devices.fromByteValue((byte) 0x00));
		assertEquals(Devices.SAVVY, Devices.fromByteValue((byte) 0x02));
		assertEquals(Devices.V1CONNECT, Devices.fromByteValue((byte) 0x06));
		assertEquals(Devices.GENERAL_BROADCAST, Devices.fromByteValue((byte) 0x08));
		assertEquals(Devices.VALENTINE1_WITHOUT_CHECKSUM, Devices.fromByteValue((byte) 0x09));
		// A V1 Gen2 identifies with the same id as a V1 with checksum
		assertEquals(Devices.VALENTINE1_WITH_CHECKSUM, Devices.fromByteValue((byte) 0x0A));
	}

	@Test
	public void mapsSpecialValues()
	{
		assertEquals(Devices.VALENTINE1_LEGACY, Devices.fromByteValue((byte) 0x98));
		assertEquals(Devices.UNKNOWN, Devices.fromByteValue((byte) 0x99));
	}

	@Test
	public void unassignedNibblesMapToUnknownInsteadOfCrashing()
	{
		// 0x0B - 0x0F are not assigned; historically 0x0D+ crashed with
		// ArrayIndexOutOfBoundsException and 0x0B/0x0C mapped to bogus devices.
		for (byte b = 0x0B; b <= 0x0F; b++)
		{
			assertEquals(Devices.UNKNOWN, Devices.fromByteValue(b));
		}
		assertEquals(Devices.UNKNOWN, Devices.fromByteValue((byte) 0x42));
		assertEquals(Devices.UNKNOWN, Devices.fromByteValue((byte) 0xF0));
	}

	@Test
	public void lookupMasksHighNibble()
	{
		// DevicesLookup receives raw destination / origin bytes like 0xEA
		assertEquals(Devices.VALENTINE1_WITH_CHECKSUM, DevicesLookup.getConstant((byte) 0xEA));
		assertEquals(Devices.V1CONNECT, DevicesLookup.getConstant((byte) 0xD6));
		assertEquals(Devices.GENERAL_BROADCAST, DevicesLookup.getConstant((byte) 0xD8));
	}

	@Test
	public void boundaryBetweenAssignedAndUnassignedIdsIsExact()
	{
		// 0x0A (V1 with checksum / V1 Gen2) is the last assigned id; 0x0B is not
		assertEquals(Devices.VALENTINE1_WITH_CHECKSUM, Devices.fromByteValue((byte) 0x0A));
		assertEquals(Devices.UNKNOWN, Devices.fromByteValue((byte) 0x0B));

		// all remaining assigned ids for completeness
		assertEquals(Devices.REMOTE_AUDIO, Devices.fromByteValue((byte) 0x01));
		assertEquals(Devices.THIRD_PARTY_1, Devices.fromByteValue((byte) 0x03));
		assertEquals(Devices.THIRD_PARTY_2, Devices.fromByteValue((byte) 0x04));
		assertEquals(Devices.THIRD_PARTY_3, Devices.fromByteValue((byte) 0x05));
		assertEquals(Devices.RESERVED, Devices.fromByteValue((byte) 0x07));
	}

	@Test
	public void negativeBytesMapToUnknownExceptTheSpecialValues()
	{
		// bytes 0x80 - 0xFF are negative in Java; only 0x98 / 0x99 have meanings
		for (int b = 0x80; b <= 0xFF; b++)
		{
			Devices d = Devices.fromByteValue((byte) b);
			if (b == 0x98)
			{
				assertEquals(Devices.VALENTINE1_LEGACY, d);
			}
			else
			{
				assertEquals("0x" + Integer.toHexString(b), Devices.UNKNOWN, d);
			}
		}
	}
}
