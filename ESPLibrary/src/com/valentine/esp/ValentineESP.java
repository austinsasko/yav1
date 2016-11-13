package com.valentine.esp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.valentine.esp.constants.ESPLibraryLogController;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.demo.DemoData;
import com.valentine.esp.packets.ESPPacket;
import com.valentine.esp.threads.DataReaderThread;
import com.valentine.esp.threads.DataWriterThread;
import com.valentine.esp.utilities.Utilities;

/** This is the underlying class that connects to and processes packets from the Valentine One.
 * This is wrapped by the ValentineClient class to extract out the data from the packets so the programmer
 * does not not need to handle packets in any fashion.  Nothing here should be called directly, but instead through the 
 * ValentineClient class.
 * 
 */
public class ValentineESP 
{
	private Map<PacketId, ArrayList<CallbackData>> m_callbackData = new HashMap<PacketId, ArrayList<CallbackData>>();	
	
	private DataReaderThread m_readerThread;
	private DataWriterThread m_writerThread;
	private ProcessingThread m_processingThread;
	
	private BluetoothAdapter m_bta;
	private BluetoothDevice m_pairedBluetoothDevice;
	private BluetoothSocket m_socket;
	private InputStream m_inputStream;
	private OutputStream m_outputStream;
	
	private ProcessDemoFileThread m_demoFileThread;
	
	private Object m_stopObject;
	private String m_stopFunction;
	
	private boolean m_notified;
	
	private boolean m_inDemoMode;
	
	private DemoData m_demoData;
	
	private Object m_noDataObject;
	private String m_noDataFunction;
	
	private Object m_unsupportedObject;
	private String m_unsupportedFunction;
	
	private ReentrantLock	m_lock = new ReentrantLock();
	private boolean		 	m_connected;
	private boolean			m_retryOnConnectFailure;
    private boolean         m_bt_workaround = false;

	/**
	 * Time out to be passed into the DataReaderThread that will be used to notify the library of the no data.
	 * Passed in using seconds.
	 *
	 */
	private final int m_secondsToWait;

	/**
	 * Object to hold the items needed to register a callback
	 */
	private class TempCallbackInfo
	{
		public PacketId type;
		public Object callBackObject;
		public String method;	
	}
	
	// The m_packetCallbackLock protects all of the following class members. 
	private ReentrantLock						m_packetCallbackLock = new ReentrantLock();
	private PacketId							m_lockedPacket = PacketId.unknownPacketType;;
	private ArrayList<DeregCallbackInfo>		m_packetsToDeregister = new ArrayList<DeregCallbackInfo>();
	private ArrayList<TempCallbackInfo> 		m_packetsToRegister = new ArrayList<TempCallbackInfo>();
	private boolean								m_clearAllCallbacksOnUnlock = false;
	// End of m_packetCallbackLock protection section

    // Added by Francky
    private int       mBroadcastEvent     = 0;
    private Context   mBroadCastContext   = null;

    public void setBroadcastContext(Context context)
    {
        this.mBroadCastContext = context;
    }

    public void setBroadcastEvents(int events)
    {
        mBroadcastEvent = events;
    }

    public void setBroadcastEvent(int event, boolean onOff)
    {
        if(onOff)
            mBroadcastEvent |= event;
        else
            mBroadcastEvent = (mBroadcastEvent & (~event));
    }

    public void broadcastV1Event(int event)
    {
        if( (mBroadcastEvent & event) > 0 ) {
            Intent intent = new Intent("V1ESP");
            intent.putExtra("event", event);
            LocalBroadcastManager.getInstance(mBroadCastContext).sendBroadcast(intent);
        }
    }

    public void broadcastV1Event(int event, boolean extra)
    {
        if( (mBroadcastEvent & event) > 0 ) {
            Intent intent = new Intent("V1ESP");
            intent.putExtra("event", event);
            intent.putExtra("extra", extra);
            LocalBroadcastManager.getInstance(mBroadCastContext).sendBroadcast(intent);
        }
    }

    public void clearBroadcastEvents()
    {
        mBroadcastEvent = 0;
    }

    public int getBroadcastEvent()
    {
        return mBroadcastEvent;
    }

    // end added

	/**
	 * ValentineESP Constructor
	 */
	public ValentineESP(int secondsToWait)
	{
		m_bta = BluetoothAdapter.getDefaultAdapter();
		m_notified = true;
		m_inDemoMode = false;
		m_demoData = new DemoData();
		m_connected = false;
		m_secondsToWait = secondsToWait;
        m_bt_workaround = false;
		m_retryOnConnectFailure = true;
	}

