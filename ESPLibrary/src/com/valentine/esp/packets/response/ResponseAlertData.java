package com.valentine.esp.packets.response;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.data.AlertData;
import com.valentine.esp.packets.ESPPacket;

public class ResponseAlertData extends ESPPacket 
{
	public ResponseAlertData(Devices _destination)
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
	 * Returns the AlertData created using this packets payload data.
	 * @returns AlertData that was sent with this packet.
	 */
	public Object getResponseData() 
	{
		AlertData rc = new AlertData();
		rc.buildFromData(payloadData);
		
		return rc;
	}
}
