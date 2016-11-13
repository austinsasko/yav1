package com.valentine.esp.packets.response;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.packets.ESPPacket;

public class ResponseDataError extends ESPPacket 
{
	public ResponseDataError(Devices _destination)
	{
		m_destination = _destination.toByteValue();
		m_timeStamp = System.currentTimeMillis();
		buildPacket();
	}
	
	@Override
	/**
	 * Returns an empty int indicating the Data Error returned from the V1.
	 * @return The Data error returned from the V1.
	 */
	public Object getResponseData()
	{
		int rc;
		
		rc = payloadData[0];
		
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
