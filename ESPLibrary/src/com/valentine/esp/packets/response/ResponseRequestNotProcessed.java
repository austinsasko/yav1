package com.valentine.esp.packets.response;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.packets.ESPPacket;

public class ResponseRequestNotProcessed extends ESPPacket 
{
	public ResponseRequestNotProcessed(Devices _destination)
	{
		m_destination = _destination.toByteValue();
		m_timeStamp = System.currentTimeMillis();
		buildPacket();
	}
	
	@Override
	/**
	 * Returns an Integer indicating the response not processed code.
	 * @return A response not processed code.
	 */
	public Object getResponseData()
	{
		Integer rc;
		
		rc = (int)payloadData[0];
		
		return rc;
	}

	/**
	 * See parent for default implementation.
	 */
	protected void buildPacket() 
	{
		
	}
}
