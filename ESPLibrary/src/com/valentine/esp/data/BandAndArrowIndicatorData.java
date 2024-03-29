package com.valentine.esp.data;

public class BandAndArrowIndicatorData 
{
	private boolean m_laser;
	private boolean m_kaBand;
	private boolean m_kBand;
	private boolean m_xBand;
	private boolean m_front;
	private boolean m_side;
	private boolean m_rear;
	private boolean m_reserved;
    private byte    m_RawData;

	public BandAndArrowIndicatorData ()
	{
		// Nothing to do in the empty constructor
	}
	
	/**
	 *  Use the copy constructor to make a deep copy of this object
	 * @param src
	 */
	public BandAndArrowIndicatorData (BandAndArrowIndicatorData src)
	{
		 m_laser= src.m_laser;
		 m_kaBand= src.m_kaBand;
		 m_kBand= src.m_kBand;
		 m_xBand= src.m_xBand;
		 m_front= src.m_front;
		 m_side= src.m_side;
		 m_rear= src.m_rear;
	}
	
	/**
	 * Method to clear the data to provide a blank band/arrow indicator
	 */
	public void clear()
	{
		 setFromByte ( (byte)0x00 );
	}
	
	/**
	 * This method will compare the contents of this object to the object passed in to see if all of the contents are equal.
	 * 
	 * @param src -The source object to use for the comparison.
	 * 
	 * @return true if ALL data in this object is equal to the object passed in, else false. 
	 */
	public boolean isEqual(BandAndArrowIndicatorData src)
	{
		if (  m_laser != src.m_laser) { return false;	}
		if (  m_kaBand != src.m_kaBand) { return false;	}
		if (  m_kBand != src.m_kBand) { return false;	}
		if (  m_xBand != src.m_xBand) { return false;	}
		if (  m_front != src.m_front) { return false;	}
		if (  m_side != src.m_side) { return false;	}
		if (  m_rear != src.m_rear) { return false;	}
		
		return true;
	}
	
	/**
	 * Returns if laser is active or not.
	 */
	public boolean getLaser()
	{
		return m_laser;
	}
	
	/**
	 * Returns if Ka is active or not.
	 */
	public boolean getKaBand()
	{
		return m_kaBand;
	}
	
	/**
	 * Returns if K is active or not.
	 */
	public boolean getKBand()
	{
		return m_kBand;
	}
	
	/**
	 * Returns if X is active or not.
	 */
	public boolean getXBand()
	{
		return m_xBand;
	}
	
	/**
	 * Returns if there is a alert present in the front.
	 */
	public boolean getFront()
	{
		return m_front;
	}
	
	/**
	 * Returns if there is a alert present in the side.
	 */
	public boolean getSide()
	{
		return m_side;
	}
	
	/**
	 * Returns if there is a alert present in the rear.
	 */
	public boolean getRear()
	{
		return m_rear;
	}

    public boolean getReserved(){ return m_reserved; }

    public byte getRawData(){ return m_RawData; }
	/**
	 * Sets the alert indicator states based on then data from the byte passed in.
	 */
	public void setFromByte(byte _data)
	{
        m_RawData = _data;
		/*
		07 06 05 04 03 02 01 00
		|  |  |  |  |  |  |  |
		|  |  |  |  |  |  |  \-- LASER
		|  |  |  |  |  |  \----- Ka BAND
		|  |  |  |  |  \-------- K BAND
		|  |  |  |  \----------- X BAND
		|  |  |  \-------------- Reserved
		|  |  \----------------- FRONT
		|  \-------------------- SIDE
		\----------------------- REAR
		*/
	
		if ((_data & 1) > 0)
		{
			m_laser = true;
		}
		else
		{
			m_laser = false;
		}
		
		if ((_data & 2) > 0)
		{
			m_kaBand = true;
		}
		else
		{
			m_kaBand = false;
		}
		
		if ((_data & 4) > 0)
		{
			m_kBand = true;
		}
		else
		{
			m_kBand = false;
		} 
		
		if ((_data & 8) > 0)
		{
			m_xBand = true;
		}
		else
		{
			m_xBand = false;
		}

        if ((_data & 16) > 0)
        {
            m_reserved = true;
        }
        else
        {
            m_reserved = false;
        }

        if ((_data & 32) > 0)
		{
			m_front = true;
		}
		else
		{
			m_front = false;
		}
		
		if ((_data & 64) > 0)
		{
			m_side = true;
		}
		else
		{
			m_side = false;
		}
		
		if ((_data & 128) > 0)
		{
			m_rear = true;
		}
		else
		{
			m_rear = false;
		}		
	}
}
