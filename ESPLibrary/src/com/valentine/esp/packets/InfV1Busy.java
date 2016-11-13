package com.valentine.esp.packets;

import java.util.ArrayList;
import com.valentine.esp.constants.Devices;

public class InfV1Busy extends ESPPacket 
{
	public InfV1Busy(Devices _destination)
	{
		m_destination = _destination.toByteValue();
		m_timeStamp = System.currentTimeMillis();
	}

	@Override
	/**
	 *  Handles setting constant values.
	 *  See super.buildPacket() for implementation details.
	 */
	protected void buildPacket()
	{

	}

	@Override
	/**
	 *  Gets the data embedded into the packet.  Should not need to call directly, data returned directly from the Valentine Client.
	 * @return An object representing the data in the packet.  Cast to the correct type for the packet. 
	 */
	public Object getResponseData() 
	{
		ArrayList<Integer> rc = new ArrayList<Integer>();
		
		for (int i = 0; i < payloadLength -1; i++)
		{
			rc.add((int) payloadData[i]);
		}
		
		return rc;
	}
}
