package com.valentine.esp.packets.request;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.data.SweepDefinition;
import com.valentine.esp.packets.ESPPacket;

public class RequestWriteSweepDefinition extends ESPPacket 
{
	SweepDefinition m_sweep;
	
	public RequestWriteSweepDefinition(SweepDefinition _sweep, Devices _destination)
	{
		m_destination = _destination.toByteValue();
		m_valentineType = _destination;
		m_sweep = _sweep;
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
		packetIdentifier = PacketId.reqWriteSweepDefinition.toByteValue();
		
		if (m_sweep != null)
		{
			payloadData = m_sweep.buildBytes();
		}
		else
		{
			payloadData =  new byte[5];
		}
		
		if ((m_valentineType == Devices.VALENTINE1_LEGACY) || (m_valentineType == Devices.VALENTINE1_WITHOUT_CHECKSUM))
		{
			payloadLength = 5;
			checkSum = 0;
		}
		else
		{
			payloadLength = 6;
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
