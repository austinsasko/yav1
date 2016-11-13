package com.valentine.esp.statemachines;

//import android.util.Log;
import android.util.Log;
import android.util.SparseArray;

import com.valentine.esp.PacketQueue;
import com.valentine.esp.ValentineESP;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.data.AlertData;
import com.valentine.esp.data.BandArrowData;
import com.valentine.esp.packets.response.ResponseAlertData;
import com.valentine.esp.utilities.Utilities;

import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1lib.YaV1AlertList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** This is the class that aggregates the Alert Data responses into an ArrayList representing all of the current alerts.
 * Used internally by the Valentine Client.
 *
 */
public class GetAlertData 
{
	//private static final String LOG_TAG = "ValentineESP/GetAlertData";
	private ValentineESP m_valentineESP;
	private Object       m_callbackObject;
	private String       m_callbackFunction;
    private Method       m_callbackMethod;
	// private Map<Integer, AlertData> m_currentResponses;
    private boolean        m_currentResponses = false;
    private BandArrowData  m_bandArrowData;
    private boolean        m_isValid;
	int m_received;

    private YaV1AlertList           m_toSend   =  new YaV1AlertList();
    private SparseArray<YaV1Alert>  m_store    =  new SparseArray<YaV1Alert>(15);

	/**
	 * Constructor that sets up the ValentineESP object, the obCallback object and callback function.
	 * 
	 * @param _valentineESP			A ValentineESP object to to register with the library for a AlertData.
	 * @param _callbackObject		The object which wants to receive the AlertData.
	 * @param _callbackFunction		the function inside of the Object that will be receiving the AlertData.
	 */
	public GetAlertData(ValentineESP _valentineESP, Object _callbackObject, String _callbackFunction)
    {
		m_valentineESP = _valentineESP;
		m_callbackObject = _callbackObject;
		m_callbackFunction = _callbackFunction;

        try
        {
            m_callbackMethod = _callbackObject.getClass().getMethod(_callbackFunction, YaV1AlertList.class);
        }
        catch(NoSuchMethodException ex)
        {
            Log.d("Valentine", "No method for callback alert");
            m_callbackMethod = null;
        }
	}

	/**
	 * Tells the V1 to start sending AlertData.
	 */
	public void start() {
		m_valentineESP.registerForPacket(PacketId.respAlertData, this, "getAlertDataCallback");
	}
	
	/**
	 * Tells the V1 to stop sending AlertData.
	 */
	public void stop() {
		//stop listening to alerts from V1
		m_valentineESP.deregisterForPacket(PacketId.respAlertData, this);
	}
	
	/**
	 * Callback that receives the ResponseAlertData and converts it to AlertData.
	 * 
	 * @param _resp the ResponseAlertData that will be converted to AlertData.
	 */
	public void getAlertDataCallback(ResponseAlertData _resp)
	{
		AlertData alert = (AlertData) _resp.getResponseData();
		int index = alert.getAlertIndexAndCount().getIndex();
		int count = alert.getAlertIndexAndCount().getCount();
		
		PacketQueue.removeFromBusyPacketIds(PacketId.reqStartAlertData);
		
		if((index == 1) && (count > 0))
		{
			//m_currentResponses = new HashMap<Integer, AlertData>();
            m_currentResponses = true;
            m_store.clear();
			m_received = 0;
		}
		
		if (count == 0)
		{
            m_store.clear();
            m_received = 0;
            if(!m_currentResponses)
                m_currentResponses = true;
            /*
			if (m_currentResponses != null)
			{
				m_currentResponses.clear();
			}
			else
			{
				m_currentResponses = new HashMap<Integer, AlertData>();
			}
			*/
		}
		
		//if ((count > 0) && (m_currentResponses != null))
        if ((count > 0) && m_currentResponses)
		{
            m_isValid = true;
            // we format the YaV1 alert
            YaV1Alert n = new YaV1Alert();
            n.setNow();

            n.setFrequency(alert.getFrequency());

            m_bandArrowData = alert.getBandArrowData();

            if(m_bandArrowData.getKaBand())
                n.setBand(n.BAND_KA);
            else if(m_bandArrowData.getKBand())
                n.setBand(n.BAND_K);
            else if(m_bandArrowData.getKuBand())
                n.setBand(n.BAND_KU);
            else if(m_bandArrowData.getXBand())
                n.setBand(n.BAND_X);
            else
                m_isValid = false;

            if(m_isValid)
            {
                if (m_bandArrowData.getRear())
                    n.setArrowDir(n.ALERT_REAR);
                else if (m_bandArrowData.getFront())
                    n.setArrowDir(n.ALERT_FRONT);
                else
                    n.setArrowDir(n.ALERT_SIDE);

                // set the signal and delta signal
                n.setSignal(alert.getFrontSignalNumberOfLEDs(), alert.getRearSignalNumberOfLEDs());

                if (alert.getPriorityAlert())
                    n.setProperty(n.PROP_PRIORITY);

                n.setOrder(index);

                // Log.d("Valentine", "Alert index " + index + " Frequency " + alert.getFrequency() + " Remain bytes " + alert.getReserved());
                m_store.put(index, n);
            }

			// m_currentResponses.put(index, alert);
			m_received++;
		}


		//if (m_currentResponses != null)
        if(m_currentResponses)
		{
            int nb = m_store.size();
            if ((count == 0) || (nb == count))
			//if ((count == 0) || (m_currentResponses.size() == count))
			{
                m_toSend.clear();
                if(nb > 0)
                {
                    YaV1Alert a;
                    for(int i=0; i<nb; i++)
                    {
                        m_toSend.add(m_store.valueAt(i));
                    }
                }

                if(m_callbackMethod != null)
                {
                    /*
                    new Runnable()
                    {
                        public void run() { */
                            try {
                                m_callbackMethod.invoke(m_callbackObject, m_toSend);
                                // m_callbackMethod.invoke(m_callbackObject, m_toSend);
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                                Log.d("Valentine", "Error Illegal access method from getAlertData");
                                e.printStackTrace();

                            } catch (InvocationTargetException ei) {
                                ei.printStackTrace();
                                Log.d("Valentine", "Target exception access method from getAlertData");
                                ei.printStackTrace();
                            }
                    /*    }
                    }.run(); */
                }
                else
                {
                    Log.d("Valentine", "Alerts done with utilities");
                    Utilities.doCallback(m_callbackObject, m_callbackFunction, YaV1AlertList.class, m_toSend);
                }
                /*
				ArrayList<AlertData> data = new ArrayList<AlertData>();

				for (int i = 0; i <= m_currentResponses.size(); i++)
				{
					AlertData item = m_currentResponses.get(i);
					if (item != null)
					{
						data.add(item);
					}
				}
				
				AlertData rc[] = new AlertData[data.size()];
				rc = data.toArray(rc);
				
				Utilities.doCallback(m_callbackObject, m_callbackFunction, AlertData[].class, rc);
				*/
			}
		}
	}
}