    /*
     * Enable the attempt2 for bluethooth
     */
    public void enableBtWorkAround(boolean bt)
    {
        m_bt_workaround = bt;
    }
	/**
	 * This method will show all callbacks in LogCat.
	 * 
	 * @precondition The caller has m_packetCallbackLock locked.
	 * 
	 */
	/*
	private void m_showAllCallbacks ()
	{
		if ( ESPLibraryLogController.LOG_WRITE_VERBOSE ){
			Log.v("CallbackList","Current ValentineESP callbacks (" + this + ")");
		
			for (Map.Entry<PacketId, ArrayList<CallbackData>> entry : m_callbackData.entrySet())
			{
			    for ( int i = 0; i < entry.getValue().size(); i++ ){
			    	Log.v("CallbackList", "   " + entry.getKey().toString() + ":" + entry.getValue().get(i).callBackOwner.toString());
			    }
			}
		}
	}
	*/
	
	/**
	 * This method will set the locked packet type for this object. A packet type that is locked can not have any new
	 * callbacks registered and it can not have any existing callbacks deregistered. This is needed to prevent modifying
	 * the array list while it is being traversed.
	 *  
	 * @param lockedType - The packet type to lock
	 */
	protected void setLockedPacket (PacketId lockedType)
	{
		m_packetCallbackLock.lock();
		m_lockedPacket = lockedType;
		
		if ( lockedType.toByteValue() == PacketId.unknownPacketType.toByteValue() ){
			if ( m_clearAllCallbacksOnUnlock ){
				// A request to clear all callbacks was received while we had a locked packet
				if ( ESPLibraryLogController.LOG_WRITE_VERBOSE ){
		    		Log.v("ValentineESP", "Clearing " + m_callbackData.size() + " callbacks after waiting for unlock");
				}
				
				m_callbackData.clear();
				m_clearAllCallbacksOnUnlock = false;
			}
			
			// Note that we want to process the pending registrations and deregistrations even if m_clearAllCallbacksOnUnlock is set. We are doing that
			// just in case someone performed one of those actions after calling clearAllCallbacks.
			
			// If the packet passed in is for an unknown packet type, we are free to handle the packets that
			// are queued up for registration and deregistration.
			for ( int i = 0; i < m_packetsToDeregister.size(); i++ ){
				DeregCallbackInfo curInfo = m_packetsToDeregister.get(i);
				// Call the private method because we have the queue locked
				m_deregisterForPacket(curInfo.type, curInfo.callBackOwner, curInfo.method);
			}			
			m_packetsToDeregister.clear();
			
			for ( int i = 0; i < m_packetsToRegister.size(); i++ ){
				TempCallbackInfo temp = m_packetsToRegister.get(i);
				// Call the private method because we have the queue locked
				m_registerForPacket(temp.type, temp.callBackObject, temp.method);
			}			
			m_packetsToRegister.clear();
		}
		m_packetCallbackLock.unlock();
	}

	/**
	 * This method will allow the caller to determine if the ESP library is operating in demo mode or not.
	 * 
	 * @return - true if the ESP library is operating in demo mode, else false.
	 */
	public boolean isInDemoMode()
	{
		return m_inDemoMode;
	}
	
	/**
	 * This method will allow the caller to determine if there is an active bluetooth connection.
	 * @return - true if there is an active bluetooth connection, else false.
	 */
	public boolean getIsConnected()
	{	
		m_lock.lock();
		boolean retVal = m_connected;
		m_lock.unlock();
		
		return retVal;
	}
	
	/**
	 * This method should be called when the broadcast received on the ValentineClient receives a connect or disconnect
	 * message. NO OTHER OBJECTS SHOULD CALL THIS METHOD.
	 * 
	 * @param val - The new connected state for the bluetooth connection.
	 */
	public void setIsConnected (boolean val)
	{
		m_lock.lock();
		m_connected = val;
		m_lock.unlock();
	}
	
	/**
	 * This method will set up a callback to use when we determine that the phone may not support SPP. 
	 * 
	 * @param _obj The object to be notifed when there is an unsupported phone
	 * @param _function The function to be called
	 */
	public void setUnsupportedCallbacks(Object _obj, String _function)
	{
		m_unsupportedObject = _obj;
		m_unsupportedFunction = _function;
	}
	
	/** Sets the callback data for when the demo data processing finds a User Settings packet in it. 
	 * Requires a function with a UserSettings parameter:  public void function( UserSettings _parameter)
	 * 
	 * @param _owner Object to be notified when there is a demo data user settings object in the demo data
	 * @param _function Function to be called
	 */
	public void setDemoConfigurationCallbackData(Object _owner, String _function)
	{
		m_demoData.setConfigCallbackData(_owner, _function);
	}
	
	/**
	 * This method starts the ESP library.
	 * 
	 * @param _pairedDevice - The bluetooth device to use for this connection.
	 * 
	 * @return - true if the connection was established and the processing threads have been started, else false
	 */
	public boolean startUp(BluetoothDevice _pairedDevice)
	{
		if (m_inDemoMode)
		{
			stopDemo(false);
		}
		
		m_processingThread = new ProcessingThread();
		m_pairedBluetoothDevice = _pairedDevice;
		
		if (connect())
		{
			m_startProcessing();
			m_notified = false;
			return true;
		}
		else
		{
			return false;
		}
	}
	
