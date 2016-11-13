package com.valentine.esp.packets;

import java.util.ArrayList;

import android.util.Log;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.DevicesLookup;
import com.valentine.esp.constants.ESPLibraryLogController;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.constants.PacketIdLookup;
import com.valentine.esp.factories.PacketFactory;

/** Base class for all the packets.  Has all the basic functionality for getting source, destination, building from a buffer,
 * and turning into a buffer to send to the Valentine One.  Due to this class being abstract, it can't be created directly, but
 * use the PacketFactory class to generate the right type of ESPPacket. 
 *
 */
public abstract class ESPPacket 
{
	
	private static final String LOG_TAG = "ValentineESP/ESPPacket";
	
	protected static final byte frameDelimitedConstant = 0x7F;
	protected static final byte frameDataEscapeConstant = 0x7D;
	
	protected static final byte startOfFrameConstant = (byte) 0xAA;
	protected static final byte destinationIdentifierBaseConstant = (byte) 0xD0;
	protected static final byte originationIdentifierBaseConstant = (byte) 0xE0;
	protected static final byte endOfFrameConstant = (byte) 0xAB;
	
	protected static final byte originationSourceId = Devices.V1CONNECT.toByteValue(); 
	
	protected static final byte valentine1DestinationId = (byte) 0x0A;
	
	protected byte headerDelimter;
	protected byte packetLength;
	
	protected byte startOfFrame;
	protected byte destinationIdentifier; 
	protected byte originatorIdentifier;
	protected byte packetIdentifier;
	protected byte payloadLength;
	protected byte[] payloadData;
	protected byte checkSum;
	protected byte endOfFrame;
	
	protected byte packetChecksum;
	protected byte endDelimter;
	
	protected byte m_destination;
	
	protected Devices m_valentineType;
	protected static Devices lastKnownV1Type = Devices.UNKNOWN; 	
	protected long m_timeStamp;
	
	protected boolean m_resent = false;
	
	private static ArrayList<Byte> mLastStartBuffer = new ArrayList<Byte>();
	private static ArrayList<Byte> mLastEndBuffer = new ArrayList<Byte>();
	
	private enum ProcessState
	{
		START_PACK_BYTE,
		PACKET_LENGTH,
		SOF,
		DESTINATION,
		ORIGINATOR,
		PACKET_ID,
		PAYLOAD_LENGTH,
		PAYLOAD,						// Note: This includes the ESP checksum byte
		EOF,
		BT_CHECKSUM,
		END_PACK_BYTE
	}
	
	/**
	 * Check the packet passed in to see if it contains the same data as this packet. Note that this is not a complete equality test. Instead, this method
	 * checks to see if the two packets are conveying the same information to the same target.  
	 * We will not use these class members for the comparison
	 * m_valentineType - This may change based on the most recent infV1Display data, so it isn't used for the comparison
	 * lastKnownV1Type - This may change based on the most recent infV1Display data, so it isn't used for the comparison
	 * m_timeStamp - This may change throughout the packet lifetime and is not indicative of the type of equality this method is looking for.
	 * m_resent - This may change throughout the packet lifetime and is not indicative of the type of equality this method is looking for.
	 * 
	 * @param rhs - The ESPPacket to use for the comparison
	 * 
	 * @return true if the packets contain the same data, else false.
	 */
	public boolean isSamePacket (ESPPacket rhs)
	{
		if ( headerDelimter != rhs.headerDelimter ||
			packetLength != rhs.packetLength ||
			startOfFrame != rhs.startOfFrame ||
			destinationIdentifier != rhs.destinationIdentifier || 
			originatorIdentifier != rhs.originatorIdentifier ||
			packetIdentifier != rhs.packetIdentifier ||
			payloadLength != rhs.payloadLength ||
			checkSum != rhs.checkSum ||
			endOfFrame != rhs.endOfFrame ||
			packetChecksum != rhs.packetChecksum ||
			endDelimter != rhs.endDelimter ||
			m_destination != rhs.m_destination ){
			// One of the primitives doesn't match. We don't care which one
			return false;
		}
		
		if ( (payloadData == null && rhs.payloadData != null) || (payloadData != null && rhs.payloadData == null) ){
			// We have payload data for one of the arrays, but not the other
			return false;
		}
		
		if ( payloadData != null && rhs.payloadData != null){ 
			// Duplicate null check, but that is OK
			try {
				for ( int i = 0; i < payloadLength; i++ ){
					if ( payloadData[i] != rhs.payloadData[i] ){
						// Payload data mismatch
					}
				}
			}
			catch (Exception e){
				// Let's call this a mismatch
				return false;
			}
		}
		
		// If we get here, the comparison was successful
		return true;
	}
	
