package com.valentine.esp.packets.response;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.data.SweepDefinition;
import com.valentine.esp.packets.ESPPacket;

public class ResponseSweepDefinitions extends ESPPacket 
{
	public ResponseSweepDefinitions(Devices _destination)
	{
		m_destination = _destination.toByteValue();
		m_timeStamp = System.currentTimeMillis();
		buildPacket();
	}
	
	@Override
	/**
	 * Returns a SweepDefinition using this packets payload data.
	 * @return SweepDefinition.
	 */
	public Object getResponseData()
	{
		SweepDefinition rc = new SweepDefinition();
		
		rc.buildFromBytes(payloadData);
		
		return rc;
	}

	@Override
	/**
	 * See parent for default implementation.
	 */
	protected void buildPacket() 
	{
		
	}
}