	/**
	 * This method performs the actual connection to the bluetooth device.
	 * 
	 * @return - true if the connection was established, else false.
	 */
	public boolean connect()
    {
		m_bta.cancelDiscovery();
		try
		{
			// Make sure the previous connection is closed before proceeding
			m_closeSocket (true);
			
			// Assume the connect attempt will not be aborted
			m_retryOnConnectFailure = m_bt_workaround;
			
			UUID uu = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
			//if (android.os.Build.VERSION.SDK_INT > 8)
			//{
				m_socket = m_pairedBluetoothDevice.createInsecureRfcommSocketToServiceRecord(uu);
			//}
			//else
			//{
			//	m_socket = m_pairedBluetoothDevice.createRfcommSocketToServiceRecord(uu);
			//}
			
			m_socket.connect();
			
			m_inputStream = m_socket.getInputStream();
			m_outputStream = m_socket.getOutputStream();
			
			m_readerThread = new DataReaderThread(this, m_inputStream, m_secondsToWait);
			m_writerThread = new DataWriterThread(this, m_outputStream);
			
			return true;
		}
		catch (IOException e) 
		{
			if ( m_retryOnConnectFailure ) {
				return m_attempt2();
			}
			else if(ESPLibraryLogController.LOG_WRITE_DEBUG){
                Log.d("Valentine", e.toString());
            }

			return false;
		} 
		
		catch (SecurityException e) 
		{
			ValentineClient.getInstance().reportError("Unable to connect to " + m_pairedBluetoothDevice.getName());
			if(ESPLibraryLogController.LOG_WRITE_DEBUG){
				Log.d("Valentine", e.toString());
			}
		} 
		catch (IllegalArgumentException e) 
		{
			ValentineClient.getInstance().reportError("Unable to connect to " + m_pairedBluetoothDevice.getName());
			if(ESPLibraryLogController.LOG_WRITE_DEBUG){
				Log.d("Valentine", e.toString());
			}
		} 
		catch (java.lang.NoSuchMethodError e)
		{
			ValentineClient.getInstance().reportError("Unable to connect to " + m_pairedBluetoothDevice.getName());
			if(ESPLibraryLogController.LOG_WRITE_DEBUG){
				Log.d("Valentine", e.toString());
			}
		}
		catch (Exception e)
		{
			ValentineClient.getInstance().reportError("Unable to connect to " + m_pairedBluetoothDevice.getName());
			if(ESPLibraryLogController.LOG_WRITE_DEBUG){
				Log.d("Valentine", e.toString());
			}
		}
		
		return false;
    }
	
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
	public void setProtectLegacyMode (boolean val)
	{
		DataWriterThread.setProtectLegacyMode(val);
	}
		
	/**
	 * Get the current state of Legacy Mode protection provided by the ESP library. Refer to 
	 * setProtectLegacyMode for more information on this feature. 
	 * 
	 * @return The current state of Legacy Mode protection provided by the ESP library.
	 */
	public boolean getProtectLegacyMode ()
	{
		return DataWriterThread.getProtectLegacyMode();
	}
	
	/** This registers an object/function combination to be notified when the ESP client stops.  
	 *  Only one allowed at a time
	 *  Requires a function with a Void parameter:  public void function( Void _parameter)
	 *  
	 * @param _stopObject  Object with the function to be called
	 * @param _stopFunction  Function to be called
	 */
	public void registerForStopNotification(Object _stopObject, String _stopFunction)
	{
		m_stopObject = _stopObject;
		m_stopFunction = _stopFunction;
	}
	
	/** This removes all registrations for callbacks to be notified when the ESP client stops.  
	 *  
	 * @param _stopObject  Object with the function to be called
	 * @param _stopFunction  Function to be called
	 */
	public void deregisterForStopNotification()
	{
		// Note that this type of callback does not use the callback data list, so it is safe to modify it here.
		m_stopObject = null;
		m_stopFunction = null;
	}
	
	/** This registers an object/function combination to be notified when a specific ESP packet is received.  
	 *  Requires a function with a Void parameter:  public void function( ESPPacket _parameter)
	 * 
	 * @param _type - The packet id the registation is for.
	 * @param _callBackObject - The object to use for the callback.
	 * @param _method - The method in _callbackObject to call.
	 */
	public void registerForPacket(PacketId _type, Object _callBackObject, String _method)
	{
		m_packetCallbackLock.lock();
		if ( m_lockedPacket.toByteValue() != _type.toByteValue() ){
			// We are allowed to deregister the packet right now
			m_registerForPacket(_type, _callBackObject, _method);			
		}
		else{
			// We are not allowed to register the packet right now. Put it in the queue for deregistration later.
			TempCallbackInfo temp = new TempCallbackInfo();
			temp.type = _type;
			temp.callBackObject = _callBackObject;
			temp.method = _method;
			m_packetsToRegister.add( temp );
		}
		m_packetCallbackLock.unlock();
	}
	