	/**
	 * Retrieve the current packet type 
	 * 
	 */
	public PacketId getPacketIdentifier()
	{
		return PacketIdLookup.getConstant(packetIdentifier);
	}
	
	/**
	 * Retrieve the current destination 
	 * 
	 */
	public Devices getDestination()
	{
		return DevicesLookup.getConstant(destinationIdentifier);
	}
	
	/**
	 * Retrieve the current origin 
	 * 
	 */
	public Devices getOrigin()
	{
		return DevicesLookup.getConstant(originatorIdentifier);
	}
	
	/**
	 * Retrieve the current V1 type used to build this packet 
	 * 
	 */
	public Devices getV1Type ()
	{
		return m_valentineType;
	}
	
	/**
	 * Retrieve the current number of bytes in the payload 
	 * 
	 */
	public int getPayloadLength()
	{
		return payloadLength;
	}
	
	/** Gets the packet's payload data
	 * @return the payload ata
	 */
	public byte[] getPayload()
	{
		return payloadData;
	}
	
	/**
	 * This method will copy bytes from one array list to another.
	 *  
	 * @param src - The byte to be copied
	 * @param dest - The copy destination
	 */
	private static void mCopyBuffer (ArrayList<Byte> src, ArrayList<Byte> dest)
	{
		dest.clear();
		
		for ( int i = 0; i < src.size(); i++ ){
			byte b = src.get(i);
			dest.add(b);
		}
	}
	
	/**
	 * This method will return a space delimited, hex format (0xCC) string for all bytes in the buffer passed in.
	 *  
	 * @param buffer - The buffer to be converted to a string
	 * 
	 * @return A space delimited hex string
	 */
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String getBufferLogString (ArrayList<Byte> buffer)
	{
		StringBuilder sb = new StringBuilder();
		char[] hexChars = new char[2];
		
		for ( int i = 0; i < buffer.size(); i++ ){
			if ( i > 0 ){
				// Add the delimiter
				sb.append(" 0x");
			}
			else{
				// Don't add the delimiter
				sb.append("0x");
			}
			
			int v = buffer.get(i) & 0xFF;
	        hexChars[0] = hexArray[v >>> 4];
	        hexChars[1] = hexArray[v & 0x0F];
	        
	        sb.append(new String(hexChars));			
		}
		
		return sb.toString();
	}
	
