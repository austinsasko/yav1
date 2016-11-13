package com.valentine.esp.threads;

//import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import android.util.Log;

import com.valentine.esp.PacketQueue;
import com.valentine.esp.ValentineESP;
import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.ESPLibraryLogController;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.constants.PacketIdLookup;
import com.valentine.esp.packets.ESPPacket;

public class DataReaderThread extends Thread 
{
	private static final int EMPTY_READ_SLEEP_TIME 	= 100;
	private final int MAX_EMPTY_READS;
	private static final String LOG_TAG         = "ValentineESP/DataReaderThread";
	
	private int m_dispCount;
	/* This is the expected sequence of m_dispCount
	 * Event															Result
	 * infV1Busy packet received										m_dispCount reset to 0
	 * infDisplayData received WITH a preceding invV1Busy packet		m_dispCount incremented to 1
	 * infDisplayData received WITHOUT a preceding invV1Busy packet		m_dispCount incremented
	 */
	private static final int V1_BUSY_RESET_VAL = 0;
	private static final int V1_NOT_BUSY_THRESH = 2;
	private static final int BUSY_INCREMENT_THRESH = V1_NOT_BUSY_THRESH + 1; // Increment to 1 past the trigger point but don't 
																			 // keep incrementing because we don't want to wrap the counter.
			
	private ArrayList<Byte> m_readByteBuffer;
	private InputStream     m_inStream;
	private ValentineESP    m_valentineEsp;
	private int             m_emptyReadCount;

	private boolean m_run;
	private boolean m_notifiedNoData;

	

	public void setRun(boolean _run) {
		m_run = _run;
	}
	
	/**
	 * Sets up the reader thread with the ValentineESP object, an inputstream and a timeout.
	 * 
	 * @param _esp				A ValentineESP object to to notify the library of the data read from the inputstream (V1/V1connection)
	 *  or shut down the library if an exception occurs.. 
	 * @param _inStream			InputStream with the bluetooth connection. Stream data from the V1connection.
	 * @param secondsToWait		A time to wait before notify the libray the no data has been received.
	 */
	public DataReaderThread(ValentineESP _esp, InputStream _inStream, int secondsToWait) {
		m_inStream = _inStream;
		m_valentineEsp = _esp;
		m_readByteBuffer = new ArrayList<Byte>();
		m_run = true;
		m_notifiedNoData = false;
		m_dispCount = 0;
		MAX_EMPTY_READS = secondsToWait * 10;
	}

