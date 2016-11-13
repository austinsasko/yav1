package com.valentine.esp.packets.response;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.packets.ESPPacket;

public class ResponseUnsupported extends ESPPacket 
{
	public ResponseUnsupported(Devices _destination)
	{
		m_destination = _destination.toByteValue();
		m_timeStamp = System.currentTimeMillis();
		buildPacket();
	}
	
	@Override
	/**
	 * Returns an Integer indicating the 'Unsupported' error code
	 * @return 'Unsuppored' error code.
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