	/**
	 * This method will perform the actual work for registering a callback packet for a specific object.
	 * 
	 * @precondition The caller has m_packetCallbackLock locked.
	 * 
	 * @param _type - The packet type to register.
	 * @param _callBackObject - The object to register.
	 * @param _method - The name of the callback method.
	 */
	private void m_registerForPacket(PacketId _type, Object _callBackObject, String _method)
	{
		CallbackData newCallbackData = new CallbackData();
		newCallbackData.callBackOwner = _callBackObject;
		newCallbackData.method = _method;
		
		if (m_callbackData.containsKey(_type))
		{
			ArrayList<CallbackData> list = m_callbackData.get(_type);
			list.add(newCallbackData);
		}
		else
		{
			ArrayList<CallbackData> newList = new ArrayList<CallbackData>();
			newList.add(newCallbackData);
			m_callbackData.put(_type, newList);
		}
	}
	
	/**
	 * This method will deregister callback set up using registerForPacket. All callbacks for the PacketId and object 
	 * passed in will be deregistered.
	 * 
	 * @param _type - The packet id to degister.
	 * @param _object - The object to deregister.
	 */
	public void deregisterForPacket(PacketId _type, Object _object, String _method)
	{
	
		m_packetCallbackLock.lock();
		if ( m_lockedPacket.toByteValue() != _type.toByteValue() ){
			// We are allowed to deregister the packet right now
			m_deregisterForPacket(_type, _object, _method);			
		}
		else{
			// We are not allowed to register the packet right now. Put it in the queue for deregistration later.
			DeregCallbackInfo info = new DeregCallbackInfo();
			info.callBackOwner = _object;
			info.type = _type;
			info.method = _method;
			m_packetsToDeregister.add(info);
		}
		m_packetCallbackLock.unlock();
	}
	
	/**
	 * This method will deregister callback set up using registerForPacket. Only the callback that matches the object and method 
	 * passed in will be deregistered.
	 * 
	 * @param _type - The packet id to degister.
	 * @param _object - The object to deregister.	 * 
	 * @param _method - The name of the callback method.
	 */
	public void deregisterForPacket(PacketId _type, Object _object)
	{
		m_packetCallbackLock.lock();
		if ( m_lockedPacket.toByteValue() != _type.toByteValue() ){
			// We are allowed to deregister the packet right now
			m_deregisterForPacket(_type, _object, "");			
		}
		else{
			// We are not allowed to register the packet right now. Put it in the queue for deregistration later.
			DeregCallbackInfo info = new DeregCallbackInfo();
			info.callBackOwner = _object;
			info.type = _type;
			info.method = "";
			m_packetsToDeregister.add(info);
		}
		m_packetCallbackLock.unlock();
	}
	
	/**
	 * This method will perform the actual work for deregistering a packet for a specific object.
	 * 
	 * @precondition The caller has m_packetCallbackLock locked.
	 * 
	 * @param _type - The packet type to deregister
	 * @param _object - The object to deregister.
	 * @params _method - If "", then remove all callbacks else remove only the callbacks to _method.
	 */
	private void m_deregisterForPacket(PacketId _type, Object _object, String _method)
	{
		if (m_callbackData.containsKey(_type))
		{
			for (Iterator<Entry<PacketId, ArrayList<CallbackData>>> it = m_callbackData.entrySet().iterator(); it.hasNext();) 
			{ 
			    Map.Entry<PacketId, ArrayList<CallbackData>> pairs = (Map.Entry<PacketId, ArrayList<CallbackData>>)it.next();
			    
			    if (pairs.getKey() == _type)
			    {
				    for (Iterator<CallbackData> it2 = pairs.getValue().iterator(); it2.hasNext();)
				    {
				    	CallbackData data = it2.next();
				    	if (data.callBackOwner == _object && (_method == "" || _method == data.method) )				    		
				    	{
				    		if ( ESPLibraryLogController.LOG_WRITE_VERBOSE ){
				    			Log.v("ValentineESP", "Deregistering " + data.callBackOwner.toString() + "." + data.method + " for packet id " + _type.toString());
				    		}
				    		
				    		it2.remove();
				    		break;
				    	}
				    }
			    }
			    
			}
		}
		
	}
	