	/**
	 * Execute indefinitely reading data from the open inputstream with V1Connection.
	 */
	public void run() {
		byte buffer[] = new byte[1024];
		m_emptyReadCount = 0;
		
		// Reset the display count before starting and clear the busy packets in the packet queue
		m_dispCount = V1_BUSY_RESET_VAL;
		PacketQueue.setBusyPacketIds(null);
		PacketQueue.clearSendAfterBusyQueue();
		
		while (m_run) {
			try {

				if (m_inStream.available() == 0) {
					m_emptyReadCount++;
					sleep(EMPTY_READ_SLEEP_TIME);
					if (m_emptyReadCount == MAX_EMPTY_READS)
					{
						if (!m_notifiedNoData)
						{
							m_notifiedNoData = true;
							m_valentineEsp.notifyNoData();
						}
					}
				}
				else
				{
					m_notifiedNoData = false;
					int readSize = m_inStream.read(buffer);
					
					m_emptyReadCount = 0;
					for (int i = 0; i < readSize; i++)
					{
						m_readByteBuffer.add(new Byte(buffer[i]));
					}
					
					ESPPacket newPacket = null;
					do
					{
						try {
							newPacket = ESPPacket.makeFromBuffer(m_readByteBuffer);
							if (newPacket != null)
							{
								if (isPacketForMe(newPacket))
								{
									if ((newPacket.getPacketIdentifier() != PacketId.infDisplayData) && (newPacket.getPacketIdentifier() != PacketId.respAlertData))
									{
										if ( ESPLibraryLogController.LOG_WRITE_INFO ) {
											if (newPacket.getPacketIdentifier().toByteValue() == PacketId.infV1Busy.toByteValue() && ESPLibraryLogController.LOG_WRITE_VERBOSE ){
												ArrayList<Integer> packetList = (ArrayList<Integer>)(newPacket.getResponseData());
												int busyCnt = packetList.size();
												if ( newPacket.getV1Type() == Devices.VALENTINE1_WITH_CHECKSUM ){
													// The response data includes the check sum, which we don't care about
													busyCnt --;
												}
												String logStr = "Received infV1Busy packet: ";
												for ( int i = 0; i < busyCnt; i++ ){
													if ( i != 0 ){
														logStr += ",";
													}
													PacketId id = PacketIdLookup.getConstant( packetList.get(i).byteValue());
													logStr += id.toString();
													
												}
												Log.i("Valentine", logStr);												
											}
											else{
												Log.i("Valentine", "Packet of type " + newPacket.getPacketIdentifier().toString() + " received");
											}
										}
									}

									if (newPacket.getPacketIdentifier() == PacketId.infV1Busy)
									{
										// Clear the counter when we get a busy packet and tell the packet queue what the V1 is busy working on.
										m_dispCount = V1_BUSY_RESET_VAL;
										PacketQueue.setBusyPacketIds(newPacket);
									}
									else if (newPacket.getPacketIdentifier() == PacketId.respRequestNotProcessed)
									{
										// The hardware could not process this requet. We are going to assume it is because the hardware is busy
										// so we will requeue the packetto be sent out again
										Integer idInt = (Integer)newPacket.getResponseData();
										byte packetId = idInt.byteValue(); // (Byte)newPacket.getResponseData();
										ESPPacket packet = PacketQueue.getLastWrittenPacketOfType(PacketIdLookup.getConstant(packetId));

										if (!packet.getResentFlag())
										{
											if(ESPLibraryLogController.LOG_WRITE_INFO){
												Log.i("Valentine", "Requeuing packet of type " + PacketIdLookup.getConstant(packetId).toString() ); 
											}
											packet.setResentFlag(true);
											
											if ( m_dispCount < V1_NOT_BUSY_THRESH ){
												// Send the try resending the packet after the V1 is not busy
												PacketQueue.pushOnToSendAfterBusyQueue(packet);
											}
											else{
												// The V1 is not busy now, so send the packet to the V1 now
												PacketQueue.pushOutputPacketOntoQueue(packet);
											}
										}
										else
										{
											if(ESPLibraryLogController.LOG_WRITE_INFO){
												Log.i("Valentine", "Aborting resend of packet of type " + PacketIdLookup.getConstant(packetId).toString() );												
											}
											PacketQueue.pushInputPacketOntoQueue(newPacket);
										}
									}
									else if (newPacket.getPacketIdentifier() == PacketId.infDisplayData)
									{
										// See the table above to determine why the values 2 and 3 were chosen for this task
										if ( m_dispCount < BUSY_INCREMENT_THRESH){
											// Increment to 1 past the trigger point but don't keep incrementing because we don't want to wrap
											// the counter.
											m_dispCount++;
										}
										
										if ( m_dispCount == V1_NOT_BUSY_THRESH){
											// We hit the trigger point to decide that the V1 is not busy. Transfer packets that were not processsed
											// to the output queue so we can try again.
											PacketQueue.setBusyPacketIds(null);
											PacketQueue.sendAfterBusyQueue();
										}

										// Always send display packets to the app.
										PacketQueue.pushInputPacketOntoQueue(newPacket);
									}

									else
									{
										// This is not a special case, so send this packet to the app
										PacketQueue.pushInputPacketOntoQueue(newPacket);
									}
								}
							}
						} catch(ClassCastException ex) {
							if(ESPLibraryLogController.LOG_WRITE_ERROR){
								Log.e(LOG_TAG, String.format("Invalid Packet Data encountered: %s", newPacket), ex);
							}
						}						
					} while (newPacket != null);
				}
			} 
			catch (InterruptedException e) 
			{
				if(ESPLibraryLogController.LOG_WRITE_ERROR){
					Log.e(LOG_TAG, "DataReader Thread Interrupted, shutting down esp...", e);
				}
				m_run = false;
				m_valentineEsp.stop();
			} 
			catch (IOException e) 
			{
				if(ESPLibraryLogController.LOG_WRITE_WARNING){
					Log.w(LOG_TAG, "IOException encountered, shutting down esp...", e);
				}
				m_run = false;
				m_valentineEsp.stop();
			}
		}
	}
	/**
	 * Determines if the passed in ESPPacket received is for us or not. 
	 * @param newPacket		The packet to check the destination of.
	 * 
	 * @return	 True if the packet is a for the V1Connection or a General Broadcast, otherwise false.
	 */
	private boolean isPacketForMe(ESPPacket newPacket)
	{
		if ((newPacket.getDestination() == Devices.V1CONNECT) || (newPacket.getDestination() == Devices.GENERAL_BROADCAST))
		{
			return true;
		}
		
		return false;
	}
}
