package com.valentine.esp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlertDataTest
{
	private static byte[] alertBytes(int index, int count, int freqMhz, int front, int rear, int band, int aux)
	{
		byte[] b = new byte[7];
		b[0] = (byte) ((index << 4) | count);
		b[1] = (byte) ((freqMhz >> 8) & 0xFF);
		b[2] = (byte) (freqMhz & 0xFF);
		b[3] = (byte) front;
		b[4] = (byte) rear;
		b[5] = (byte) band;
		b[6] = (byte) aux;
		return b;
	}

	@Test
	public void parsesFrequencyAndIndex()
	{
		AlertData a = new AlertData();
		// 35500 MHz, Ka band front (band byte: 0x22 -> Ka + front arrow)
		a.buildFromData(alertBytes(1, 2, 35500, 0xA5, 0x00, 0x22, 0x00));

		assertEquals(35500, a.getFrequency());
		assertEquals(1, a.getAlertIndexAndCount().getIndex());
		assertEquals(2, a.getAlertIndexAndCount().getCount());
		assertFalse(a.getPriorityAlert());
		assertFalse(a.isJunkAlert());
	}

	@Test
	public void parsesPriorityBit()
	{
		AlertData a = new AlertData();
		a.buildFromData(alertBytes(1, 1, 24150, 0x90, 0x00, 0x24, 0x80));

		assertTrue(a.getPriorityAlert());
		assertFalse(a.isJunkAlert());
	}

	@Test
	public void parsesGen2JunkBit()
	{
		AlertData a = new AlertData();
		// aux 0x40: junk alert (V1 Gen2 V4.1032+), not priority
		a.buildFromData(alertBytes(1, 1, 24150, 0x90, 0x00, 0x24, 0x40));

		assertFalse(a.getPriorityAlert());
		assertTrue(a.isJunkAlert());

		// both bits together
		a.buildFromData(alertBytes(1, 1, 24150, 0x90, 0x00, 0x24, 0xC0));
		assertTrue(a.getPriorityAlert());
		assertTrue(a.isJunkAlert());
	}
}
