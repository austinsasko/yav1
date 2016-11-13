package com.valentine.esp.packets.request;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.packets.ESPPacket;

public class RequestChangeMode extends ESPPacket 
{
	byte m_mode;
	
	public static final byte ALL_BOGIES = 1;
	public static final byte LOGIC_MODE = 2;
	public static final byte ADVANCED_LOGIC_MODE = 3;
	
	public RequestChangeMode(byte _mode, Devices _destination)
	{
		m_mode = _mode;
		m_destination = _destination.toByteValue();
		m_valentineType = _destination;
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
		packetIdentifier = PacketId.reqChangeMode.toByteValue();
		
		payloadData = new byte[1];
		payloadData[0] = m_mode;
		
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