	/** 
	 * Takes an ArrayList of bytes and builds the resulting packet from that array.
	 * 
	 * @param buffer the buffer to make the packet from
	 * @return the new packet made from the buffer.  Look at its packetIdentifier and cast to the correct type
	 */	
	public static ESPPacket makeFromBuffer(ArrayList<Byte> buffer)
	{
		int startIdx = -1;
		int endIdx = -1;
		
		// Make a copy of the buffer as it was at the beginning of the method call
		ArrayList<Byte> curStartBuffer = new ArrayList<Byte>();		
		mCopyBuffer (buffer, curStartBuffer);				
				
		for ( int i = 0; i < buffer.size(); i++ ){
			byte test = buffer.get(i).byteValue();
			if (test == frameDelimitedConstant){
				if ( startIdx == -1 ){
					// We are looking for the start index
					startIdx = i;
				}
				else{
					// We are looking for the end index
					if ( i == startIdx + 1 ){
						// There are two delimiters together. This is expected to happen during startup when we can 
						// receive the end of one packet followed by a valid packet. In this instance, we want to 
						// move the start index to the beginning of the new packet
						startIdx = i;
					}
					else{
						// This should be the end of the packet so save the index and stop searching
						endIdx = i;
						break;
					}
				}
			}
		}
		
		if ( startIdx == -1 || endIdx == -1 ){
			// Copy start and end buffer in case the next call fails			
			mCopyBuffer (curStartBuffer, mLastStartBuffer);
			mCopyBuffer (buffer, mLastEndBuffer);
			
			// We did not receive a full packet
			return null;
		}
		
		if(startIdx != 0 && ESPLibraryLogController.LOG_WRITE_ERROR) {
			Log.e(LOG_TAG, "Skipping " + startIdx + " bytes because there was no delimiter at index 0");
			Log.e(LOG_TAG, "  Current buffer: "  + getBufferLogString (buffer));
			Log.e(LOG_TAG, "  Last Start buffer: "  + getBufferLogString (mLastStartBuffer));
			Log.e(LOG_TAG, "  Last End buffer: "  + getBufferLogString (mLastEndBuffer));
		}
		
		// Process the buffer
		int i = startIdx;
		int payloadIdx = 0;
		ESPPacket retPacket = null;
		ProcessState processState = ProcessState.START_PACK_BYTE;
		boolean dataError = false;
		
		// Store these values until we have a packet to put them into
		byte tempLength = 0;
		byte tempDest = 0;
		byte tempOrigin = 0;
		byte packetChecksum = 0;
		byte espChecksum = 0;
		
		while ( i <= endIdx ){
			// Read the next byte from the buffer 
			byte curByte = buffer.get(i).byteValue();
			
			if ( curByte == frameDataEscapeConstant ){
				// Check the next byte to see if it should be turned into an 0x7F or 0x7D
				i++;
				curByte = buffer.get(i).byteValue();
				// Skip the next byte after the delimiter
				if (curByte == (byte)0x5D){
					// If we find 0x5D after the delimiter, turn it into an 0x7D
					curByte = (byte)0x7D;					
				}
				else if (curByte == (byte)0x5F){
					// If we find 0x5F after the delimiter, turn it into an 0x7F
					curByte = (byte)0x7F;
				}				
			}
			
			switch (processState){
				default:
				case START_PACK_BYTE:
					if ( curByte != frameDelimitedConstant ){
						// How did THIS happen?!?
						dataError = true;
						if(ESPLibraryLogController.LOG_WRITE_ERROR) {
							Log.e(LOG_TAG, "Missing 0x7F at startIdx: " + startIdx);
						}
					}
					
					// Move to the next state. If there is a data error, the next state will never be used so
					// we don't need to check for that.
					processState = ProcessState.PACKET_LENGTH;					
					break;
					
				case PACKET_LENGTH:
					tempLength = curByte;
					
					// Update the checksum
					packetChecksum += curByte;
					
					// Move to the next state. If there is a data error, the next state will never be used so
					// we don't need to check for that.
					processState = ProcessState.SOF;
					break;
					
				case SOF:
					if ( curByte != startOfFrameConstant ){
						// Bad data so let's bail
						dataError = true;
						if(ESPLibraryLogController.LOG_WRITE_ERROR) {
							Log.e(LOG_TAG, "Missing SOF at the expected index: " + i);
						}
					}
					
					// Update the checksum
					packetChecksum += curByte;
					espChecksum += curByte;
					
					// Move to the next state. If there is a data error, the next state will never be used so
					// we don't need to check for that.
					processState = ProcessState.DESTINATION;					
					break;
					
				case DESTINATION:
					if ( (curByte & destinationIdentifierBaseConstant) != destinationIdentifierBaseConstant ){
						// This is not a valid destination
						dataError = true;
						if(ESPLibraryLogController.LOG_WRITE_ERROR) {
							Log.e(LOG_TAG, "Invalid destination ID (" + String.format("%02X ",curByte) + ") at the expected index: " + i);
						}
					}
					
					tempDest = curByte;
					
					// Update the checksum
					packetChecksum += curByte;
					espChecksum += curByte;
					
					// Move to the next state. If there is a data error, the next state will never be used so
					// we don't need to check for that.
					processState = ProcessState.ORIGINATOR;	
					break;
					
				case ORIGINATOR:
					if ( (curByte & originationIdentifierBaseConstant) != originationIdentifierBaseConstant ){
						// This is not a valid originator
						dataError = true;
						if(ESPLibraryLogController.LOG_WRITE_ERROR) {
							Log.e(LOG_TAG, "Invalid originator ID (" + String.format("%02X ",curByte) + ") at the expected index: " + i);
						}
					}
					
					tempOrigin = curByte;
					
					// Update the checksum
					packetChecksum += curByte;
					espChecksum += curByte;
					
					// Move to the next state. If there is a data error, the next state will never be used so
					// we don't need to check for that.
					processState = ProcessState.PACKET_ID;	
					break;
					
				case PACKET_ID:
					// Make the packet
					retPacket = PacketFactory.getPacket(PacketIdLookup.getConstant(curByte));
					if ( retPacket == null ){
						// We couldn't build the packet so stop trying
						dataError = true;
						if(ESPLibraryLogController.LOG_WRITE_ERROR) {
							Log.e(LOG_TAG, "Unable to generate packet for packet id (" + String.format("%02X ",curByte) + ")");
						}
					}
					else{
						// We have a good packet so fill it up
						retPacket.headerDelimter = frameDelimitedConstant;  //<- We can't get here if this wasn't true
						retPacket.packetLength = tempLength;
						
						retPacket.startOfFrame = startOfFrameConstant;   //<- We can't get here if this wasn't true
						retPacket.destinationIdentifier = tempDest;
						// Don't store the upper nibble of the origin
						retPacket.originatorIdentifier = (byte)(tempOrigin - originationIdentifierBaseConstant);
						retPacket.packetIdentifier = curByte;
						
						// If the packet identifier is equal to information display data id, the lastKnownV1Type.
						if(retPacket.packetIdentifier == PacketId.infDisplayData.toByteValue()){
							lastKnownV1Type = Devices.fromByteValue(retPacket.originatorIdentifier);							
						}
						else if ( lastKnownV1Type == Devices.UNKNOWN ){
							if ( retPacket.packetIdentifier != PacketId.respVersion.toByteValue() || retPacket.originatorIdentifier != Devices.V1CONNECT.toByteValue() ){
								// Always allow the V1connection version responses to pass through
								// Don't process any other data until we know what type of V1 we are working with
								dataError = true;
								if(ESPLibraryLogController.LOG_WRITE_ERROR) {
									Log.e(LOG_TAG, "Ignore packet id 0x" + String.format("%02X ",curByte) + " because the V1 type is unknown");
								}
							}
						}
						
						// Set the V1 type in the return packet without causing a packet rebuild
						retPacket.m_valentineType = lastKnownV1Type;
					}
					
					// Update the checksum
					packetChecksum += curByte;
					espChecksum += curByte;
					
					// Move to the next state. If there is a data error, the next state will never be used so
					// we don't need to check for that.
					processState = ProcessState.PAYLOAD_LENGTH;	
					break;
					
				case PAYLOAD_LENGTH:
					// Update the checksum
					packetChecksum += curByte;
					espChecksum += curByte;
					
					// Store the payload length reported by the source device
					retPacket.payloadLength = curByte;
					
					if ( retPacket.payloadLength == 0 ){
						// There is no payload data so we want to read the EOF.
						processState = ProcessState.EOF;
					}
					else{
						// There is payload data						
						processState = ProcessState.PAYLOAD;
						retPacket.payloadData = new byte[retPacket.payloadLength];
					}
					break;
					
				case PAYLOAD:					
					retPacket.payloadData[payloadIdx] = curByte;					
					payloadIdx ++;	
					
					if ( payloadIdx == retPacket.payloadLength ){
						// We are done reading the payload
						if(lastKnownV1Type == Devices.VALENTINE1_WITH_CHECKSUM){
							if ( espChecksum != curByte ){
								// The checksum does not match
								dataError = true;
								if(ESPLibraryLogController.LOG_WRITE_ERROR) {
									Log.e(LOG_TAG, "Bad ESP checksum. Expected 0x" + String.format("%02X ", espChecksum) + " but found 0x" + String.format("%02X ",curByte) );
								}
							}
							
							// Store the checksum 
							retPacket.checkSum = curByte;
						}
						
						// Get the EOF byte next
						processState = ProcessState.EOF;
					}
					else{
						// Update the ESP checksum only if this isn't the last byte
						espChecksum += curByte;
					}
					
					// Update the PACKET checksum
					packetChecksum += curByte;
					
					break;
					
				case EOF:
					if ( curByte != endOfFrameConstant ){
						// Bad data so let's bail
						dataError = true;
						if(ESPLibraryLogController.LOG_WRITE_ERROR) {
							Log.e(LOG_TAG, "Unable to find SOF at the expected index: " + i);
						}
					}
					
					retPacket.endOfFrame = curByte;
					
					// Update the packet checksum
					packetChecksum += curByte;
					
					// Move to the next state. If there is a data error, the next state will never be used so
					// we don't need to check for that.
					processState = ProcessState.BT_CHECKSUM;
					break;
					
				case BT_CHECKSUM:
					// Update the packet checksum
					if ( packetChecksum != curByte ){
						// We are missing something
						dataError = true;
						if(ESPLibraryLogController.LOG_WRITE_ERROR) {
							Log.e(LOG_TAG, "Bad packet checksum. Expected 0x" + String.format("%02X ",packetChecksum) + " but found 0x" + String.format("%02X ",curByte) );
						}
					}
					
					retPacket.packetChecksum = curByte;
					
					
					// Move to the next state. If there is a data error, the next state will never be used so
					// we don't need to check for that.
					processState = ProcessState.END_PACK_BYTE;
					break;
					
				case END_PACK_BYTE:
					if ( i != endIdx ){
						// We should be at the end of the data by now
						dataError = true; 
						if(ESPLibraryLogController.LOG_WRITE_ERROR) {
							Log.e(LOG_TAG, "Excpected to be at index " + endIdx + " but we are at index " + i);
						}
					}
					else if ( curByte != frameDelimitedConstant ){
						// How did THIS happen?!?
						dataError = true;
						if(ESPLibraryLogController.LOG_WRITE_ERROR) {
							Log.e(LOG_TAG, "Missing 0x7F at endIdx: " + endIdx);
						}
					}
					
					retPacket.endDelimter = curByte;
					
					// Move to the next state. If there is a data error, the next state will never be used so
					// we don't need to check for that.
					processState = ProcessState.START_PACK_BYTE;
					break;
			}
			
			if ( dataError ){
				// Stop processing the data
				break;
			}
			
			// Increment the index to the next byte
			i ++;		
		}
		
		// Remove all bytes up to and including the end index
		trimBuffer(buffer, endIdx);
		
		// Copy start and end buffer in case the next call fails			
		mCopyBuffer (curStartBuffer, mLastStartBuffer);
		mCopyBuffer (buffer, mLastEndBuffer);
		
		if ( dataError ){
			return null;
		}
		
		return retPacket;
	}

