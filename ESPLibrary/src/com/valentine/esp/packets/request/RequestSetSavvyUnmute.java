package com.valentine.esp.packets.request;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.packets.ESPPacket;

public class RequestSetSavvyUnmute extends ESPPacket 
{
	boolean m_mute;
	
	public RequestSetSavvyUnmute(Devices _valentineType, boolean _mute, Devices _destination)
	{
		m_destination = _destination.toByteValue();
		m_valentineType = _valentineType;
		m_mute = _mute;
		m_timeStamp = System.currentTimeMillis();
		buildPacket();
	}

	@Override
	/**
	 * Initializes data using logic thats specific to this packet.
	 * See parent for default implementation.
	 */
	protected void buildPacket()
	{
		super.buildPacket();
		packetIdentifier = PacketId.reqSetSavvyUnmuteEnable.toByteValue();
		
		payloadData = new byte[1];
		if (m_mute)
		{
			payloadData[0] = 1;
		}
		else
		{
			payloadData[0] = 0;
		}
		
		if ((m_valentineType == Devices.VALENTINE1_LEGACY) || (m_valentineType == Devices.VALENTINE1_WITHOUT_CHECKSUM))
		{
			payloadLength = 1;
			checkSum = 0;
		}
		else
		{
			payloadLength = 2;
			checkSum = makeMessageChecksum();
		}
		
		packetLength = (byte) (6 + payloadLength);
		packetChecksum = makePacketChecksum();
	}

	@Override
	/**
	 * See parent for default implementation.
	 */
	public Object getResponseData() 
	{
		return null;
	}
}
