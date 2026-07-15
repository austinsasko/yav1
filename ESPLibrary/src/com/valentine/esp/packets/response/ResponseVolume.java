package com.valentine.esp.packets.response;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.packets.ESPPacket;

/**
 * Volume settings response sent by a V1 Gen2 (respCurrentVolume 0x38 / respAllVolume 0x3D).
 *
 * For respCurrentVolume the payload contains the current main and muted volume bytes.
 * For respAllVolume the payload contains all of the volume bytes of the device.
 * Both are exposed as the raw payload since the app currently has no volume UI;
 * parsing them keeps the ESP stream in sync when a Gen2 is connected.
 */
public class ResponseVolume extends ESPPacket
{
	public ResponseVolume(Devices _destination)
	{
		m_destination = _destination.toByteValue();
		m_timeStamp = System.currentTimeMillis();
	}

	@Override
	/**
	 * See parent for default implementation.
	 */
	protected void buildPacket()
	{

	}

	@Override
	/**
	 * Returns the volume payload bytes, without the trailing ESP checksum when the
	 * V1 type uses checksums.
	 *
	 * @return a byte array with the volume data.
	 */
	public Object getResponseData()
	{
		int length = payloadData == null ? 0 : payloadData.length;

		if (m_valentineType == Devices.VALENTINE1_WITH_CHECKSUM && length > 0)
		{
			// Don't include the checksum byte
			length = length - 1;
		}

		byte[] rc = new byte[length];
		for (int i = 0; i < length; i++)
		{
			rc[i] = payloadData[i];
		}

		return rc;
	}
}