	/**
	 * Trims all the bytes from the buffer from zero to a specified end point
	 *  
	 * @param buffer	Reference to the byte buffer that will be trimmed.
	 * @param trimToPosition		The position to stop trimming at.
	 */
	public static void trimBuffer(ArrayList<Byte> buffer, int trimToPosition){
		// A potentially faster implementation to clear the buffer
	     buffer.subList(0, trimToPosition + 1).clear();
	}
	
	/** 
	 * Turns the given packet into a byte array to be sent off to the Valentine One
	 * 
	 * @param _packet the packet to be turned into a byte array
	 * @return the byte array that represents the packet
	 */
	public static byte[] makeByteStream(ESPPacket _packet)
	{
		/*
		 * The data format for a V1 With Checksums is
		 * 		Byte		Description
		 * 		0				Leading PACK byte (0x7F)
		 * 		1				The packet length
		 * 		2				ESP SOF
		 * 		3				ESP Destination
		 * 		4				ESP Originator
		 * 		5				ESP Packet ID
		 * 		6				ESP Payload Length
		 * 		7				First byte of payload data
		 *     		....
		 *     		....
		 *     		....
		 * 		7 + payloadSize	ESP Checksum
		 * 		8 + payloadSize	ESP EOF
		 * 		9 + payloadSize	BT Wrapper Checksum
		 * 		10				Trailing PACK byte (0x7F)
		 * 
		 * The data format for a V1 Without Checksums is
		 * 		Byte		Description
		 * 		0				Leading PACK byte (0x7F)
		 * 		1				The packet length
		 * 		2				ESP SOF
		 * 		3				ESP Destination
		 * 		4				ESP Originator
		 * 		5				ESP Packet ID
		 * 		6				ESP Payload Length
		 * 		7				First byte of payload data
		 *     		....
		 *     		....
		 *     		....
		 * 		7 + payloadSize	ESP EOF
		 * 		8 + payloadSize	BT Wrapper Checksum
		 * 		9				Trailing PACK byte (0x7F)
		 */
		
		byte[] buffer;
		int size = _packet.packetLength + 4;
		
		buffer = new byte[size];
		
		buffer[0] = (byte) (_packet.headerDelimter & 0xff);
		buffer[1] = (byte) (_packet.packetLength & 0xff);
		
		buffer[2] = (byte)(_packet.startOfFrame & 0xff);
		buffer[3] = (byte)(_packet.destinationIdentifier & 0xff); 
		buffer[4] = (byte)(_packet.originatorIdentifier & 0xff);
		buffer[5] = (byte)(_packet.packetIdentifier & 0xff);
		buffer[6] = (byte)(_packet.payloadLength & 0xff);

		int payloadDataSize = 0;
		if (_packet.payloadData != null)
		{
			payloadDataSize = _packet.payloadData.length;
			for (int i = 0; i < payloadDataSize; i++)
			{
				buffer[7 + i] = (byte)_packet.payloadData[i];
			}
		}
		
		if (_packet.checkSum == 0)
		{
			buffer[7 + payloadDataSize] = (byte)_packet.endOfFrame;
			
			buffer[8 + payloadDataSize] = _packet.packetChecksum;
			buffer[9 + payloadDataSize] = _packet.endDelimter;
		}
		else
		{
			buffer[7 + payloadDataSize] = (byte)_packet.checkSum;
			buffer[8 + payloadDataSize] = (byte)_packet.endOfFrame;
			
			buffer[9 + payloadDataSize] = _packet.packetChecksum;
			buffer[10 + payloadDataSize] = _packet.endDelimter;
		}
		
		return buffer;
	}

