package com.franckyl.yav1;

import com.franckyl.yav1lib.YaV1Alert;

/**
 * Created by franck on 9/8/13.
 */
public class YaV1CurrentView
{
    public static byte  sBogey0          = 0;
    public static byte  sBogey1          = 0;

    public static byte  sArrow0          = 0;
    public static byte  sArrow1          = 0;

    public static int   sSignal          = 0;

    //public static int[] sSignalDir       = {0,0,0};

    // used for the current alert packet -- Alert not muted only --
    /*
    public static int[]     sNotMutedAlertCount     = {0,0,0,0,0};
    public static int[]     sNotMutedLastAlertCount = {0,0,0,0,0};
    public static int[]     sNotMutedBandSignal     = {0,0,0,0,0};
    public static int[]     sNotMutedLastBandSignal = {0,0,0,0,0};
    public static int       sNotMutedMaxSignal      = 0;
    */
    // for all alerts

    public static int[]     sAlertCount        = {0,0,0,0,0};
    public static int[]     sNewAlert          = {0,0,0,0,0};
    public static int[]     sInboxAlert        = {0,0,0,0,0};
    public static int[]     sDirectionStrength = {0,0,0,0,0};
    public static boolean   sHasLaser          = false;
    public static int       sAlertOverlay      = 0;
    public static int       sTotalInbox        = 0;
    public static int       sTotalAlert        = 0;

    public static void resetBeforeProcess()
    {
        for(int i=0; i<5; i++)
        {
            sDirectionStrength[i] = 0;
            sNewAlert[i]          = 0;
            sAlertCount[i]        = 0;
            sInboxAlert[i]        = 0;
            sHasLaser             = false;
            sAlertOverlay         = 0;
            sTotalInbox           = 0;
            sTotalAlert           = 0;
        }
    }

    // set the max signal for the given direction

    public static void setDirectionStrength(int dir, int st)
    {
        sDirectionStrength[dir] = Math.max(sDirectionStrength[dir], st);
    }

    // set the inBox for the band

    public static void setInboxBand(int band)
    {
        sInboxAlert[band]++;
        sTotalInbox++;
    }

    // set the alert count per band

    public static void setAlertBand(int band)
    {
        sAlertCount[band]++;
        sTotalAlert++;
        if(band == YaV1Alert.BAND_LASER)
            sHasLaser = true;
    }
}