	/**
	 * This method will allow the caller to determine if they have already registered for a specific packet using registerForPacket.
	 * 
	 * @param _type - The PacketId to search for.
	 * @param _object - The object to search for.
	 * 
	 * @return - true if the obect is already registered for the packet id passed in, else false.
	 */
	public boolean isRegisteredForPacket (PacketId _type, Object _object)
	{
		// Lock here so the queue doesn't get modified while we are traversing it.
		m_packetCallbackLock.lock();
		
		if (m_callbackData.containsKey(_type))
		{
			for (Iterator<Entry<PacketId, ArrayList<CallbackData>>> it = m_callbackData.entrySet().iterator(); it.hasNext();) 
			{ 
			    Map.Entry<PacketId, ArrayList<CallbackData>> pairs = (Map.Entry<PacketId, ArrayList<CallbackData>>)it.next();
			    
			    if (pairs.getKey() == _type)
			    {
				    for (Iterator<CallbackData> it2 = pairs.getValue().iterator(); it2.hasNext();)
				    {
				    	CallbackData data = it2.next();
				    	if (data.callBackOwner == _object)
				    	{
				    		// Found a registration for the requested packet
				    		
				    		m_packetCallbackLock.unlock();
				    		
				    		return true;
				    	}
				    }
			    }
			    
			} 
			
		}
		
		m_packetCallbackLock.unlock();
		
		// We didn't find any registration for the packet/object pair passed in. 
		return false;
		
	}
	
	/**
	 * This method will start the read, write and processing threads.
	 * 
	 */
	private void m_startProcessing()
	{
		m_readerThread.start();
		m_writerThread.start();
		if (!m_processingThread.isAlive())
			m_processingThread.start();
	}
	