	@Override
	public String toString() {
		byte[] buffer = ESPPacket.makeByteStream(this);

		StringBuilder b = new StringBuilder("Packet Buffer Values:\n");

		for(int i = 0; i < buffer.length; i++) {
			b.append(String.format("Pos %d = %02X\n", i, buffer[i]));
		}

		return b.toString();
	}
	
	/**
	 * Calculates the checksum for the esp packet.
	 * 
	 * @return 		The byte value opf the esp packet checksum.
	 */
	protected byte makeMessageChecksum()
	{
		long temp = 
				startOfFrame +
				destinationIdentifier + 
				originatorIdentifier +
				packetIdentifier +
				payloadLength;
			
		if (payloadData != null)
		{
			for (int i = 0; i < payloadData.length; i++)
			{
				temp += payloadData[i];
			}
		}
			
		temp = temp & 0xff;
		
		return (byte)temp;
	}
	
	/**
	 * Calculates the checksum for the esp packet.
	 * 
	 * @return 	The byte value of the esp packet checksum.
	 */
	protected byte makePacketChecksum()
	{
		long temp = 
			startOfFrame +
			destinationIdentifier + 
			originatorIdentifier +
			packetIdentifier +
			payloadLength +
			checkSum +
			endOfFrame;
		
		if (payloadData != null)
		{
			for (int i = 0; i < payloadData.length; i++)
			{
				temp += payloadData[i];
			}
		}
		
		temp += packetLength;
		
		return (byte)temp;
	}
	
	
	/**
	 * Retreives the time stamp fo the ESP packet.
	 * 
	 * @return  	 The time stamp of the ESP packet.
	 */
	public long getTimeStamp()
	{
		return m_timeStamp;
	}
	
