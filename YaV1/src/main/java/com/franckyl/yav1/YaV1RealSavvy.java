package com.franckyl.yav1;

import com.valentine.esp.data.SavvyStatus;

/**
 * Created by franck on 11/15/13.
 */
public class YaV1RealSavvy
{
    public static boolean enabled          = false;
    public static int     count            = 0;
    public static int     speed            = 0;
    public static double  speedMs          = 0.0;
    public static int     mCurrUnit        = 0;
    public static SavvyStatus sSavvyStatus = null;

    // reset the current real savvy

    public static void reset(boolean enable)
    {
        enabled     = enable;
        speed       = 0;

        if(!enabled)
            sSavvyStatus = null;
    }

    public static void setSpeed(int s)
    {
        speed   = s;
        speedMs = Math.round( ( (double) s / 3.6d) * 100) / 100;
        if(YaV1.sCurrUnit > 0)
            speed = (int) (speed * 0.621371192);
        count++;
    }

    public static void refreshSettings()
    {
        mCurrUnit    = Integer.valueOf(YaV1.sPrefs.getString("speed_unit", "0"));
    }

    public static void setSavvyStatus(SavvyStatus s)
    {
        sSavvyStatus = s;
    }

    public static boolean hasRealSavvy()
    {
        return sSavvyStatus != null;
    }
}
