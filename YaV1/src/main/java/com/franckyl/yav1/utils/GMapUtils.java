package com.franckyl.yav1.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.format.DateFormat;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.List;

/**
 * Created by franck on 1/29/14.
 */
public class GMapUtils
{
    public static int       sCurrUnit  = 0;
    public static String    sUnitStr[] = {"Kmh", "Mph"};
    public static double    sUnit[]    = {3.6d, 2.23693629d};
    public static String    sTimeFmt[] = {"kk:mm:ss", "hh:mm:ss aa"};
    private static java.util.Date sJv  = new java.util.Date();

    // refresh the settings

    public static void refreshSettings()
    {
        sCurrUnit =  Integer.valueOf(YaV1.sPrefs.getString("gmap_unit", "0"));
    }

    // string for no connection to google map

    public static int getGMapError(Context context)
    {
        int rc = 0;

        if(!isGoogleMapsInstalled())
            rc = R.string.gmap_no_gmap;
        else if(!isConnectionAvailable() || !isNetworkAvailable())
            rc = R.string.gmap_no_connection;
        else if(!googleServicesOK())
            rc = R.string.gmap_service_error;

        return rc;
    }

    // speed in unit (min/max)
    public static String getSpeedStr(double minSpeed, double maxSpeed)
    {
        return ( (int) (minSpeed * sUnit[sCurrUnit]) ) + "/" + ( (int) (maxSpeed * sUnit[sCurrUnit])) + " " + sUnitStr[sCurrUnit];
    }

    // speed in unit
    public static String getSpeedStr(double speed)
    {
        return ( (int) (speed * sUnit[sCurrUnit]) ) + " " + sUnitStr[sCurrUnit];
    }

    // distance in unit

    public static String getDistanceInUnit(int m)
    {
        String s = "";

        if(sCurrUnit == 0)
        {
            if(m < 1000)
                s = m + " m";
            else
                s = String.format("%.03f Km", (m / 1000.0));
        }
        else
        {
            s = String.format("%.02f m", (m * 0.00062137d));
        }

        return s;
    }

    // time in currUnit

    public static String getTimeString(long t)
    {
        sJv.setTime(t * 1000);
        return (String) DateFormat.format(sTimeFmt[sCurrUnit], sJv);
    }

    // date in currUnit

    public static String getShortDateString(long t)
    {
        sJv.setTime(t * 1000);
        return (String) DateFormat.format("MMM dd", sJv);
    }

    // implode like utility function

    public static String implode(String separator, List<String> data)
    {
        StringBuilder sb = new StringBuilder();
        int z = 0;
        for(int i = 0; i < data.size(); i++)
        {
            //data.length - 1 => to not add separator at the end
            if(!data.get(i).matches(" *"))
            {
                if(z > 0)
                    sb.append(separator);
                //empty string are ""; " "; "  "; and so on
                sb.append(data.get(i));
                ++z;
            }
        }

        return sb.toString();
    }


    // static function used for G map

    public static boolean isGoogleMapsInstalled()
    {
        try
        {
            ApplicationInfo info = YaV1.sContext.getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0);
            return true;
        }
        catch(PackageManager.NameNotFoundException e)
        {
            return false;
        }
    }

    // check connection to internet

    public static boolean isNetworkAvailable()
    {
        ConnectivityManager connectivityManager = (ConnectivityManager) YaV1.sContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null;
    }

    // check for connection (either wifi or mobile if authorized)

    public static boolean isConnectionAvailable()
    {
        ConnectivityManager connManager = (ConnectivityManager) YaV1.sContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if(mWifi.isConnected())
            return true;

        // if we do not allow using mobile data, we return false here

        if(!YaV1.sPrefs.getBoolean("wifi_only", true))
        {
            ConnectivityManager connManager1 = (ConnectivityManager)  YaV1.sContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mMobile = connManager1.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

            if(mMobile != null && mMobile.isConnected())
                return true;
        }

        return false;
    }

    // check for Google map service

    public static boolean googleServicesOK()
    {
        int isAvailable = GooglePlayServicesUtil.isGooglePlayServicesAvailable(YaV1.sContext);

        if (isAvailable == ConnectionResult.SUCCESS)
            return true;
        return false;
    }
}
