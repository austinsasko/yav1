package com.valentine.esp.packets.response;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.data.SweepWriteResult;
import com.valentine.esp.packets.ESPPacket;

public class ResponseSweepWriteResult extends ESPPacket 
{
	public ResponseSweepWriteResult(Devices _destination)
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
	 * Returns SweepWriteResult from the V1 using this packets payload data.
	 * @return	SweepWriteResult from the V1.
	 */
	public Object getResponseData() 
	{
		SweepWriteResult rc = new SweepWriteResult();
		
		rc.buildFromByte(payloadData[0]);
		
		return rc;
	}
}
