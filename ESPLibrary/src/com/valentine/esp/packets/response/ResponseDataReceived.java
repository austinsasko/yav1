package com.valentine.esp.packets.response;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.packets.ESPPacket;

public class ResponseDataReceived extends ESPPacket 
{
	public ResponseDataReceived(Devices _destination)
	{
		m_destination = _destination.toByteValue();
		m_timeStamp = System.currentTimeMillis();
		buildPacket();
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
	 * Returns an empty string.
	 * @returns An empty string.
	 */
	public Object getResponseData() 
	{
		String rc = "";
		
		return rc;
	}
}