	/**
	 * This method will close the bluetooth connection and stop all processing threads. This method DOES NOT wait for 
	 * the connection to be completely closed before returning.
	 */
	public void stop()
	{
		if (!m_inDemoMode)
		{
			try 
			{
				// Close the socket, but don't wait for a disconnect.
				m_closeSocket (false);
				
				if (m_processingThread != null)
				{
					m_processingThread.setRun(false);
					m_processingThread.interrupt();
				}
				
				if (m_readerThread != null)
				{
					m_readerThread.setRun(false);
					m_readerThread.interrupt();
				}
				
				if (m_writerThread != null)
				{
					m_writerThread.setRun(false);
					m_writerThread.interrupt();
				}
				
				if ((m_stopObject != null) && (m_stopFunction != null) && !m_notified)
				{
					m_notified = true;
					Utilities.doCallback(m_stopObject, m_stopFunction, Boolean.class, true);
				}
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * This helper method will close the socket and optionally wait for it to close.
	 * 
	 * @param waitForClose - If true, this method will wait for the socket to be closed before returning.
	 * 
	 * @return True if no errors, else false.   
	 */
	private boolean m_closeSocket (boolean waitForClose)
	{
		if ( m_socket != null ){
			try 
			{			
				if(ESPLibraryLogController.LOG_WRITE_INFO){
					Log.i("ValentineShutdown", "Calling socket close");
				}
				
				// Abort any connection retry attempts
				m_retryOnConnectFailure = false;
				
				m_socket.close();
				
				if ( waitForClose ){
					boolean connected = getIsConnected();
					long waitEnd = SystemClock.uptimeMillis() + 15000;
					long loopCount = 0;
					
					while ( connected && SystemClock.uptimeMillis() < waitEnd){
						if ( loopCount == 0 ){
							if(ESPLibraryLogController.LOG_WRITE_INFO){
								Log.i("ValentineShutdown", "Starting socket close wait");
							}
						}
						else if ( loopCount % 10 == 0 ){
							if(ESPLibraryLogController.LOG_WRITE_INFO){
								Log.i("ValentineShutdown", "Waiting for the socket to close (" + loopCount + ")");
							}
						}
						Thread.sleep(100);
						connected = getIsConnected();
						
						loopCount ++;
					}
					
					if ( connected ){
						if(ESPLibraryLogController.LOG_WRITE_ERROR){
							Log.e("ValentineShutdown", "The Bluetooth socket did not close down");
						}
						return false;
					}
				}
			}
			catch (IOException e) 
			{
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}						
		}
		
		return true;
	}
	
	/**
	 * This method performs the actual callback for packets registered using registerForPacket
	 * 
	 * @param _callbackData - The callback information.
	 * @param _packet - The ESP packet to pass through the callback.
	 */
	private void m_doCallback(final CallbackData _callbackData, final ESPPacket _packet) 
	{
		Runnable callback = new Runnable()
		{
			public void run()
			{
				try 
				{
					if ((_callbackData != null) && (_callbackData.callBackOwner != null ) && (_callbackData.callBackOwner != null))
					{
						Class<? extends ESPPacket> packetClass = _packet.getClass();
						_callbackData.callBackOwner.getClass().getMethod(_callbackData.method, packetClass).invoke(_callbackData.callBackOwner, _packet);
					}
				} 
				catch ( InvocationTargetException e ) 
				{
		//			ValentineClient.getInstance().reportError(e.toString());
					if(ESPLibraryLogController.LOG_WRITE_INFO){
						Log.i("Valentine", _callbackData.callBackOwner.toString() + " " + _callbackData.method + " There was an invoke error calling back to owner: " + e.getTargetException().toString());
					}
					e.printStackTrace();
				} 
				catch (Exception e) 
				{
					ValentineClient.getInstance().reportError(e.toString());
					if(ESPLibraryLogController.LOG_WRITE_INFO){
						Log.i("Valentine", _callbackData.callBackOwner.toString() + " " + _callbackData.method + " There was an error calling back to owner: " + e.toString());
					}
					e.printStackTrace();
				}
			}
		};
		
		callback.run();
	}
	
	/**
	 * Send a packet to the hardware.
	 * 
	 * @param _packet - The packet to send.
	 */
	public void sendPacket(ESPPacket _packet)
	{
		if (m_inDemoMode)
		{
			m_demoData.handleDemoPacket(_packet);
		}
		else
		{
			PacketQueue.pushOutputPacketOntoQueue(_packet);
		}
	}
	
	/**
	 * Start demo mode. If a bluetooth connection exists, it will be closed.
	 * 
	 * @param _demoData - The contents of demo mode data file.
	 * @param _repeat - If true, the demo mode loop will return to the beginning of _demoData when it reached the end.
	 */
	public void startDemo(String _demoData, boolean _repeat)
	{
		stopDemo(false);
		
		try
		{
			m_inDemoMode = true;
			
			// Close the socket, but don't wait for the disconnect
			m_closeSocket (false);
			
			if (m_readerThread.isAlive())
			{
				m_readerThread.setRun(false);
				m_readerThread.interrupt();
			}
			if (m_writerThread.isAlive())
			{
				m_writerThread.setRun(false);
				m_writerThread.interrupt();
			}
		}
		catch (Exception e)
		{
			if(ESPLibraryLogController.LOG_WRITE_ERROR) {
				Log.e("ValentineESP","An error occured while interrupting the reader/writer thread. " + e.getMessage());
			}
		}
		
		m_demoFileThread = new ProcessDemoFileThread(_demoData, _repeat);
		m_demoFileThread.start();
		
		if ((m_processingThread == null) || (!m_processingThread.isAlive()))
		{
			m_processingThread = new ProcessingThread();
			m_processingThread.start();
		}
		
		// Allow a stop notification when exiting demo mode
		m_notified = false;
	}
	
	/**
	 * Stop demo mode and optionally establish a live bluetooth connection.
	 * 
	 * @param _restartLiveMode - If true, an attempt will be made to connect to the last used bluetooth device.
	 * 
	 * @return - If _restartLiveMode is true, return the result of the restart attempt, else return true.
	 */
	public boolean stopDemo( boolean _restartLiveMode)
	{
		m_inDemoMode = false;
		
		if (m_demoFileThread != null)
		{
			try 
			{
				m_demoFileThread.setRun(false);
				m_demoFileThread.interrupt();
			
				while (m_demoFileThread.isAlive())
				{
					try 
					{
						Thread.sleep(10);
					} 
					catch (InterruptedException e) 
					{
						e.printStackTrace();
					}
				}
			} 
			catch (Exception e1) 
			{
				
			}
		}
			
		m_demoFileThread = null;
		
		if (_restartLiveMode)
		{
			if (m_pairedBluetoothDevice != null)
			{
				return startUp(m_pairedBluetoothDevice);
			}
		}
		
		 
		return true;
	}

	/**
	 * Class to hold the callback data.
	 *
	 */
	public class CallbackData
	{
		public Object callBackOwner;
		public String method;
	}
	
	/** 
	 * Class to hold information about callbacks that are waiting to be deregistered
	 */
	public class DeregCallbackInfo
	{
		public Object callBackOwner;	
		public PacketId type;
		public String method;
	}
	
	/**
	 * The ProcessingThread class is responsible for processing all packets received from the ESP bus.
	 *
	 */
	private class ProcessingThread extends Thread
	{
		private boolean m_run;
		
		/**
		 * Sets or clears the flag to keep the thread running.
		 * @param _run - Set to true to keep the thread running, set to false to stop the thread.
		 */
		public void setRun(boolean _run)
		{
			m_run = _run;
		}
		
		/**
		 * ProcessingThread constructor.
		 */
		public ProcessingThread()
		{
			m_run = true;
		}
		
		/**
		 * This is the actual thread execution method.
		 */
		public void run()
		{
			while (m_run)
			{
				try
				{
					final ESPPacket packet = PacketQueue.getNextInputPacket();
					
					if (packet == null)
					{
						// No packets, so hang out for a while.
						Thread.sleep(100);
					}
					else
					{
						if (m_callbackData.containsKey(packet.getPacketIdentifier()))
						{
							// Do not allow deregistering packets while iterating through this list because that will
							// cause a structural change to the list, which should not be done while iterating through
							// the list.							
							ValentineESP.this.setLockedPacket( packet.getPacketIdentifier() );
							
							final ArrayList<CallbackData> list = m_callbackData.get(packet.getPacketIdentifier());
							
							for (int i = 0; i < list.size(); i++)
							{
								final CallbackData data = list.get(i);
								
								if (data == null)
								{

								}
								else
								{
									new Runnable()
									{
										public void run()
										{
											m_doCallback(data, packet);
										}
									}.run();
								}
							}
						}
						
						// Allow registering and deregistering after we are done processing this packet
						ValentineESP.this.setLockedPacket( PacketId.unknownPacketType );						
					}
				} 
				catch (Exception e) 
				{
					m_run = false;
				} 
			}
		}
	};
	
	/**
	 * This class will procss the demo mode file contents as if they were ESP data.
	 *
	 */
	private class ProcessDemoFileThread extends Thread
	{
		private boolean m_run;
		private String m_demoFileStream;
		private boolean m_repeat;
		
		/**
		 * Sets or clears the flag to keep the thread running.
		 * @param _run - Set to true to keep the thread running, set to false to stop the thread.
		 */
		public void setRun(boolean _run)
		{
			m_run = _run;
		}
		
		/**
		 * ProcessDemoFileThread constuctor.
		 * 
		 * @param _demoFileContents - The contents of the demo mode file that will be processed as ESP data.
		 * @param _repeat - If true, the demo mode loop will return to the beginning of _demoData when it reached the end.
		 */
		public ProcessDemoFileThread(String _demoFileContents, boolean _repeat)
		{
			m_run = true;
			m_demoFileStream = _demoFileContents;
			m_repeat = _repeat;
		}

		/**
		 * This is the actual thread execution method.
		 */
		public void run()
		{
			int current = 0;
			boolean process = true;
			while (m_run)
			{
				try
				{
					do
					{
						int eolnLocation = m_demoFileStream.indexOf("\n", current);
						String currentByteStream = m_demoFileStream.substring(current, eolnLocation);
						current = eolnLocation + 1;
						
						String specialTest = currentByteStream.substring(0, 2);
						if (specialTest.equals("//"))
						{
							//do nothing
						}
						else if (specialTest.charAt(0) == '<')
						{
							String notification = "";
							int notificationStartLocation = currentByteStream.indexOf(":");
							int notificationStopLocation = currentByteStream.indexOf(">");
							
							notification = currentByteStream.substring(notificationStartLocation+1, notificationStopLocation);
							
							ValentineClient.getInstance().doNotification(notification);
						}
						else
						{
							ArrayList<Byte> byteBuffer = m_makeByteBufferFromString(currentByteStream);
							ESPPacket newPacket = ESPPacket.makeFromBuffer(byteBuffer);

                            if(newPacket != null)
    							m_demoData.handleDemoPacket(newPacket);
							
							sleep(68);
						}
						
						if (current == m_demoFileStream.length())
						{
							if (m_repeat)
							{
								current = 0;
							}
							else
							{
								process = false;
							}
						}
					}
					while (process);
				}
				catch (Exception e)
				{
					m_run = false;
				}
			}

            // we notify the stop
            broadcastV1Event(ValentineClient.V1_DEMO_END);
		}
		
		/**
		 * This method will convert a string of space delimited, 2 character strings into the hex bytes the strings represent. 
		 * @param _data - The string data to convert.
		 * @return - An array of bytes.
		 */
		private ArrayList<Byte> m_makeByteBufferFromString(String _data)
		{
			ArrayList<Byte> rc = new ArrayList<Byte>();
			String[] split = _data.split(" ");
			
			for (int i = 0; i < split.length; i++)
			{
				String item = split[i];
				byte temp = (byte) ((Character.digit(item.charAt(0), 16) << 4) + Character.digit(item.charAt(1), 16));
				rc.add(temp);
			}
			return rc;
		}
	};
	
	/**
	 * This method will remove all callbacks set up using registerForCallbacks.
	 */
	public void clearAllCallbacks()
	{
		m_packetCallbackLock.lock();
		if ( m_lockedPacket.toByteValue() == PacketId.unknownPacketType.toByteValue() ){
		// We don't have a locked packet, so just clear everything
			if ( ESPLibraryLogController.LOG_WRITE_VERBOSE ){
	    		Log.v("ValentineESP", "Clearing " + m_callbackData.size() + " callbacks");
			}			
			
			m_callbackData.clear();
			m_clearAllCallbacksOnUnlock = false;
		}
		else{		
		// We have a locked packet type, so set the flag to clear all packets when we unlock the current packet type
			m_clearAllCallbacksOnUnlock = true; 
		}
		
		// Clear the pending queues while we are locked
		m_packetsToDeregister.clear();
		m_packetsToRegister.clear();
		
		m_packetCallbackLock.unlock();
	}
	
	/**
	 * This method will clear all callbacks set up using registerForCallbacks for the packet id passed in
	 *  
	 * @param _type - The packet id to deregister.
	 */
	public void clearCallbacks(PacketId _type)
	{
		// Keep this locked for the duration of this method 
		m_packetCallbackLock.lock();
		
		for (Iterator<Entry<PacketId, ArrayList<CallbackData>>> it = m_callbackData.entrySet().iterator(); it.hasNext();) 
		{ 
		    Map.Entry<PacketId, ArrayList<CallbackData>> pairs = (Map.Entry<PacketId, ArrayList<CallbackData>>)it.next();
		    
		    if (pairs.getKey() == _type)
		    {
		    	if ( _type.toByteValue() != m_lockedPacket.toByteValue() ){
		    		if ( ESPLibraryLogController.LOG_WRITE_VERBOSE ){
		        		Log.v("ValentineESP", "Clearing all callbacks for packet id " + _type.toString());
		    		}
		    		
		    		it.remove();
		    		break;
		    	}
		    	else{
		    		// Add all callbacks of this packet type to the pending degistration queue
		    		for ( int i = 0; i < pairs.getValue().size(); i++ ){
		    			DeregCallbackInfo info = new DeregCallbackInfo();
		    			info.callBackOwner = pairs.getValue().get(i);
		    			info.type = pairs.getKey();
		    			info.method = "";
		    			m_packetsToDeregister.add(info);
		    		}
		    	}
		    }
		} 
		
		m_packetCallbackLock.unlock();
		
	}
		
	/** Register a function to bl;e notified if the ESP client has not received any data from the Valentine One after 5 seconds.
	 * Example case of this being called, the valentine one has been turned off for more than 5 seconds.
	 * Requires a function with a String parameter:  public void function( String _parameter)
	 *  
	 *  Only one Notification callback is allowed at a time, registering a second will overwrite the first
	 *  
	 * @param _owner The object with the function to be notified when there is no data from the Valentine One
	 * @param _function The function to call
	 */
	public void registerForNoDataNotification(Object _noDataObject, String _noDataFunction)
	{
		// Note that this type of callback does not use the callback data list, so it is safe to modify it here.		
		
		m_noDataObject = _noDataObject;
		m_noDataFunction = _noDataFunction;
	}
	
	/**
	 * Deregister callbacks set up using registerForNoDataNotification.
	 */
	public void deregisterForNoDataNotification()
	{
		// Note that this type of callback does not use the callback data list, so it is safe to modify it here.		
		m_noDataObject = null;
		m_noDataFunction = null;
	}
	
	/**
	 * Do the actual callback set up using registerForNoDataNotification
	 */
	public void notifyNoData()
	{
        // added by Francky
        broadcastV1Event(ValentineClient.V1_NO_DATA);

		if ((m_noDataObject != null) && m_noDataFunction != null)
		{
			Utilities.doCallback(m_noDataObject, m_noDataFunction, String.class, "No Data Received");
		}
	}
	
	/**
	 * Return the last demo mode data file contents loaded in.
	 * @return The last demo mode data file contents loaded in.
	 */	
	public DemoData getDemoData()
	{
		return m_demoData;
	}
	
	/**
	 * This method will attempt to connect to the current bluetooth device after the last connect attempt failed.
	 * 
	 * @return true if a connection was established, else false.
	 */
	private boolean m_attempt2()
	{
		Method m;
		try 
		{
			m = m_pairedBluetoothDevice.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
			m_socket = (BluetoothSocket) m.invoke(m_pairedBluetoothDevice, 1); 
			m_socket.connect();
			
			m_inputStream = m_socket.getInputStream();
			m_outputStream = m_socket.getOutputStream();
			
			m_readerThread = new DataReaderThread(this, m_inputStream, m_secondsToWait);
			m_writerThread = new DataWriterThread(this, m_outputStream);
			
			return true;
		} 
		catch (SecurityException e) 
		{
			e.printStackTrace();
		} 
		catch (NoSuchMethodException e) 
		{
			e.printStackTrace();
		} 
		catch (IllegalArgumentException e) 
		{
			e.printStackTrace();
		} 
		catch (IllegalAccessException e) 
		{
			e.printStackTrace();
		} 
		catch (InvocationTargetException e) 
		{
			e.printStackTrace();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
		if ((m_unsupportedObject != null) && (m_unsupportedFunction != null))
		{
			Utilities.doCallback(m_unsupportedObject, m_unsupportedFunction, String.class, "Unsupported Device");
		}
		
		return false;
	}

	/**
	 * This method will allow the caller to determine if this object is currently set up to process ESP data.
	 * @return - true if this object is currently set up to process ESP data, else false.
	 */
	public boolean isRunning() 
	{
		if (m_processingThread == null)
		{
			return false;
		}
		return m_processingThread.isAlive();
	}
	
	/**
	 * This method will allow the caller to determine if this object is currently set up to process ESP data.
	 * 
	 * @return - The current bluetooth socket.
	 */
	public BluetoothSocket getSocket() 
	{
		return m_socket;
	}
}
