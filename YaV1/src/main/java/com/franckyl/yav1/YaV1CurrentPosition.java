package com.franckyl.yav1;

import android.os.SystemClock;
import android.util.Log;

import com.franckyl.yav1.utils.YaV1GpsPos;

/**
 * Created by franck on 8/8/13.
 */
public class YaV1CurrentPosition
{
    public static boolean  enabled = false;
    public static boolean  isValid = false;
    public static double   lat;
    public static double   lon;
    public static int      bearing;
    public static long     timeStamp;

    // initialize the speed with negative to know when we first search of a location

    public static double   speed   = -10.;
    public static double   cSpeed;
    public static int      accuracy;
    public static int      count = 0;
    public static int      unit  = 0;

    // the flag for collector / lockout

    public static boolean  sCollector = false;
    public static boolean  sLockout   = false;

    public static String   direction[] = {YaV1.sContext.getString(R.string.alert_run_direction_n),
                                          YaV1.sContext.getString(R.string.alert_run_direction_ne),
                                          YaV1.sContext.getString(R.string.alert_run_direction_e),
                                          YaV1.sContext.getString(R.string.alert_run_direction_se),
                                          YaV1.sContext.getString(R.string.alert_run_direction_s),
                                          YaV1.sContext.getString(R.string.alert_run_direction_sw),
                                          YaV1.sContext.getString(R.string.alert_run_direction_w),
                                          YaV1.sContext.getString(R.string.alert_run_direction_nw),
                                         };
    // reset enable/disable

    public static void reset(boolean enable)
    {
        Log.d("Valentine", "CurrentPosition reset " + enable);
        enabled     = enable;
        speed       = -10.;
        timeStamp   = SystemClock.elapsedRealtime();
        if(!enable)
        {
            isValid     = false;
            sCollector  = false;
            sLockout    = false;
        }
        else
        {
            sCollector = YaV1.sPrefs.getBoolean("data_collect", false);
            sLockout   = YaV1.sPrefs.getBoolean("enable_lockout", false);
        }
    }

    // get the bearing from the degrees

    public static String getBearingString()
    {
        return bearingToString(bearing);
    }

    // static function to convert bearing to string

    public static String bearingToString(float bearingTo)
    {
        if(bearingTo < 0)
            bearingTo += 360;

        int dir = -1;

        // Set the direction

        if ( (360 >= bearingTo && bearingTo >= 337.5) || (0 <= bearingTo && bearingTo <= 22.5) ) dir = 0;
        else if (bearingTo > 22.5 && bearingTo < 67.5) dir = 1;
        else if (bearingTo >= 67.5 && bearingTo <= 112.5) dir = 2;
        else if (bearingTo > 112.5 && bearingTo < 157.5) dir = 3;
        else if (bearingTo >= 157.5 && bearingTo <= 202.5) dir = 4;
        else if (bearingTo > 202.5 && bearingTo < 247.5) dir = 5;
        else if (bearingTo >= 247.5 && bearingTo <= 292.5) dir = 6;
        else if (bearingTo > 292.5 && bearingTo < 337.5) dir = 7;

        return (dir >= 0 ? direction[dir] : "?");
    }

    // retrieve a GpsPos object

    public static YaV1GpsPos getPos()
    {
        YaV1GpsPos pos = new YaV1GpsPos();

        if(isValid)
        {
            pos.timestamp = timeStamp;
            pos.lat       = lat;
            pos.lon       = lon;
            pos.speed     = speed;
            pos.bearing   = bearing;
        }
        else
        {
            pos.timestamp = SystemClock.elapsedRealtime();
            pos.lat       = Double.NaN;
        }

        return pos;
    }

    // return the Angle between 2 direction

    public static int getAngle(int aDirection, int bDirection)
    {
        int angle = 0;
        if(aDirection < 0)
            aDirection += 360;
        if(bDirection < 0)
            bDirection += 360;
        angle = (int) Math.abs(aDirection - bDirection);

        if(angle > 180)
            angle = 360 - angle;

        return angle;
    }
}
