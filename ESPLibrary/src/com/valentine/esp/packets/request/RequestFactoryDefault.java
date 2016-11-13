package com.valentine.esp.packets.request;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.packets.ESPPacket;

public class RequestFactoryDefault extends ESPPacket 
{
	public RequestFactoryDefault(Devices _valentineType, Devices _destination)
	{
		m_destination = _destination.toByteValue();
		m_valentineType = _valentineType;
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
		packetIdentifier = PacketId.reqFactoryDefault.toByteValue();

		if ((m_valentineType == Devices.VALENTINE1_LEGACY) || (m_valentineType == Devices.VALENTINE1_WITHOUT_CHECKSUM))
		{
			payloadLength = 0;
			checkSum = 0;
		}
		else
		{
			payloadLength = 1;
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
