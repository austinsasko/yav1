package com.valentine.esp.threads;

import android.util.Log;
import com.valentine.esp.PacketQueue;
import com.valentine.esp.ValentineESP;
import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.ESPLibraryLogController;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.packets.ESPPacket;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;


public class DataWriterThread extends Thread 
{
	private static final String LOG_TAG = "ValentineESP/DataWriterThread";
	ArrayList<Byte> m_readByteBuffer;
	OutputStream    m_outStream;
	ValentineESP    m_valentineEsp;

	private boolean m_run;
	private static boolean m_protectLegacyMode = false;
	
	/**
	 * Set method to determine if the ESP library should protect legacy mode. If this value is true when the
	 * V1 is running in Legacy mode, the write thread will not send any commands that are not compatible with
	 * Legacy Mode. The only commands that are compatible with Legacy Mode is a V1 mute request and a version
	 * request to the V1connection. If this value is false, the write thread will send all commands to all
	 * devices.  
	 * 
	 * This value is defaulted to false to make it compatible with older versions of this library.
	 *   
	 * @param val The new value of this setting.
	 */
	public static void setProtectLegacyMode (boolean val)
	{
		m_protectLegacyMode = val;
	}
		
	/**
	 * Get the current state of Legacy Mode protection provided by the ESP library. Refer to 
	 * setProtectLegacyMode for more information on this feature. 
	 * 
	 * @return The current state of Legacy Mode protection provided by the ESP library.
	 */
	public static boolean getProtectLegacyMode ()
	{
		return m_protectLegacyMode;
	}

	public void setRun(boolean _run) {
		m_run = _run;
	}
	/**
	 * Sets up the data writer thread with a ValentineESP object, and an outputstream.
	 * 
	 * @param _esp			ValentineESP object to shut down various threads in the library if an exception occurs.
	 * @param _outStream	OutputStream to write to the V1Connection with.
	 */
	public DataWriterThread(ValentineESP _esp, OutputStream _outStream) {
		m_outStream = _outStream;		
		m_valentineEsp = _esp;
		m_readByteBuffer = new ArrayList<Byte>();
		m_run = true;
	}
	
	/**
	 * Executes indefinitely attempting to write Packets in the packetqueue to the open outputstream with the V1Connection.
	 */
	public void run() {
		while (m_run) {
			try {
				//if (PacketQueue.isV1Busy())
				//{
				//	sleep(100);
				//}
				//else
				//{
					ESPPacket packet = PacketQueue.getNextOutputPacket();
					
					if ( packet != null && !m_allowSendingPacket (packet) ){
						// Don't send this packet
						packet = null;
					}
					
					if (packet == null)
					{
						sleep(100);
					}
					else
					{
						if (PacketQueue.isPacketIdInBusyList(packet.getPacketIdentifier()))
						{
							PacketQueue.pushOutputPacketOntoQueue(packet);
							sleep(100);
						}
						else
						{
							PacketQueue.putLastWrittenPacketOfType(packet);
							byte[] buffer;
							
							if(ESPLibraryLogController.LOG_WRITE_INFO){
								Log.i("Valentine", "Writing to device " + packet.getPacketIdentifier().toString() + " to " + packet.getDestination().toString());
							}
							
							buffer = ESPPacket.makeByteStream(packet);
							
							buffer = escape(buffer);
							
							m_outStream.write(buffer);
							m_outStream.flush(); 
						}
	
					}
				//}
			} 
			catch (InterruptedException e) 
			{
				if(ESPLibraryLogController.LOG_WRITE_ERROR){
					Log.e(LOG_TAG, "DataWriterThread Interrupted. Shutting down esp...", e);
				}

				m_valentineEsp.stop();
				m_run = false;
			} 
			catch (IOException e) 
			{
				if(ESPLibraryLogController.LOG_WRITE_ERROR){
					Log.e(LOG_TAG, "IOException in DataWriter. Shutting down esp...", e);
				}
				m_valentineEsp.stop();
				m_run = false;
			}
		}
	}
	
	
	/**
	 * Determines if the packet passed in is allowed to be sent
	 *
	 * @param packet - The packet to check
	 * 
	 * @return True if the rules do not prohibit sending the packet, else false
	 */
	private static boolean m_allowSendingPacket (ESPPacket packet)
	{
		if ( PacketQueue.getV1Type() == Devices.VALENTINE1_LEGACY && m_protectLegacyMode ){
			// Check to see if the packet should be prohibited by Legacy Mode rules
			PacketId id = packet.getPacketIdentifier();
			if ( id == PacketId.reqVersion ){
				byte dest = (byte)(packet.getDestination().toByteValue() & 0x0F);
				byte origin = (byte)(packet.getOrigin().toByteValue() & 0x0F);
				
				// The V1connection is the only device that can accept a version query in Legacy Mode
				// Refer to the Bluetooth addendum for a discussion of why the origin should match the 
				// destination for the V1connection.
				if ( dest != origin ){
					// Do not allow version requests that are not to the V1connection
					if(ESPLibraryLogController.LOG_WRITE_INFO){
						Log.i(LOG_TAG, "Ignoring version request to " + packet.getDestination().toString() + " because the app is in Legacy mode.");
					}
					return false;
				}
			}			
			else if ( id == PacketId.reqMuteOn ){
				// The V1 can accept a mute request
				byte dest = (byte)(packet.getDestination().toByteValue() & 0x0F);
				byte v1Type = (byte)(packet.getV1Type().toByteValue() & 0x0F);
				if ( dest == v1Type ){
					// The request is not for the V1
					if(ESPLibraryLogController.LOG_WRITE_INFO){
						Log.i(LOG_TAG, "Ignoring mute on request to " + packet.getDestination().toString() + " because the app is in Legacy mode.");
					}
					return false;
				}
			}
			else{
				// There are no other requests allowed in Legacy Mode
				if(ESPLibraryLogController.LOG_WRITE_INFO){
					Log.i(LOG_TAG, "Ignoring " + id.toString() + " request to " + packet.getDestination().toString() + " because the app is in Legacy mode.");
				}
				return false;
			}
		}
		
		// No rules prohibited sending it, so allow it to be sent
		return true;
	}
	
	/**
	 * Converts reserved bytes to escape bytes, so they can be sent to the V1Connection.
	 * 
	 * @param _bytes	The byte to escape.
	 * 
	 * @return			The modified byte.
	 */
	private byte[] escape(byte[] _bytes)
	{
		int count = 0;
		for (int i = 1; i < _bytes.length-1; i++)
		{
			if (_bytes[i] == 0x7d )
			{
				count++;
			}
			else if (_bytes[i] == 0x7f)
			{
				count++;
			}
		}
		
		if (count == 0)
		{
			return _bytes;
		}
		else
		{
			byte[] escaped = new byte[_bytes.length + count];
			int escapedCounter = 1;
			escaped[0] = _bytes[0];
			
			for (int i = 1; i < _bytes.length-1; i++)
			{
				if (_bytes[i] == 0x7d )
				{
					escaped[escapedCounter] = 0x7d;
					escapedCounter++;
					escaped[escapedCounter] = 0x5d;
				}
				else if (_bytes[i] == 0x7f)
				{
					escaped[escapedCounter] = 0x7d;
					escapedCounter++;
					escaped[escapedCounter] = 0x5f;
				}
				else
				{
					escaped[escapedCounter] = _bytes[i];
				}
				escapedCounter++;
			}
			escaped[escaped.length-1] = _bytes[_bytes.length-1];
			return escaped;
		}
	}
}