	/**
	 * Sets a flag that notifies that a ESP pacek thas been resent.
	 * 
	 * @param _resent	A boolean value that determine whether the packet has been resent.
	 */
	public void setResentFlag(boolean _resent)
	{
		m_resent = _resent;
	}
	
	/**
	 * Retrieves the resent flag.
	 * 
	 * @return		The boolean value taht determines whether or not the packet has been resent.
	 */
	public boolean getResentFlag()
	{
		return m_resent;
	}
	
	/**
	 * Changes the Valentine One type used to build this packet. If the current destination is a V1 type, the destination is changed to the new V1 type. identifier and rebuilds the packet
	 * 
	 * @param newV1Type	The new V1 type to use when building this packet.
	 */
	public void setNewV1Type (Devices newV1Type)
	{
		if ( newV1Type != m_valentineType ){
			Devices oldDest = getDestination();
			
			if ( oldDest == m_valentineType || oldDest == Devices.VALENTINE1_LEGACY || oldDest == Devices.VALENTINE1_WITH_CHECKSUM || oldDest == Devices.VALENTINE1_WITHOUT_CHECKSUM ){
				// Change the destination to the current V1 type
				m_destination = newV1Type.toByteValue();
			}
			m_valentineType = newV1Type;
			buildPacket();
		}
	}
	
