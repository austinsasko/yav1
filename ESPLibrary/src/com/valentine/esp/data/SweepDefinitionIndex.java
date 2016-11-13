package com.valentine.esp.data;

public class SweepDefinitionIndex 
{
	private int m_numberOfSectionsAvailable;
	private int m_currentSweepIndex;
	
	/**
	 * Get the number of available sweep sections.
	 * 
	 * @return The number of sweep section.
	 */
	public int getNumberOfSectionsAvailable()
	{
		return m_numberOfSectionsAvailable;
	}
	
	/**
	 * Gets the current sweep index.
	 * @return	Current sweep index. 
	 */
	public int getCurrentSweepIndex()
	{
		return m_currentSweepIndex;
	}
	
	/**
	 * Sets the number of available sweep section.
	 * @param val	Number of available sweep section.
	 * 
	 */
	public void setNumberOfSectionsAvailable(int val)
	{
		m_numberOfSectionsAvailable = val;
	}
	
	/**
	 * Sets the current sweeps index.
	 * 
	 * @param val	The index of the current sweep.
	 */
	public void setCurrentSweepIndex(int val)
	{
		m_currentSweepIndex = val;
	}
	
	/**
	 * Sets the sweep definitions index data using the data from the passed in byte.	 
	 * @param _data	byte containing data about the sweep definition index.
	 */
	public void buildFromByte(byte _data)
	{
		byte temp;
		
		temp = (byte) (_data & 0x0F);
		
		m_numberOfSectionsAvailable = temp;
		
		temp = (byte) (_data & 0xF0);
		
		m_currentSweepIndex = temp;
	}
}
