package com.valentine.esp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.ESPLibraryLogController;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.data.InfDisplayInfoData;
import com.valentine.esp.data.SavvyStatus;
import com.valentine.esp.data.SweepDefinition;
import com.valentine.esp.data.SweepSection;
import com.valentine.esp.data.UserSettings;
import com.valentine.esp.packets.InfDisplayData;
import com.valentine.esp.packets.request.RequestBatteryVoltage;
import com.valentine.esp.packets.request.RequestChangeMode;
import com.valentine.esp.packets.request.RequestFactoryDefault;
import com.valentine.esp.packets.request.RequestMaxSweepIndex;
import com.valentine.esp.packets.request.RequestMuteOff;
import com.valentine.esp.packets.request.RequestMuteOn;
import com.valentine.esp.packets.request.RequestOverrideThumbwheel;
import com.valentine.esp.packets.request.RequestSavvyStatus;
import com.valentine.esp.packets.request.RequestSerialNumber;
import com.valentine.esp.packets.request.RequestSetSavvyUnmute;
import com.valentine.esp.packets.request.RequestSetSweepsToDefault;
import com.valentine.esp.packets.request.RequestStartAlertData;
import com.valentine.esp.packets.request.RequestStopAlertData;
import com.valentine.esp.packets.request.RequestSweepSections;
import com.valentine.esp.packets.request.RequestTurnOffMainDisplay;
import com.valentine.esp.packets.request.RequestTurnOnMainDisplay;
import com.valentine.esp.packets.request.RequestUserBytes;
import com.valentine.esp.packets.request.RequestVehicleSpeed;
import com.valentine.esp.packets.request.RequestVersion;
import com.valentine.esp.packets.request.RequestWriteUserBytes;
import com.valentine.esp.packets.response.ResponseBatteryVoltage;
import com.valentine.esp.packets.response.ResponseDataError;
import com.valentine.esp.packets.response.ResponseMaxSweepIndex;
import com.valentine.esp.packets.response.ResponseRequestNotProcessed;
import com.valentine.esp.packets.response.ResponseSavvyStatus;
import com.valentine.esp.packets.response.ResponseSerialNumber;
import com.valentine.esp.packets.response.ResponseSweepSections;
import com.valentine.esp.packets.response.ResponseUnsupported;
import com.valentine.esp.packets.response.ResponseUserBytes;
import com.valentine.esp.packets.response.ResponseVehicleSpeed;
import com.valentine.esp.packets.response.ResponseVersion;
import com.valentine.esp.statemachines.GetAlertData;
import com.valentine.esp.statemachines.GetAllSweeps;
import com.valentine.esp.statemachines.WriteCustomSweeps;
import com.valentine.esp.utilities.Utilities;
import com.valentine.esp.utilities.V1VersionSettingLookup;

/** This is the class that handles all the interfacing with the Valentine One.  All the calls to the device are done through this class.
 * It will wrap all the handling and unpacking of packets and handling the registering and deregistering for packets leaving the user to use the data 
 * structures in the data package to handle the data.   Only create one instance of this class at a time, so do not create multiple instances of
 * this class.  Put this in your application class and save the instance there.
 */
public class ValentineClient 
{
    /* Added by Francky, Event to local BroadCast */
    public static final int V1_NO_DATA        = 1;
    public static final int V1_DEMO_END       = 2;
    public static final int V1_STOP           = 4;
    public static final int V1_ESP_CONNECTED  = 8;

	private static final String LOG_TAG = "ValentineESP/ValentineClient";
	private static final int MAX_INDEX_NOT_READ = -1;
	BluetoothDevice   m_bluetoothDevice;
	ValentineESP      m_valentineESP;
	SharedPreferences m_preferences;
	String            m_bluetoothAddress;
	V1VersionSettingLookup m_settingLookup;
	
	Object m_errorCallbackObject;
	String m_errorCallbackFunction;
	

	Context m_context;

	public Devices m_valentineType;
	
	private Devices m_lastV1Type = Devices.UNKNOWN;
	private int m_v1TypeChangeCnt = 0;
	private final int V1_TYPE_SWITCH_THRESHOLD = 8;

	private Map<Devices, Object> m_versionCallbackObject;
	private Map<Devices, String> m_versionCallbackFunction;
	private Map<Devices, Object> m_serialNumberCallbackObject;
	private Map<Devices, String> m_serialNumberCallbackFunction;
	private Object               m_userBytesCallbackObject;
	private String               m_userBytesCallbackFunction;
	private Object               m_voltageCallbackObject;
	private String               m_voltageCallbackFunction;
	private Object               m_savvyStatusObject;
	private String               m_savvyStatusFunction;
	private Object               m_vehicleSpeedObject;
	private String               m_vehicleSpeedFunction;
	private Object               m_getSweepsObject;
	private String               m_getSweepsFunction;
	private Object               m_getMaxSweepIndexObject;
	private String               m_getMaxSweepIndexFunction;

	private GetAllSweeps      m_getAllSweepsMachine;
	private WriteCustomSweeps m_writeCustomSweepsMachine;
	private ConcurrentHashMap<Object, GetAlertData> m_getAlertDataMachineMap = new ConcurrentHashMap<Object, GetAlertData>();

	private ConcurrentHashMap<Object, String> m_infCallbackCallbackData;

	private Object m_notificationObject;
	private String m_notificationFunction;

	private static ValentineClient m_instance;

	private Object m_stopObject;
	private String m_stopFunction;

	private Object m_unsupportedDeviceObject;
	private String m_unsupportedDeviceFunction;

	private Object m_unsupportedPacketObject;
	private String m_unsupportedPacketFunction;
	private Object m_requestNotProcessedObject;
	private String m_requestNotProcessedFunction;
	private Object m_dataErrorObject;
	private String m_dataErrorFunction;
	private Object m_dataErrorObjectRaw;
	private String m_dataErrorFunctionRaw;
	
	private ArrayList<SweepSection> m_sweepSections;
	private int m_maxSweepIndex = -1;
	
	private String m_lastV1ConnVer;
	
	/**
	 * Create a receiver to handle specific Bluetooth connection events. 
	 */
	private final BroadcastReceiver m_receiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
	        