	/**
	 * Sets up this packets static values. Vital logic should be implemented in children classes.
	 * Method that should be overridden by all inheriting classes.
	 * 
	 * Super call is mandatory for the ESP packet to function. Should be the first call in the method.
	 */
	protected void buildPacket(){
		headerDelimter = (byte)frameDelimitedConstant;
		
		startOfFrame = startOfFrameConstant;
		
		if (m_destination == Devices.VALENTINE1_LEGACY.toByteValue())
		{
			destinationIdentifier = (byte) (destinationIdentifierBaseConstant + Devices.VALENTINE1_WITHOUT_CHECKSUM.toByteValue());
		}
		else if(m_destination == Devices.UNKNOWN.toByteValue()){
			
			destinationIdentifier = (byte) (destinationIdentifierBaseConstant + Devices.VALENTINE1_WITH_CHECKSUM.toByteValue());
		}
		else
		{
			destinationIdentifier = (byte) (destinationIdentifierBaseConstant + m_destination);
		}
		
		originatorIdentifier = (byte) (originationIdentifierBaseConstant + Devices.V1CONNECT.toByteValue());
		
		endOfFrame = endOfFrameConstant;

		endDelimter = (byte)frameDelimitedConstant;
		
	}

	/**
	 *  Gets the data embedded into the packet.  Should not need to call directly, data returned directly from the Valentine Client.
	 * @return An object representing the data in the packet.  Cast to the correct type for the packet. 
	 */
	abstract public Object getResponseData();
}