	        if(device == null){
	        	return;
	        }
	        
	        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
	        	if(ESPLibraryLogController.LOG_WRITE_DEBUG){
	        		Log.d("ESP", "ACTION_ACL_CONNECTED");
	        	}
	        	if(m_bluetoothDevice != null){
		            if (device.getAddress().equalsIgnoreCase(m_bluetoothDevice.getAddress())) {
		            	// Tell the ESP object that we are connected
		        	    m_valentineESP.setIsConnected(true);
                        m_valentineESP.broadcastV1Event(V1_ESP_CONNECTED, true);
		            }
	        	}
	        }
	        else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
	        	if(ESPLibraryLogController.LOG_WRITE_DEBUG){
	        		Log.d("ESP", "ACTION_ACL_DISCONNECT_REQUESTED");
	        	}
	        }
	        else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
	        	if(ESPLibraryLogController.LOG_WRITE_DEBUG){
	        		Log.d("ESP", "ACTION_ACL_DISCONNECTED");
	        	}
	        	if(m_bluetoothDevice != null){
		            if (device.getAddress().equalsIgnoreCase(m_bluetoothDevice.getAddress())) {
		            	// Tell the ESP object that we have been disconnected.
		            	m_valentineESP.setIsConnected(false);
                        m_valentineESP.broadcastV1Event(V1_ESP_CONNECTED, false);
		            }
	        	}
	        }          
	    }
	};
	
	/**
	 * Constructor the ValentineClient class.
	 * 
	 * @param _context The context to use for this object.
	 */
	public ValentineClient(Context _context) {
		// Initializes the library with the default timeout supplied.
		init(_context, 10);
	}
	
	public ValentineClient(Context _context, int secondsToWait) {
		// Initializes the library with the supplied timeout.
		init(_context, secondsToWait);
	}

    /**
     * Added By Francky, called by ESP to broadcat Event
     *
     */

    // set all events in one go, this will replace the current broadcasted events

    public void setBroadcastEvents(int event)
    {
        m_valentineESP.setBroadcastEvents(event);
    }

    // set on/off a single event
    public void setBroadcastEvent(int event, boolean onOff)
    {
        m_valentineESP.setBroadcastEvent(event, onOff);
    }

    // cleat all events
    public void clearBroadcastEvents()
    {
        m_valentineESP.clearBroadcastEvents();
    }

    // retrieve the events broadcasted

    public int getBroadcastEvent()
    {
        return m_valentineESP.getBroadcastEvent();
    }

	/**
	 * Initializes the library.
	 * 
	 * @param _context 	 		Context needed to register broadcast listeners and retrieve the SharedPreferences.
	 * @param secondsToWait		The seconds to wait before the data reader thread notifies the library of no data. To be passed in in seconds.
	 */
	public void init(Context _context, int secondsToWait) {
		m_instance = this;
		m_context = _context;		
		m_valentineESP = new ValentineESP(secondsToWait);
		m_settingLookup = new V1VersionSettingLookup();
		m_lastV1ConnVer = "";
		
		// Initialize the callback maps
		m_versionCallbackObject = new HashMap<Devices, Object>();
		m_versionCallbackFunction = new HashMap<Devices, String>();
		m_serialNumberCallbackObject = new HashMap<Devices, Object>();
		m_serialNumberCallbackFunction = new HashMap<Devices, String>();
		m_infCallbackCallbackData = new ConcurrentHashMap<Object, String>();    	
		
		// Set up the callbacks used within this object.
		registerLocalCallbacks();

		m_preferences = m_context.getSharedPreferences("com.valentine.esp.savedData", Context.MODE_PRIVATE);

		m_bluetoothAddress = m_preferences.getString("com.valentine.esp.LastBlueToothConnectedDevice", "");

		if (m_bluetoothAddress != "") {
			Set<BluetoothDevice> pairedDevices;
			pairedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice bd : pairedDevices) {
					String test = bd.getAddress();
					if (test.equals(m_bluetoothAddress)) {
						m_bluetoothDevice = bd;
					}
				}
			}
		}
		m_valentineType = Devices.UNKNOWN;
    	
   		IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
   		IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
   		IntentFilter filter3 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
   		m_context.registerReceiver(m_receiver, filter1);
   		m_context.registerReceiver(m_receiver, filter2);
   		m_context.registerReceiver(m_receiver, filter3);

        // added by Francky
        m_valentineESP.setBroadcastContext(m_context);
	}

    /**
     * Added by Francky, enable the BT Work Around
     *
     */
	public void enableBtWorkAround(boolean bt)
    {
        m_valentineESP.enableBtWorkAround(bt);
    }
	/** 
	 * Returns if the connected Valentine One is a legacy device.
	 * 
	 * @return True if the device is a legacy Valentine One, false otherwise. 
	 */
	public boolean isLegacyMode()
	{
		if (m_valentineType == Devices.VALENTINE1_LEGACY)
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	/**
	 * Register the callbacks used by this object. 
	 */
	private void registerLocalCallbacks()
	{
		// There can only be one unsupported packet callback so we don't need to see if we are already registered.
		m_valentineESP.setUnsupportedCallbacks(this, "unsupportedDeviceCallback");

		if ( !m_valentineESP.isRegisteredForPacket(PacketId.respUnsupportedPacket, this) ){
			m_valentineESP.registerForPacket(PacketId.respUnsupportedPacket, this, "unsupportedPacketCallback");
		}
		
		if ( !m_valentineESP.isRegisteredForPacket(PacketId.respRequestNotProcessed, this) ){
			m_valentineESP.registerForPacket(PacketId.respRequestNotProcessed, this, "RequestNotProcessedCallback");
		}
		if ( !m_valentineESP.isRegisteredForPacket(PacketId.respDataError, this) ){
			m_valentineESP.registerForPacket(PacketId.respDataError, this, "dataErrorCallback");
		}

		if ( !m_valentineESP.isRegisteredForPacket(PacketId.infDisplayData, this) ){
			m_valentineESP.registerForPacket(PacketId.infDisplayData, this, "infDisplayCallback");
		}		
	}
	
	/**
	 * Check to see if there is an open Bluetooth connection to the V1connection.
	 *  
	 * @return true if there is an open Bluetooth connection to the V1connection, else false.
	 */
	public boolean isConnected()
	{
		return m_valentineESP.getIsConnected();
	}
	
	/**
	 * Returns the last V1connection version received
	 * 
	 * @return - the last V1connection version received
	 */
	public String getCachedV1connectionVersion()
	{
		return m_lastV1ConnVer;
	}
	
	/** 
	 * Returns the last set of sweep sections returned from the Valentine One.
	 * 
	 * @return The last set of SweepSections returned from the device.
	 */
	public ArrayList<SweepSection> getCachedSweepSections()
	{
		ArrayList<SweepSection> retArray;
		if ( m_valentineESP.isInDemoMode() ){
			// We are in demo mode, so use the demo default sweep sections
			retArray = m_settingLookup.getV1DefaultSweepSections();
		}
		else if ( m_sweepSections == null || m_sweepSections.size() == 0 ){
			// We are not in demo mode but we have not read the sweep section data from the V1 yet, so use
			// the 3.8920 default values.
			retArray = m_settingLookup.getV1DefaultSweepSections();
		}
		else{
			// We are not in demo mode and we have read the sweep sections from the V1, so use the values from the V1.
			// If we've reached this point we don't need to do anything but exit to the return statement below.
			retArray = m_sweepSections;
		}
		
		return retArray;
	}
	
	/** 
	 * Sets the cached sweep sections for the custom sweeps.
	 * 
	 * @param _sections the sweep sections to set.
	 */
	public void setCachedSweepSections(ArrayList<SweepSection> _sections)
	{
		m_sweepSections = _sections;
	}
	
	/** 
	 * Returns the the last known max sweep index for the custom sweeps.
	 * 
	 * @return The max index of the custom sweeps.
	 */
	public int getCachedMaxSweepIndex()
	{
		int retVal;
		
		if ( m_valentineESP.isInDemoMode() ){
			// We are in demo mode, so use the V3.8920 default max sweep index
			retVal = m_settingLookup.getV1DefaultMaxSweepIndex();
		}
		else if ( m_maxSweepIndex == MAX_INDEX_NOT_READ ){
			// We are not in demo mode but we have not read the max sweep index from the V1 yet, so use
			// the 3.8920 max sweep index.
			retVal = m_settingLookup.getV1DefaultMaxSweepIndex();
		}
		else{
			// We are not in demo mode and we have read the max sweep index from the V1, so use the values from the V1.
			// If we've reached this point we don't need to do anything but exit to the return statement below.
			retVal = m_maxSweepIndex;
		}
		
		return retVal;
	}
	
	/** 
	 * Sets the cached max sweep index for custom sweeps.
	 * 
	 * @param _maxIndex the index of the max index for custom sweeps.
	 */
	public void setCachedMaxSweepIndex(Integer _maxIndex)
	{
		m_maxSweepIndex = _maxIndex;
	}
	
	/**
	 * This method will clear the cache of sweep definition information read from the V1.
	 */
	public void clearSweepCache() 
	{
		if ( m_sweepSections != null ){
			m_sweepSections.clear();
		}
		m_maxSweepIndex = MAX_INDEX_NOT_READ;
	}
	
	/**
	 *  Returns the current instance of the ValentineClient, only one ValentineClient should exist.
	 * 
	 * @return The instance of the ValentineClient.
	 */
	public static ValentineClient getInstance()
	{
		return m_instance;
	}
	
	/** 
	 * Sets the function and object to handle errors.
	 * 
	 * @param _errorHandlerObject The object that has the function on it to handle errors.
	 * @param _errorHandlerFunction The function to call when there is an error.
	 */
	public void setErrorHandler(Object _errorHandlerObject, String _errorHandlerFunction)
	{
		m_errorCallbackObject = _errorHandlerObject;
		m_errorCallbackFunction = _errorHandlerFunction;
	}
	
	/** 
	 * Calls the registered Error Handler callback to handle the given error
	 * 
	 * @param _error The error that happened that needs to be passed to the callback function to handle errors.
	 */
	public void reportError(String _error)
	{
		if(ESPLibraryLogController.LOG_WRITE_WARNING){
			Log.w("Valentine",_error);
		}
		if ((m_errorCallbackObject != null ) && (m_errorCallbackFunction != null))
		{
			Utilities.doCallback(m_errorCallbackObject, m_errorCallbackFunction, String.class, _error);
		}
	}
	
	/** 
	 * Used to check if we have connected to a bluetooth device previously.
	 * 
	 * @return True of we have connected to a previous bluetooth device, false if we have not.
	 */
	public boolean havePreviousDevice()
	{
		if (m_bluetoothDevice != null)
		{
			return true;
		}
		else 
		{
			return false;
		}
	}

	/**
	 * Gets the current Bluetooth Device.
	 * 
	 * @return Current Bluetooth Device.
	 */
	public BluetoothDevice getDevice() {
		return m_bluetoothDevice;
	}
	
	/**
	 * Checks to see if the connection to the Bluetooth is up and the ValentineESP class is reading and
	 * writing to the bluetooth connection.
	 * 
	 * @return boolean true data is flowing back and forth, false otherwise.
	 */
	public boolean isRunning()
	{
		return m_valentineESP.isRunning();
	}

	public boolean StartUp() {
		return StartUp(null);
	}
	
	/** 
	 * Starts up the client and connection to the Valentine One with the given BlueTooth device.
	 * 
	 * @param _device The BlueTooth device to connect to and to try to communicate with the Valentine One.
	 * @return true if it started up correctly.
	 */
	public boolean StartUp(BluetoothDevice _device)
	{
		stopDemoMode(false);
		
		// Start the callbacks into the local methods
		registerLocalCallbacks();
		
		// Force an unknown V1 type until we get the infDisplayData packet from the V1.
		m_valentineType = Devices.UNKNOWN;
		m_lastV1Type = Devices.UNKNOWN;
		m_v1TypeChangeCnt = 0;
		
		// Do not allow writing to the V1 type is known
		PacketQueue.initOutputQueue(Devices.UNKNOWN, false, true);
		PacketQueue.initInputQueue (true);
		
		boolean newDevice = false;
		
		if (m_bluetoothDevice == null)
		{
			newDevice = true;
		}
		else if ((_device != null) && (!m_bluetoothDevice.getAddress().equals(_device.getAddress())))
		{
			newDevice = true;
		}
		
		if (newDevice)
		{
			Editor edit = m_preferences.edit();
			
			m_bluetoothDevice = _device;
			
			if (m_bluetoothDevice != null)
		    {
				edit.putString("com.valentine.esp.LastBlueToothConnectedDevice", m_bluetoothDevice.getAddress());
				edit.commit();
				
		    	if (m_valentineESP.startUp(m_bluetoothDevice))
		    	{
		    		m_versionCallbackObject.clear();
		    		m_versionCallbackFunction.clear();
		    		m_serialNumberCallbackObject.clear();
		    		m_serialNumberCallbackFunction.clear();
		    		//m_infCallbackCallbackData.clear();
		    		doSearch();
		    		return true;
		    	}
		    	
		    	return false;
		    }
				
			return false;
		}
		else
		{	
			if (m_valentineESP.startUp(m_bluetoothDevice))
			{
				m_versionCallbackObject.clear();
	    		m_versionCallbackFunction.clear();
	    		m_serialNumberCallbackObject.clear();
	    		m_serialNumberCallbackFunction.clear();
	    		//m_infCallbackCallbackData.clear();
				doSearch();
				
				return true;
			}
			
			return false;
		}
	}
	
	
	/** 
	 * Shuts down the client and disconnects from the connected bluetooth device and stops the demo mode if its running.
	 */
	public void Shutdown()
	{
        clearBroadcastEvents();

		if (isLibraryInDemoMode())
		{
			stopDemoMode(false);
		}

		m_valentineESP.stop();
		
		if(ESPLibraryLogController.LOG_WRITE_VERBOSE){
			Log.v("STOPPING", "Turning off all callbacks");
		}
		clearAllCallbacks();
	}
	
	/**
	 * Checks to see if the library is in 'Demo' mode.
	 * 
	 * @return true if the library is in 'Demo' mode, otherwise false.
	 */
	public boolean isLibraryInDemoMode() {
		return m_valentineESP.isInDemoMode();
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
		m_valentineESP.setProtectLegacyMode(val);
	}
		
	/**
	 * Get the current state of Legacy Mode protection provided by the ESP library. Refer to 
	 * setProtectLegacyMode for more information on this feature. 
	 * 
	 * @return The current state of Legacy Mode protection provided by the ESP library.
	 */
	public boolean getProtectLegacyMode ()
	{
		return m_valentineESP.getProtectLegacyMode();
	}
	
	
	/** 
	 * Sets up the Valentine One discovery mechanism.  This is now done through the infDisplayDataData received from the Valentine One. 
	 */
	private void doSearch()
	{
		m_valentineESP.registerForPacket(PacketId.infDisplayData, this, "doSearchCallback");
	}
	
	/**
	 * This is the Callback registered with the system to determine which of the Valentine One types we have.  This is updated each time we 
	 * get a InfDisplayData packet response.
	 * 
	 * @param _resp The display data to tell us what type of Valentine One we have.
	 */
	public void doSearchCallback(InfDisplayData _resp)
	{
		InfDisplayInfoData data = (InfDisplayInfoData)_resp.getResponseData();
		
		Devices devType = Devices.UNKNOWN;
		
		if (data.getAuxData().getLegacy())
		{
			devType = Devices.VALENTINE1_LEGACY;
		}
		else if (_resp.getOrigin() == Devices.VALENTINE1_WITH_CHECKSUM)
		{
			devType = Devices.VALENTINE1_WITH_CHECKSUM;
		}
		else if (_resp.getOrigin() == Devices.VALENTINE1_WITHOUT_CHECKSUM)
		{
			devType = Devices.VALENTINE1_WITHOUT_CHECKSUM;
		}
		
		if ( devType != m_valentineType ){
			// Possibly change the device type
			if ( devType == m_lastV1Type ){
				// This is the same as the last device type we found last time. This could be a possible V1 type change.
				m_v1TypeChangeCnt ++;
			}
			else{
				// This is the first time we received this V1 type. Initialize the switch variables.
				m_v1TypeChangeCnt = 1;
				m_lastV1Type = devType;
			}
			
			if ( m_v1TypeChangeCnt >= V1_TYPE_SWITCH_THRESHOLD ){
				// Change the V1 type and tell the packet queue what type of V1 to use.
				if( ESPLibraryLogController.LOG_WRITE_INFO ){
					Log.i( LOG_TAG, "Changing V1 type from " + m_valentineType.toString() + " to " + devType.toString() );
				}
				
				m_valentineType = devType;
				PacketQueue.setNewV1Type(m_valentineType);
				
				// Always request the V1connection version whenever we change the V1 type
				getV1connectionVerison();
				
				// Reset the switch variables to prevent a quick switch next time
				m_lastV1Type = Devices.UNKNOWN;
				m_v1TypeChangeCnt = 0;
			}
		}
		else{
			// The V1 type is not changing
			m_lastV1Type = Devices.UNKNOWN;
			m_v1TypeChangeCnt = 0;
		}
		
		if( PacketQueue.getV1Type() != Devices.UNKNOWN ){
			// Use the TS Holdoff bit to hold off or allow requests
			PacketQueue.setHoldoffOutput(data.getAuxData().getTSHoldOff());
		}	
	}
	
	/** 
	 * This registers an object/function combination to be notified when the ESP client stops.  Only one allowed at a time
	 * Requires a function with a Void parameter:  public void function( Void _parameter).
	 *  
	 * @param _stopObject  Object with the function to be called
	 * @param _stopFunction  Function to be called
	 */
	public void registerStopNotification(Object _stopObject, String _stopFunction)
	{
		if ( ESPLibraryLogController.LOG_WRITE_INFO ){
			Log.i ("ValentineClient", "Register stop notifcation for " + _stopObject);
		}
		
		m_stopObject = _stopObject;
		m_stopFunction = _stopFunction;
		
		m_valentineESP.registerForStopNotification(this, "handleStopNotification");
	}
	
	/** 
	 * Removes the registered stop notification callback information.
	 */
	public void deregisterStopNotification(Object _stopObject)
	{
		if ( _stopObject == m_stopObject ){
			// Only deregister if called from the registered object.
			if(ESPLibraryLogController.LOG_WRITE_INFO){
				Log.i("ValentineClient", "Deregistering stop notification");	
			}
			m_stopObject = null;
			m_stopFunction = null;
			m_valentineESP.deregisterForStopNotification();
		}
	}
	
	/** 
	 * This is the call back from the ESP client to call. Don't call this directly.
	 */
	public void handleStopNotification(Boolean _rc)
	{
		if ((m_stopObject != null) && (m_stopFunction != null)) {
			Utilities.doCallback(m_stopObject, m_stopFunction, Void.class, null);
		}
	}
	
	//***//
	
	/**
	 * Registers a callback to handle InfDisplayInfoData data structures from the Valentine One.  Many can be registered.
	 * Requires a function with a InfDisplayInfoData parameter:  public void function( InfDisplayInfoData _parameter).
	 * 
	 * @param _callbackObject Object with the function to be called.
	 * @param _function Function on the object to be called.
	 */
	public void registerForDisplayData(Object _callbackObject, String _function)
	{
		if(m_infCallbackCallbackData.containsKey(_callbackObject)) {
			m_infCallbackCallbackData.remove(_callbackObject);
		}
		
		m_infCallbackCallbackData.put(_callbackObject, _function);
	}
	
	/** 
	 * Registers a callback to handle an ArrayList of AlertData data structures from the Valentine One.  One can be registered. 
	 * Does not start the flow of the alert data, that requires a call to sendAlertData.
	 * Requires a function with a ArrayList<AlertData> parameter:  public void function( ArrayList<AlertData> _parameter).
	 * 
	 * @param _callbackObject Object with the function to be called.
	 * @param _function Function on the object to be called.
	 */
	public void registerForAlertData(Object _callbackObject, String _function) 
	{
		if (m_getAlertDataMachineMap.containsKey(_callbackObject)) {
			//this callback object was previously registered, de-register it and remove it from the map
			m_getAlertDataMachineMap.get(_callbackObject).stop();
			m_getAlertDataMachineMap.remove(_callbackObject);
		}

		GetAlertData newSubscriber = new GetAlertData(m_valentineESP, _callbackObject, _function);
		m_getAlertDataMachineMap.put(_callbackObject, newSubscriber);
		newSubscriber.start();
	}


	/**
	 * This is the callback from the ESP client to handle InfDisplayData coming from the Valentine One, it converts the response packet
	 * into InfDisplayInfoData data and sends that on to the list of registered functions. 
	 * Do not call this directly.
	 * 
	 * @param _resp The InfDisplayData packet from the Valentine One.
	 */
	public void infDisplayCallback(InfDisplayData _resp)
	{
		Set<Object> keys = m_infCallbackCallbackData.keySet();
	
		for (Object o : keys)
		{
			String function = m_infCallbackCallbackData.get(o);
			try {
				Utilities.doCallback(o, function, String.class, (InfDisplayInfoData)_resp.getResponseData());
			} 
			catch(Exception e) {
				if(ESPLibraryLogController.LOG_WRITE_WARNING ){
					Log.w(LOG_TAG, o.toString() + " " + function);
				}
			}
		}
	}
	
	/**
	 * Removes the callback for the given object so it will no longer receive InfDisplayInfoData data structures to process.
	 * 
	 * @param _source The object which to stop getting InfDisplayInfoData from.
	 */
	public void deregisterForDisplayData(Object _source)
	{
		m_infCallbackCallbackData.remove(_source);

		
		if(ESPLibraryLogController.LOG_WRITE_DEBUG){
			Log.d(LOG_TAG, String.format("Got deregisterForDisplayData requested for %s", _source.toString()));// System.identityHashCode(_source)));
		}
	}
	
	/**
	 * Stops the processing of the alert data from the Valentine One. 
	 */
	public void deregisterForAlertData(Object _originalCallbackObject)
	{
		if (m_getAlertDataMachineMap.containsKey(_originalCallbackObject) )
		{
			//stop alerts and release the state machine from the map
			m_getAlertDataMachineMap.get(_originalCallbackObject).stop();
			m_getAlertDataMachineMap.remove(_originalCallbackObject);
		}		
	}

	/**
	 * Gets the version of the requested device.  Don't use this one for getting the Valentine One version, use the getV1Version call instead.
	 * Requires a function with a String parameter:  public void function( String _parameter).
	 * 
	 * @param _callbackObject The object that has the function on it to be notified when it has the version of the device.
	 * @param _function The function to call when the version is available.
	 * @param _destination The device to query for its version.
	 */
	public void getVersion(Object _callbackObject, String _function, Devices _destination)
	{
		m_versionCallbackObject.put(_destination, _callbackObject);
		m_versionCallbackFunction.put(_destination, _function);

		RequestVersion packet = new RequestVersion(m_valentineType, _destination);
		if ( !m_valentineESP.isRegisteredForPacket(PacketId.respVersion, this) ){
			// Only register if not already registered
			m_valentineESP.registerForPacket(PacketId.respVersion, this, "getVersionCallback");
		}
		
		m_valentineESP.sendPacket(packet);
	}
	
	/**
	 * Gets the version of the currently connected Valentine One.
	 * Requires a function with a String parameter:  public void function( String _parameter).
	 * 
	 * @param _callbackObject The object that has the function on it to be notified when it has the version of the Valentine One.
	 * @param _function The function to call when the version is available.
	 */
	public void getV1Version(Object _callbackObject, String _function)
	{
		RequestVersion packet;
		if (isLibraryInDemoMode())
		{
			packet = new RequestVersion(Devices.VALENTINE1_WITH_CHECKSUM, Devices.VALENTINE1_WITH_CHECKSUM);
			m_versionCallbackObject.put(Devices.VALENTINE1_WITH_CHECKSUM, _callbackObject);
			m_versionCallbackFunction.put(Devices.VALENTINE1_WITH_CHECKSUM, _function);
		}
		else
		{
			m_versionCallbackObject.put(m_valentineType, _callbackObject);
			m_versionCallbackFunction.put(m_valentineType, _function);
			packet = new RequestVersion(m_valentineType, m_valentineType);
		}
		
		if ( !m_valentineESP.isRegisteredForPacket(PacketId.respVersion, this) ){
			// Only register if not already registered		
			m_valentineESP.registerForPacket(PacketId.respVersion, this, "getVersionCallback");
		}
		m_valentineESP.sendPacket(packet);
	}
	/**
	 * Gets the serial number of the requested device.  Don't use this one for getting the Valentine One serial number, 
	 * use the getV1SerialNumber call instead.
	 * Requires a function with a String parameter:  public void function( String _parameter).
	 * 
	 * @param _callbackObject The object that has the function on it to be notified when it has the serial number of the device.
	 * @param _function The function to call when the serial number is available.
	 * @param _destination The device to query for its serial number.
	 */
	public void getSerialNumber(Object _callbackObject, String _function, Devices _destination)
	{
		m_serialNumberCallbackObject.put(_destination, _callbackObject);
		m_serialNumberCallbackFunction.put(_destination, _function);
		
		RequestSerialNumber packet = new RequestSerialNumber(m_valentineType, _destination);
		if ( !m_valentineESP.isRegisteredForPacket(PacketId.respSerialNumber, this) ){
			// Only register if not already registered		
			m_valentineESP.registerForPacket(PacketId.respSerialNumber, this, "getSerialNumberCallback");
		}
		m_valentineESP.sendPacket(packet);
	}
	/** 
	 * Gets the serial number of the currently connected Valentine One.
	 * Requires a function with a String parameter:  public void function( String _parameter).
	 * 
	 * @param _callbackObject The object that has the function on it to be notified when it has the serial number of the Valentine One.
	 * @param _function The function to call when the serial number is available.
	 */
	public void getV1SerialNumber(Object _callbackObject, String _function)
	{
		RequestSerialNumber packet;
		if (isLibraryInDemoMode())
		{
			packet = new RequestSerialNumber(Devices.VALENTINE1_WITH_CHECKSUM, Devices.VALENTINE1_WITH_CHECKSUM);
			m_serialNumberCallbackObject.put(Devices.VALENTINE1_WITH_CHECKSUM, _callbackObject);
			m_serialNumberCallbackFunction.put(Devices.VALENTINE1_WITH_CHECKSUM, _function);
		}
		else
		{
			packet = new RequestSerialNumber(m_valentineType, m_valentineType);
			m_serialNumberCallbackObject.put(m_valentineType, _callbackObject);
			m_serialNumberCallbackFunction.put(m_valentineType, _function);
		}
		if ( !m_valentineESP.isRegisteredForPacket(PacketId.respSerialNumber, this) ){
			// Only register if not already registered		
			m_valentineESP.registerForPacket(PacketId.respSerialNumber, this, "getSerialNumberCallback");
		}
		m_valentineESP.sendPacket(packet);
	}
	/**
	 * Retrieves the current set of user settings (programming settings) from the Valentine One.
	 * Requires a function with a UserSettings parameter:  public void function( UserSettings _parameter).
	 *  
	 * @param _callbackObject  Object which has the function to be called when the UserSettings is available.
	 * @param _function Function to be called when the UserSettings is available.
	 */
	public void getUserSettings(Object _callbackObject, String _function)
	{
		m_userBytesCallbackObject = _callbackObject;
		m_userBytesCallbackFunction = _function;

		RequestUserBytes packet = new RequestUserBytes(m_valentineType);
		m_valentineESP.registerForPacket(PacketId.respUserBytes, this, "getUserBytesCallback");
		m_valentineESP.sendPacket(packet);
	}
	/** 
	 * Writes the given User Settings to the Valentine One.
	 * 
	 * @param _userSettings The user settings to be written to the device.
	 */
	public void writeUserSettings(UserSettings _userSettings)
	{
		RequestWriteUserBytes packet = new RequestWriteUserBytes(_userSettings, m_valentineType);
		m_valentineESP.sendPacket(packet);
	}
	/**
	 * Gets the current battery level of the power source the Valentine One is connected to.
	 * Requires a function with a String parameter:  public void function( String _parameter).
	 * 
	 * @param _callbackObject Object that has the function to be called when the battery level is available.
	 * @param _function Function to be called when the battery level is available.
	 */
	public void getBatteryVoltage(Object _callbackObject, String _function)
	{
		m_voltageCallbackObject = _callbackObject;
		m_voltageCallbackFunction = _function;

		RequestBatteryVoltage packet = new RequestBatteryVoltage(m_valentineType);
		m_valentineESP.registerForPacket(PacketId.respBatteryVoltage, this, "getBatteryVoltageCallback");
		m_valentineESP.sendPacket(packet);
	}	
	
	//***// callbacks
	
	/**
	 * This is the callback from the ESP client to handle the version response from the requested device
	 * and sends this to the requesting object.
	 * Do not call this directly.
	 * 
	 * @param _resp The ResponseVersion packet from the Valentine One.
	 */
	public void localV1connVerCallback(ResponseVersion _resp)
	{
		if ( _resp.getOrigin() == Devices.V1CONNECT && _resp.getOrigin() == Devices.V1CONNECT ){
			m_lastV1ConnVer = (String)_resp.getResponseData();
			m_valentineESP.deregisterForPacket(PacketId.respVersion, this, "localV1connVerCallback");
		}
	}
	
	/**
	 * This is the callback from the ESP client to handle the version response from the requested device
	 * and sends this to the requesting object.
	 * Do not call this directly.
	 * 
	 * @param _resp The ResponseVersion packet from the Valentine One.
	 */
	public void getVersionCallback(ResponseVersion _resp)
	{
		// Cache the V1connection version whenever it is received
		if ( _resp.getOrigin() == Devices.V1CONNECT && _resp.getOrigin() == Devices.V1CONNECT ){
			m_lastV1ConnVer = (String)_resp.getResponseData();
		}
		
		Object callbackObj = m_versionCallbackObject.get(_resp.getOrigin());
		String callbackFunction = m_versionCallbackFunction.get(_resp.getOrigin());

		m_versionCallbackObject.remove(_resp.getOrigin());
		m_versionCallbackFunction.remove(_resp.getOrigin());
		
		if (m_versionCallbackObject.size() == 0)
		{
			m_valentineESP.deregisterForPacket(PacketId.respVersion, this);
		}
		
		PacketQueue.removeFromBusyPacketIds(PacketId.reqVersion);
		
		if ( callbackObj != null && callbackFunction != null ){
			Utilities.doCallback(callbackObj, callbackFunction, String.class, (String)_resp.getResponseData());
		}

		// Update the V1 version based frequency lookup object
		if ( _resp.getOrigin() == Devices.VALENTINE1_WITHOUT_CHECKSUM || _resp.getOrigin() == Devices.VALENTINE1_WITH_CHECKSUM ){
			m_settingLookup.setV1Version ( (String)_resp.getResponseData() );
		}
	}
	
	/**
	 * This is the callback from the ESP client to handle the serial number response from the requested device
	 * and sends this to the requesting object.
	 * Do not call this directly.
	 * 
	 * @param _resp The ResponseSerialNumber packet from the Valentine One.
	 */
	public void getSerialNumberCallback(ResponseSerialNumber _resp)
	{
		Object callbackObj = m_serialNumberCallbackObject.get(_resp.getOrigin());
		String callbackFunction = m_serialNumberCallbackFunction.get(_resp.getOrigin());
		
		m_serialNumberCallbackObject.remove(_resp.getOrigin());
		m_serialNumberCallbackFunction.remove(_resp.getOrigin());
		
		if (m_serialNumberCallbackObject.size() == 0)
		{
			m_valentineESP.deregisterForPacket(PacketId.respSerialNumber, this);
		}
		
		PacketQueue.removeFromBusyPacketIds(PacketId.reqSerialNumber);
		
		Utilities.doCallback(callbackObj, callbackFunction, String.class, (String)_resp.getResponseData());
	}
	
	/**
	 * This is the callback from the ESP client to handle the UserBytes response from the Valentine One
	 * and sends this to the requesting object.
	 * Do not call this directly.
	 * 
	 * @param _resp The ResponseUserBytes packet from the Valentine One.
	 */
	public void getUserBytesCallback(ResponseUserBytes _resp)
	{
		m_valentineESP.deregisterForPacket(PacketId.respUserBytes, this);
		
		PacketQueue.removeFromBusyPacketIds(PacketId.reqUserBytes);
		
		Utilities.doCallback(m_userBytesCallbackObject, m_userBytesCallbackFunction, UserSettings.class, (UserSettings)_resp.getResponseData());
	}
	
	/**
	 * This is the callback from the ESP client to handle the Battery Voltage response from the Valentine One
	 * and sends this to the requesting object.
	 * Do not call this directly.
	 * 
	 * @param _resp The ResponseUserBytes packet from the Valentine One.
	 */
	public void getBatteryVoltageCallback(ResponseBatteryVoltage _resp)
	{
		m_valentineESP.deregisterForPacket(PacketId.respBatteryVoltage, this);
		
		PacketQueue.removeFromBusyPacketIds(PacketId.reqBatteryVoltage);
		Utilities.doCallback(m_voltageCallbackObject, m_voltageCallbackFunction, Float.class, (Float)_resp.getResponseData());
	}	
	
	/** 
	 * Mutes the Valentine one.  True turns the muting on.
	 * 
	 * @param _onOff True Mutes the device.
	 */
	public void mute(boolean _onOff)
	{
		if (_onOff)
		{
			RequestMuteOn packet;
			
			packet = new RequestMuteOn(m_valentineType);
			
			m_valentineESP.sendPacket(packet);
		}
		else
		{
			RequestMuteOff packet;
			
			packet = new RequestMuteOff(m_valentineType);
			
			m_valentineESP.sendPacket(packet);
		}
	}
	
	/** 
	 * Turns off and on the main display on the Valentine One.  True is on, false is off.
	 * 
	 * @param _onOff  True is on, false is off/
	 */
	public void turnMainDisplayOnOff(boolean _onOff)
	{
		if (_onOff)
		{
			RequestTurnOnMainDisplay packet = new RequestTurnOnMainDisplay(m_valentineType);
			m_valentineESP.sendPacket(packet);
		}
		else
		{
			RequestTurnOffMainDisplay packet = new RequestTurnOffMainDisplay(m_valentineType);
			m_valentineESP.sendPacket(packet);
		}
	}
	
	/**
	 * Turns off and on the flow of Alert Data from the Valentine One. To process this data, use registerForAlertData
	 * to register a callback to process the data.
	 * 
	 * @param _send True starts the data, false stops the data.
	 */
	public void sendAlertData(boolean _send)
	{
		if (_send)
		{
			sendStartAlertData();
		}
		else
		{
			sendStopAlertData();
		}
	}	
	
	/** 
	 * Sends a RequestFactoryDefault packet to the indicated device.
	 * 
	 * @param _device The device to send a RequestFactoryDefault to.
	 */
	public void doFactoryDefault(Devices _device)
	{
		RequestFactoryDefault packet = new RequestFactoryDefault(m_valentineType, _device);
		m_valentineESP.sendPacket(packet);
	}
	
	//***// Savvy
	
	/**
	 * Requests the status from the connected Savvy device.
	 * Requires a function with a SavvyStatus parameter:  public void function( SavvyStatus _parameter).
	 * 
	 * @param _callbackObject The object which has the function to call when the status is available.
	 * @param _function The function to call when the status is available.
	 */
	public void getSavvyStatus(Object _callbackObject, String _function)
	{
		m_savvyStatusObject = _callbackObject;
		m_savvyStatusFunction = _function;

		RequestSavvyStatus packet = new RequestSavvyStatus(m_valentineType, Devices.SAVVY);
		m_valentineESP.registerForPacket(PacketId.respSavvyStatus, this, "getSavvyStatusCallback");
		m_valentineESP.sendPacket(packet);
	}
	
	/**
	 * Requests the vehicles speed from the connected Savvy device.
	 * Requires a function with a Integer parameter:  public void function( Integer _parameter).
	 * 
	 * @param _callbackObject The object which has the function to call when the speed is available.
	 * @param _function The function to call when the speed is available.
	 */
	public void getVehicleSpeed(Object _callbackObject, String _function)
	{
		m_vehicleSpeedObject = _callbackObject;
		m_vehicleSpeedFunction = _function;

		RequestVehicleSpeed packet = new RequestVehicleSpeed(m_valentineType, Devices.SAVVY);
		m_valentineESP.registerForPacket(PacketId.respVehicleSpeed, this, "getVehicleSpeedCallback");
		m_valentineESP.sendPacket(packet);
	}	
	
	/** 
	 * This is the call back from the ESP client to call to handle the ResponseSavvyStatus packet.
	 *  Do not call directly.
	 */
	public void getSavvyStatusCallback(ResponseSavvyStatus _resp)
	{
		if(ESPLibraryLogController.LOG_WRITE_DEBUG){
			Log.d("Valentine/SavvyStatus", "callback function = " + m_savvyStatusFunction);
		}
		m_valentineESP.deregisterForPacket(PacketId.respSavvyStatus, this);
		PacketQueue.removeFromBusyPacketIds(PacketId.reqSavvyStatus);
		if ((m_savvyStatusObject != null) && (m_savvyStatusFunction != null))
		{
			Utilities.doCallback(m_savvyStatusObject, m_savvyStatusFunction, SavvyStatus.class, (SavvyStatus)_resp.getResponseData());
		}
		
		//m_savvyStatusObject = null;
		//m_savvyStatusFunction = null;
	}
	
	/**
	 * This is the call back from the ESP client to call to handle the ResponseVehicleSpeed packet.
	 * Do not call directly.
	 */
	public void getVehicleSpeedCallback(ResponseVehicleSpeed _resp)
	{
		m_valentineESP.deregisterForPacket(PacketId.respVehicleSpeed, this);
		PacketQueue.removeFromBusyPacketIds(PacketId.reqVehicleSpeed);
		Utilities.doCallback(m_vehicleSpeedObject, m_vehicleSpeedFunction, Integer.class, (Integer)_resp.getResponseData());
	}
	
	
	/** 
	 * Overrides the thumb wheel on the Savvy to the given speed.
	 * 
	 * @param _speed The speed to set the override to, 0-255.
	 */
	public void setOverrideThumbwheel(byte _speed)
	{
		RequestOverrideThumbwheel packet = new RequestOverrideThumbwheel(m_valentineType, _speed, Devices.SAVVY);
		m_valentineESP.sendPacket(packet);
	}
	
	/** 
	 * Overrides the thumb wheel on the Savvy to the None setting.
	 */
	public void setOverrideThumbwheelToNone()
	{
		RequestOverrideThumbwheel packet = new RequestOverrideThumbwheel(m_valentineType, (byte) 0x00, Devices.SAVVY);
		m_valentineESP.sendPacket(packet);
	}
	
	/**
	 *  Overrides the thumb wheel on the Savvy to the Auto setting.
	 */
	public void setOverrideThumbwheelToAuto()
	{
		RequestOverrideThumbwheel packet = new RequestOverrideThumbwheel(m_valentineType, (byte) 0xff, Devices.SAVVY);
		m_valentineESP.sendPacket(packet);
	}
	
	/**
	 *  Sets the Savvy Mute functionality to on or off.
	 * 
	 * @param _enableMute True enables the functionality, false disables it.
	 */
	public void setSavvyMute(Boolean _enableMute)
	{
		RequestSetSavvyUnmute packet = new RequestSetSavvyUnmute(m_valentineType, _enableMute, Devices.SAVVY);
		m_valentineESP.sendPacket(packet);
	}
	
	//***//Custom Sweeps
	
	/**
	 * Requests current set sweeps on the Valentine One.
	 * Requires a function with a ArrayList<SweepDefinition> parameter:  public void function( ArrayList<SweepDefinition> _parameter).
	 * 
	 * @param _callbackObject The object which has the function to call when the sweeps are available.
	 * @param _function The function to call when the sweeps are available.
	 */
	public void getAllSweeps(Object _callbackObject, String _function)
	{
		// Always clear the cache when reading the sweep data using this method. If the caller does not want the
		// cache cleared they should call the other method.
		getAllSweeps (true, _callbackObject, _function);
	}
	
	/** 
	 * Requests current set sweeps on the Valentine One.
	 * Requires a function with a ArrayList<SweepDefinition> parameter:  public void function( ArrayList<SweepDefinition> _parameter).
	 * 
	 * @param _clearCache if true, the sweep data cache will be cleared before the sweep data is read.
	 * @param _callbackObject The object which has the function to call when the sweeps are available.
	 * @param _function The function to call when the sweeps are available.
	 */
	public void getAllSweeps(boolean _clearCache, Object _callbackObject, String _function)
	{
		// This method does not provide an error callback for handling sweep read errors
		m_getAllSweepsMachine = new GetAllSweeps(_clearCache, m_valentineType, m_valentineESP, _callbackObject, _function, null, null);
		m_getAllSweepsMachine.getSweeps();
	}
	
	/**
	 * This method will force the stop the sweep reading state machine.
	 */
	public void abortSweepRequest ()
	{
		if ( m_getAllSweepsMachine != null ){
			m_getAllSweepsMachine.abort();
		}
	}
	
	/** 
	 * Write the supplied custom sweep definitions to the Valentine One.  This does not do any checking of the definitions before 
	 * writing them to the device.
	 * Both the success callback and the error call back require a function with a String parameter:  public void function( String _parameter).
	 * 
	 * @param _definitions A list of definitions for the sweeps.
	 * @param _callbackObject The object that has the function to call when the sweeps are written correctly.
	 * @param _function The function to call on success.
	 * @param _errorObject The object that has the function to call when the sweeps writing fails.
	 * @param _errorFunction The function to call on failure.
	 */
	public void setCustomSweeps(ArrayList<SweepDefinition> _definitions, Object _callbackObject, String _function, Object _errorObject, String _errorFunction)
	{
		m_writeCustomSweepsMachine = new WriteCustomSweeps(_definitions, m_valentineType, m_valentineESP, _callbackObject, _function, _errorObject, _errorFunction);
		m_writeCustomSweepsMachine.Start();
	}
	
	/**
	 * Requests the current sweep sections from the Valentine One.
	 * Requires a function with a ArrayList<SweepSections> parameter:  public void function( ArrayList<SweepSections> _parameter).
	 * 
	 * @param _callbackObject Object that has the function to call when the sweep sections are available.
	 * @param _function Function to call when the sections are available.
	 */
	public void getSweepSections(Object _callbackObject, String _function)
	{
		m_getSweepsObject = _callbackObject;
		m_getSweepsFunction = _function;
		
		RequestSweepSections packet = new RequestSweepSections(m_valentineType);
		m_valentineESP.registerForPacket(PacketId.respSweepSections, this, "getSweepSectionsCallback");
		m_valentineESP.sendPacket(packet);		
	}
	
	/**
	 * Tells the Valentine One to set the current Sweeps to the default ones. 
	 */
	public void setSweepsToDefault()
	{
		RequestSetSweepsToDefault packet = new RequestSetSweepsToDefault(m_valentineType);
		m_valentineESP.sendPacket(packet);
	}
	
	//**//CustomSweepCallbacks
	/** 
	 * This is the call back from the ESP client to call to handle the RequestSweepSections packets.
	 * Do not call directly. 
	 */
	public void getSweepSectionsCallback(ResponseSweepSections _resp)
	{
		//get sweep sections back
		m_valentineESP.deregisterForPacket(PacketId.respSweepSections, this);
		SweepSection[] sections = (SweepSection[]) _resp.getResponseData();

		ArrayList<SweepSection> temp = new ArrayList<SweepSection>();
		for (int i = 0; i < sections.length; i++)
		{
			temp.add(sections[i]);
		}
		
		setCachedSweepSections(temp);
		
		PacketQueue.removeFromBusyPacketIds(PacketId.reqSweepSections);
		
		Utilities.doCallback(m_getSweepsObject, m_getSweepsFunction, SweepSection[].class, (SweepSection[])sections);
	}
	
	/** 
	 * Starts the client into demo mode.  This will disconnect the client from the V1Connection.  
	 * 
	 * @param _demoData The demo data of bytes from packets, comments, and display messages read into a single string from a file.
	 * @param _repeat Repeat the data from the beginning when it reaches the end of the data.
	 */
	public void startDemoMode(String _demoData, boolean _repeat)
	{
		// Start the callbacks into the local methods whenever we start demo mode.
		registerLocalCallbacks();
				
		m_valentineESP.startDemo(_demoData, _repeat);
		
		// Turn on demo mode in the frequency lookup object.		
		m_settingLookup.setDemoMode(true);
	}
	
	/** 
	 * Stops the playing of the demo mode data and potentially reconnects to the V1Connection.
	 * 
	 * @param _restart Restarts and reconnects the client to the V1Connection.
	 * @return Successful restart of the client if required.
	 */
	public boolean stopDemoMode( boolean _restart)
	{
        clearBroadcastEvents();

		boolean rc = m_valentineESP.stopDemo( _restart);
		if ( _restart)
		{
			if (rc)
			{
				doSearch();
			}
		}		
		
		// Turn off demo mode in the frequency lookup object.
		m_settingLookup.setDemoMode(false);
		
		return rc;
	}
	
	/**
	 * Registers a function to handle notification messages from the Demo mode data playback.
	 * Requires a function with a String parameter:  public void function( String _parameter).
	 * 
	 * Only one Notification callback is allowed at a time, registering a second will overwrite the first.
	 * 
	 * @param _notificationObject The object that handles the notification and has the function on it.
	 * @param _function The function to be called.
	 */
	public void registerNotificationFunction(Object _notificationObject, String _function)
	{
		m_notificationObject = _notificationObject;
		m_notificationFunction = _function;
	}
	
	/** 
	 * Removes the notification callback data.
	 */
	public void deregisterNotificationFunction()
	{
		m_notificationObject = null;
		m_notificationFunction = null;
	}
	
	/**
	 * Sends a notification to the Notification object to handle.
	 * 
	 * @param _notification The notification to be handled by the notification object.
	 */
	public void doNotification(String _notification)
	{
		if ((m_notificationObject != null) && (m_notificationFunction != null))
		{
			Utilities.doCallback(m_notificationObject, m_notificationFunction, String.class, _notification);
		}
	}
	
	
	//**// Mode Change
	/** 
	 * Changes the logic mode of the Valentine One.
	 * 
	 * @param _mode The value of the mode you want to set the valentine one to.
	 * <pre>
	 * Value    Mode
	 *          US                      Euro Non Custom Sweeps      Euro Custom Sweeps 
	 * 1        All Bogeys              K & Ka(Photo)               K & Ka Custom Sweeps
	 * 2        Logic Mode              Ka(Photo Only)              Ka Custom Sweeps
	 * 3        Advanced Logic Mode     n/a                         n/a
	 * </pre>
	 */
	public void changeMode(byte _mode)
	{
		RequestChangeMode packet = new RequestChangeMode(_mode, m_valentineType);
		m_valentineESP.sendPacket(packet);
	}
	
	/**
	 * Utility function that will clear all callbacks for the Request Version packets.  This is to clear outstanding
	 * callbacks if a device is not connected when an activity is dismissed. 
	 */
	public void clearVersionCallbacks()
	{
		m_valentineESP.clearCallbacks( PacketId.respVersion);
	}
	
	/**
	 * Utility function that will clear all callbacks for all packets. This is to clear outstanding
	 * callbacks if a device is not connected when an activity is dismissed.
	 * 
	 */
	public void clearAllCallbacks()
	{
		m_valentineESP.clearAllCallbacks();
		
		// We do not need to call stop on the alert data callbacks because the call to 
		// m_valentineESP.clearAllCallbacks() will do that for us.
		m_getAlertDataMachineMap.clear();
		
		m_infCallbackCallbackData.clear();		
		m_versionCallbackObject.clear();
		m_versionCallbackFunction.clear();
		m_serialNumberCallbackObject.clear();
		m_serialNumberCallbackFunction.clear();
		
		m_userBytesCallbackObject = null;
		m_userBytesCallbackFunction = null;
		m_voltageCallbackObject = null;
		m_voltageCallbackFunction = null;
		m_savvyStatusObject = null;
		m_savvyStatusFunction = null;
		m_vehicleSpeedObject = null;
		m_vehicleSpeedFunction = null;
		m_getSweepsObject = null;
		m_getSweepsFunction = null;
		m_getMaxSweepIndexObject = null;
		m_getMaxSweepIndexFunction = null;
		
		m_notificationObject = null;
		m_notificationFunction = null;

		m_stopObject = null;
		m_stopFunction = null;

		m_unsupportedDeviceObject = null;
		m_unsupportedDeviceFunction = null;

		m_unsupportedPacketObject = null;
		m_unsupportedPacketFunction = null;
		m_requestNotProcessedObject = null;
		m_requestNotProcessedFunction = null;
		m_dataErrorObject = null;
		m_dataErrorFunction = null;
		m_dataErrorObjectRaw = null;
		m_dataErrorFunctionRaw = null;
	}
	
	/** 
	 * Register a function to bl;e notified if the ESP client has not received any data from the Valentine One after 5 seconds.
	 * Example case of this being called, the valentine one has been turned off for more than 5 seconds.
	 * Requires a function with a String parameter:  public void function( String _parameter).
	 *  
	 * Only one Notification callback is allowed at a time, registering a second will overwrite the first.
	 *  
	 * @param _owner The object with the function to be notified when there is no data from the Valentine One.
	 * @param _function The function to call.
	 */
	public void registerNoDataCallback(Object _owner, String _function)
	{
		m_valentineESP.registerForNoDataNotification(_owner, _function);
	}
	
	/**
	 * Removes the no data notification callback.
	 */
	public void deregisterNoDataCallback()
	{
		m_valentineESP.deregisterForNoDataNotification();
	}

	/**
	 * Sets the callback data for when the demo data processing finds a User Settings packet in it. 
	 * Requires a function with a UserSettings parameter:  public void function( UserSettings _parameter).
	 * 
	 * @param _owner Object to be notified when there is a demo data user settings object in the demo data.
	 * @param _function Function to be called.
	 */
	public void setDemoConfigurationCallbackData(Object _owner, String _function)
	{
		m_valentineESP.setDemoConfigurationCallbackData(_owner, _function);
	}
	
	/**
	 * Returns the current UserSettings from the demo data outside of the callback. 
	 * @return the demo data user settings.
	 */
	public UserSettings getDemoUserSettings()
	{
		return m_valentineESP.getDemoData().getUserSettings();
	}
	
	/**
	 * The callback for the when the ESP client tries to connect to the Valentine One with an unsupported phone.
	 * Requires a function with a String parameter:  public void function( String _parameter).
	 * 
	 * @param _obj The object to be notified when there is an unsupported phone.
	 * @param _function The function to be called.
	 */
	public void setUnsupportedDeviceCallback(Object _obj, String _function)
	{
		m_unsupportedDeviceObject = _obj;
		m_unsupportedDeviceFunction = _function;
	}
	
	/**
	 * Does the notification when there is a unsupported device detected.
	 * 
	 * @param _error Error to pass to the notification object.
	 */
	public void unsupportedDeviceCallback(String _error)
	{
		if ((m_unsupportedDeviceObject != null) && (m_unsupportedDeviceFunction != null))
		{
			Utilities.doCallback(m_unsupportedDeviceObject, m_unsupportedDeviceFunction, String.class, _error);
		}
	}
	
	/**
	 * Sets the callback object to be notified when the Valentine One sends a Unsupported packet to the client.
	 * Requires a function with a Integer parameter: public void function( Integer _parameter).
	 * 
	 * @param _obj The object to be notified when there is an UnsupportedPacket sent.
	 * @param _function The function to be called.
	 */
	public void registerUnsupportedPacketCallback(Object _obj, String _function)
	{
		m_unsupportedPacketObject = _obj;
		m_unsupportedPacketFunction = _function;
	}
	
	/** 
	 * Does the notification when there is a unsupported packet detected.
	 * 
	 * @param _error Error to pass to the notification object.
	 */
	public void unsupportedPacketCallback(ResponseUnsupported _error)
	{
		if ((m_unsupportedPacketObject != null) && (m_unsupportedPacketFunction != null))
		{
			Utilities.doCallback(m_unsupportedPacketObject, m_unsupportedPacketFunction, Integer.class, _error.getResponseData());
		}
	}
	
	/** 
	 * Sets the callback object to be notified when the Valentine One sends a RequestNotProcessed packet to the client.
	 * Requires a function with a Integer parameter:  public void function( Integer _parameter).
	 * 
	 * @param _obj The object to be notified when there is an RequestNotProcessed object
	 * @param _function The function to be called
	 */
	public void registerRequestNotProcessedCallback(Object _obj, String _function)
	{
		m_requestNotProcessedObject = _obj;
		m_requestNotProcessedFunction = _function;
	}
	
	/** 
	 * Does the notification when the Valentine One sends a RequestNotProcessed packet.
	 * 
	 * @param _error Error to pass to the notification object.
	 */
	public void RequestNotProcessedCallback(ResponseRequestNotProcessed _error)
	{
		if ((m_requestNotProcessedObject != null) && (m_requestNotProcessedFunction != null))
		{
			Utilities.doCallback(m_requestNotProcessedObject, m_requestNotProcessedFunction, Integer.class, _error.getResponseData());
		}
	}
	
	/**
	 * Sets the callback object to be notified when the Valentine One sends a DataError packet to the client.
	 * Requires a function with a Integer parameter:  public void function( Integer _parameter).
	 * 
	 * @param _obj The object to be notified when there is an DataError packet.
	 * @param _function The function to be called.
	 */
	public void registerDataErrorCallback(Object _obj, String _function)
	{
		m_dataErrorObject = _obj;
		m_dataErrorFunction = _function;
	}
	
	/**
	 * Sets the callback object to be notified when the Valentine One sends a DataError packet to the client.
	 * Requires a function with a ResponseDataError parameter:  public void function( String _parameter).
	 * This sends the raw packet to the app to handle.
	 * 
	 * @param _obj The object to be notified when there is an DataError packet.
	 * @param _function The function to be called.
	 */
	public void registerDataErrorRawCallback(Object _obj, String _function)
	{
		m_dataErrorObjectRaw = _obj;
		m_dataErrorFunctionRaw = _function;
	}
	
	/**
	 *  Does the notification when the Valentine One sends a DataError packet.
	 *  
	 * @param _error Error to pass to the notification object.
	 */
	public void dataErrorCallback(ResponseDataError _error)
	{
		if ((m_dataErrorObject != null) && (m_dataErrorFunction != null))
		{
			Utilities.doCallback(m_dataErrorObject, m_dataErrorFunction, Integer.class, _error);
		}
		if ((m_dataErrorObjectRaw != null) && (m_dataErrorFunctionRaw != null))
		{
			Utilities.doCallback(m_dataErrorObjectRaw, m_dataErrorFunctionRaw, ResponseDataError.class, _error);
		}
		
	}
	
	/** 
	 * Gets the max sweep index from the Valentine One.
	 * Requires a function with a Integer parameter:  public void function( Integer _parameter).
	 * 
	 * @param _obj The object to be notified when the max sweep index has been retrieved.
	 * @param _function The function to be called.
	 */
	public void getMaxSweepIndex(Object _obj, String _function)
	{
		m_getMaxSweepIndexObject = _obj;
		m_getMaxSweepIndexFunction = _function;
		
		RequestMaxSweepIndex packet = new RequestMaxSweepIndex(m_valentineType);
		m_valentineESP.registerForPacket(PacketId.respMaxSweepIndex, this, "maxSweepIndexCallback");
		m_valentineESP.sendPacket(packet);		
	}
	
	/** 
	 * Does the notification when the Valentine One sends back the Max sweep index.
	 * 
	 * @param _maxSweep The maximum number of Sweeps.
	 */
	public void maxSweepIndexCallback(ResponseMaxSweepIndex _maxSweep)
	{
		m_valentineESP.deregisterForPacket(PacketId.respMaxSweepIndex, this);
		PacketQueue.removeFromBusyPacketIds(PacketId.reqMaxSweepIndex);
	
		m_maxSweepIndex = (Integer)_maxSweep.getResponseData();
		
		Utilities.doCallback(m_getMaxSweepIndexObject, m_getMaxSweepIndexFunction, Integer.class, _maxSweep.getResponseData());
	}

	/**
	 * Send a request to the V1 to start sending alert data.
	 */
	private void sendStartAlertData() {
		if(ESPLibraryLogController.LOG_WRITE_DEBUG){
			Log.d(LOG_TAG, "Sending Start Alert Data Packet");
		}
		RequestStartAlertData packet = new RequestStartAlertData(m_valentineType);
		m_valentineESP.sendPacket(packet);
	}

	/**
	 * Send a request to the V1 to stop sending alert data.
	 */
	private void sendStopAlertData() {
		if(ESPLibraryLogController.LOG_WRITE_DEBUG){
			Log.d(LOG_TAG, "Sending Stop Alert Data Packet");
		}
		RequestStopAlertData packet = new RequestStopAlertData(m_valentineType);
		m_valentineESP.sendPacket(packet);
	}
	
	/** This method will request the V1connection version so it can be cached in this object
	 */
	private void getV1connectionVerison ()
	{
		RequestVersion packet = new RequestVersion(m_valentineType, Devices.V1CONNECT);
		// Only register if not already registered
		m_valentineESP.registerForPacket(PacketId.respVersion, this, "localV1connVerCallback");
		
		m_valentineESP.sendPacket(packet);
	}
	
	/**
	 * Retrieve the current Bluetooth socket.
	 * 
	 * @return The current Bluetooth socket.
	 */
	BluetoothSocket getSocket() {
		return m_valentineESP.getSocket();
	}
	
	/**
	 * Pass through method to the ValentineESP object to determine if the object passed in is registered for
	 * the packet type passed in.
	 * 
	 * @param _type - The packet type to look for.
	 * @param _object - The object to look for.
	 * 
	 * @return true if the object passed in is registered for the packet type passed in, else false.
	 */
	public boolean isRegisteredForPacket (PacketId _type, Object _object)
	{
		return m_valentineESP.isRegisteredForPacket (_type, _object);
	}
	
	/**
	 * Pass through method to the ValentineESP object to register for a packet.
	 * 
	 * @param _type - The packet type to register for.
	 * @param _object - The object to register.
	 * @param _method - The method to register.
	 * 
	 * @return true if the object passed in is registered for the packet type passed in, else false.
	 */
	public void registerForPacket(PacketId _type, Object _callBackObject, String _method)
	{
		m_valentineESP.registerForPacket(_type, _callBackObject, _method);
	}
	
}
