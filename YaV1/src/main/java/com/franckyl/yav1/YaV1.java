package com.franckyl.yav1;

import android.app.AlertDialog;
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.franckyl.yav1.events.AlertEvent;
import com.franckyl.yav1.events.InfoEvent;
import com.franckyl.yav1.events.YaV1Event;
import com.franckyl.yav1.lockout.LockoutData;
import com.franckyl.yav1.lockout.LockoutOverride;
import com.franckyl.yav1.lockout.LockoutParam;
import com.franckyl.yav1.utils.MemoryBoss;
import com.franckyl.yav1.utils.YaV1DbgLogger;
import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1lib.YaV1AlertList;
import com.squareup.otto.ThreadEnforcer;
import com.valentine.esp.ValentineClient;
import com.valentine.esp.constants.Devices;
import com.valentine.esp.data.ModeData;
import com.valentine.esp.data.SavvyStatus;

import java.io.File;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.squareup.otto.Bus;
import com.valentine.esp.utilities.V1VersionSettingLookup;

/**
 * Created by franck on 6/28/13.
 */

public class YaV1 extends Application
{
    public static ValentineClient       mV1Client      = null;
    public static BluetoothDevice       sDevice         = null;
    public static String                sDeviceName     = "";
    public static boolean               sDemoEnable     = false;
    public static String                sDeviceAddr     = "";
    public static int                   nbStart         = 0;
    public static boolean               isClientStarted = false;
    public static YaV1Activity.WaitV1Thread v1Wait      = null;

    public static String                PACKAGE_NAME    = "";
    public static String                sStorageDir     = "";
    public static ModeData              sModeData       = null;
    public static String                sV1Version      = "";
    public static String                sV1Serial       = "";
    public static String                sSavvyVersion   = "";
    public static boolean               sLegacy         = false;
    public static Context               sContext;
    public static SharedPreferences     sPrefs;
    public static YaV1SweepSets         sSweep          = null;
    public static YaV1SettingSet        sV1Settings     = null;
    public static String                VERSION_NAME    = "";
    public static int                   sCurrentActivity = 0;
    public static boolean               sOverlayVisible = false;
    public static YaV1Logger            sLog            = null;
    public static YaV1                  sInstance       = null;

    public static double   sUnits[]      = {3.6d, 2.23693629d};
    public static int      sCurrUnit     = 0;
    public static String   sUnitLabel[]  = {"Kmh", "MPH"};
    public static Typeface sDigital      = null;

    // the V1 mode strings and bytes

    public static  String  mModeText[][] = {{"A", "&", "L"},
                                             {"C", "c", ""},
                                             {"U", "u", ""}};

    public static  byte    mModeByte[][] = { {119, 24, 56},
                                             {57, 88, 0},
                                             {62, 28, 0}};


    // the auto lockout

    public static LockoutData       sAutoLockout        = null;
    public static LockoutOverride   sLockoutOverride    = null;

    static public final  int            APP_ID          = 2907;

    // our services
    public static ComponentName         sAlertService = null;
    public static ComponentName         sGpsService   = null;

    // multi purpose looper (soundManager, GpsListener)

    static public YaV1Looper   sLooper  = null;

    // when we wait for V1
    public static AtomicBoolean mInWait = new AtomicBoolean(false);

    // flag if we have BluetoothGpsApp

    public static boolean               sHasBluetoothGpsApp = false;

    // save muting and display before going dark

    public static boolean               sDarkMute           = false;
    public static boolean               sDarkDisplay        = false;

    // Debug logger
    public static YaV1DbgLogger         sDbgLog             = null;

    // our Memory boss object

    public static MemoryBoss            sMemoryBoss         = null;
    public static boolean               sInTestingMode      = false;
    public static boolean               sScreenOn           = true;
    public static boolean               sIsLocked           = false;

    // expert mode
    public static boolean               sExpert             = false;
    public static Float                 sBatteryVoltage     = new Float(0.0);

    // A number format that can be used for formatting number in English Locale
    public static NumberFormat          sNF                 = NumberFormat.getInstance(Locale.ENGLISH);

    // the otto bus
    private static Bus                  sBus                = null;

    // the current alert list available for all view
    public  static YaV1AlertList        sAlertForView       = new YaV1AlertList();

    private static AlertEvent           sAlertEvent         = new AlertEvent(AlertEvent.Type.V1_ALERT);
    private static AlertEvent           sAlertEventOverlay  = new AlertEvent(AlertEvent.Type.V1_ALERT_OVERLAY);

    // the override savvy speed
    public  static int                  sSavvySpeedOverride = 0;

    // testing font increase ratio
    public  static float                sFontFrequencyRatio = 0f;

    // new in ESP V2 library
    public  static V1VersionSettingLookup sV1Lookup         = new V1VersionSettingLookup();

    // create the application

    public void onCreate()
    {
        super.onCreate();
        sContext     = getApplicationContext();
        sInstance    = this;

        sNF.setMaximumFractionDigits(8);
        sNF.setMinimumFractionDigits(7);

        if(sDigital == null)
            sDigital = Typeface.createFromAsset(this.getAssets(), "yav1_digib.ttf");

        sBus = new Bus(ThreadEnforcer.ANY);

        if(nbStart == 0)
        {
            YaV1RealSavvy.reset(false);
            sSavvyVersion = "";
            sPrefs       = YaV1PreferenceActivity.getYaV1Preference();
            PACKAGE_NAME = getApplicationContext().getPackageName();
            File sDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + PACKAGE_NAME);
            if(sDir.isDirectory() || sDir.mkdirs())
            {
                sStorageDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + PACKAGE_NAME;
                // check for the backup dir
                sDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + PACKAGE_NAME + "/" + "backup");
                if(!sDir.isDirectory())
                    sDir.mkdirs();
            }
            else
                sStorageDir = getString(R.string.no_storage);

            try
            {
                VERSION_NAME = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            }
            catch(PackageManager.NameNotFoundException exp)
            {
                VERSION_NAME = "???";
            }
        }

        // start our memory boss if possible
        nbStart++;
    }

    // return the event Bus

    public static Bus getEventBus()
    {
        return sBus;
    }

    // post an event on the Bud

    public static void postEvent(YaV1Event evt)
    {
        sBus.post(evt);
    }

    public static void newAlert(YaV1AlertList il)
    {
        synchronized(sAlertForView)
        {
            sAlertForView = il;
        }
        postEvent(sAlertEvent);
    }

    public static void newAlertOverlay(YaV1AlertList il)
    {
        synchronized(sAlertForView)
        {
            sAlertForView = il;
        }
        postEvent(sAlertEventOverlay);
    }

    public static void getNewAlert(YaV1AlertList il)
    {
        synchronized(sAlertForView)
        {
            il.clear();
            il.addAll(sAlertForView);
        }
    }

    // start/stop the alert service

    public static void startAlertService(boolean on)
    {
        if(on)
        {
            if(sAlertService == null)
                sAlertService = sContext.startService(new Intent(sContext, YaV1AlertService.class));
        }
        else
        {
            if(sAlertService != null)
            {
                sContext.stopService(new Intent(sContext, YaV1AlertService.class));
                sAlertService = null;
            }
        }
    }

    // start/stop the gps service

    public static void startGpsService(boolean on)
    {
        if(on)
        {
            if(sGpsService == null)
                sGpsService = sContext.startService(new Intent(sContext, YaV1GpsService.class));
        }
        else
        {
            if(sGpsService != null)
            {
                sContext.stopService(new Intent(sContext, YaV1GpsService.class));
                sGpsService = null;
            }
        }
    }

    // get the row from the mode (0 => USa, 1 => Euro Custom sweep, 2 => Euro)

    private static int getRowMode()
    {
        if(sModeData != null)
        {
            return (sModeData.getUsaMode() ? 0 : sModeData.getCustomSweeps() ? 1 : 2);
        }
        return 0;
    }

    // get the columns in the current row for the mode

    private static int getColMode(int row)
    {
        if(sModeData != null)
        {
            if(row == 0)
                return (sModeData.getAllBogeysMode() ? 0 : sModeData.getLogicMode() ? 1 : 2);
            return (sModeData.getLogicMode() ? 1 : 0);
        }

        return 0;
    }

    // get the modeByte from the mode

    public static byte getModeByte()
    {
        if(sModeData == null)
            return 0;

        int i = getRowMode();
        int j = getColMode(i);
        return mModeByte[i][j];
    }

    // get the modeText from the mode

    public static String getModeText()
    {
        if(sModeData == null)
            return "";

        int i = getRowMode();
        int j = getColMode(i);
        return mModeText[i][j];
    }

    // get the modeText from the mode

    public static int getNextMode()
    {
        if(sModeData == null)
            return -1;

        int i = getRowMode();
        int j = getColMode(i);

        // mode is given by j + 1
        j++;
        // next is j + 1
        j++;

        if( (i == 0 && j > 3) || (i > 0 && j > 2))
            j = 1;

        return j;
    }

    public static void refreshSettings()
    {
        sCurrUnit       = Integer.valueOf(sPrefs.getString("speed_unit", "0"));
    }

    public static double getSpeedFromMs(double ms)
    {
        return ms * sUnits[sCurrUnit];
    }

    public static double getMsFromSpeed(int sp)
    {
        return Math.round( ( (double) sp / sUnits[sCurrUnit]) * 100) / 100;
    }

    public static void superResume()
    {
        sCurrentActivity++;
        Log.d("Valentine Overlay", "superResume: " + sCurrentActivity);
        if(YaV1Activity.sOverlay != null)
        {
            Log.d("Valentine Overlay", "superResume Hiding overlay");
            YaV1Activity.sOverlay.registerAlert(false);
            YaV1Activity.sOverlay.hideFromExternal();
        }
    }

    public static void superPause()
    {
        if(sMemoryBoss != null)
        {
            sCurrentActivity--;
            if(sCurrentActivity < 1 && YaV1Activity.sOverlay != null)
                YaV1Activity.sOverlay.registerAlert(true);
        }
        Log.d("Valentine Overlay", "superPause: " + sCurrentActivity);
    }

    public static void backgroundFromBoss()
    {
        sCurrentActivity = 0;
        if(YaV1Activity.sOverlay != null)
            YaV1Activity.sOverlay.registerAlert(true);
        Log.d("Valentine Overlay", "backgroundFromBoss: " + sCurrentActivity);
    }

    public static boolean isInBackground()
    {
        /*
        if(sMemoryBoss == null)
        {
            if(sCurrentActivity > 0 && YaV1Activity.sOverlay != null && YaV1Activity.sOverlay.isVisible())
            {
                YaV1Activity.sOverlay.hideFromExternal();
            }
        }
        */
        return sCurrentActivity == 0;
    }

    // check if we can send custom sweep

    public static boolean customPossible()
    {
        return (sModeData != null && sModeData.getEuroMode());
    }

    // static function that checks the storage

    public static boolean checkStorage(Context context, String folder)
    {
        // check fist if we can write on phone
        File sDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + YaV1.PACKAGE_NAME + "/" + (!folder.equals("") ? "/" + folder + "/" : ""));
        if(sDir.isDirectory() || sDir.mkdirs())
            return true;

        // we issue an error
        new AlertDialog.Builder(context)
                .setTitle(R.string.error)
                .setMessage(R.string.no_storage_available)
                .setPositiveButton(R.string.ok, null).show();

        return false;
    }

    // Get some info from V1

    public void getV1Info()
    {
        // if no version, we request the version
        if (sV1Version.equals(""))
        {
            Log.d("Valentine", "Version requested");
            mV1Client.getV1Version(this, "v1VersionCallback");
        }

        // if no serial, we request the serial
        if (sV1Serial.equals(""))
        {
            Log.d("Valentine", "Serial requested");
            mV1Client.getV1SerialNumber(this, "v1SerialCallback");
        }
    }

    // retrieve the V1 settings

    public void getV1Setting()
    {
        Log.d("Valentine", "Request V1 settings");
        // we request the userSettings
        mV1Client.getUserSettings(YaV1.sV1Settings, "setCurrentUserSettingCallback");
    }

    // query the custom sweeps

    public boolean getSweeps()
    {
        // we request the Custom Sweep (if any)
        if(sModeData.getEuroMode())
        {
            if(sModeData.getCustomSweeps())
            {
                if(YaV1.sSweep.sNbRequestSweepSection == 0)
                {
                    Log.d("Valentine", "Request sweep section");
                    mV1Client.getSweepSections(YaV1.sSweep, "setSweepSectionCallback");
                }
                else
                {
                    mV1Client.getAllSweeps(true, YaV1.sSweep, "setCurrentSweepCallback");
                    Log.d("Valentine", "Request sweep from V1");
                }
                return true;
            }
            // default will be the factory one
            sSweep.setDefaultFactory();
        }
        else
            sSweep.setNoCurrent();

        // update the current sweep names
        Log.d("Valentine", "No sweep mode U");
        YaV1.postEvent(new InfoEvent(InfoEvent.Type.V1_INFO));
        return false;
    }

    // Version call back

    public void v1VersionCallback(String version)
    {        // if the version was not set or the version is different
        sV1Version = version;
        Log.d("Valentine", "V1 version is " + version);
        sV1Lookup.setV1Version(sV1Version);
    }

    // Serial call back

    public void v1SerialCallback(String serial)
    {
        sV1Serial = serial;
        Log.d("Valentine", "V1 serial is " + sV1Serial);

        YaV1RealSavvy.reset(false);

        if(sSavvyVersion.equals(""))
        {
            YaV1RealSavvy.reset(false);
            mV1Client.getSerialNumber(this, "accessoryCallback", Devices.SAVVY);
        }
    }

    // Savvy serial callback

    public void accessoryCallback(String serial)
    {
        sSavvyVersion = ( serial == null ? "" : serial);
        if(serial != null && serial.length() > 2)
        {
            YaV1RealSavvy.reset(true);
            mV1Client.getSavvyStatus(this, "setCurrentSavvyCallback");
        }
        else
            YaV1RealSavvy.reset(false);
    }

    // Battery call back

    // handle the battery Voltage

    public void v1BatteryCallBack(Float volts)
    {
        // store the value, in YaV1

        sBatteryVoltage = volts;

        // request the display of the screen activity
        postEvent(new InfoEvent(InfoEvent.Type.V1_INFO));
    }


    // savvy status callback

    public void setCurrentSavvyCallback(SavvyStatus iStatus)
    {
        YaV1RealSavvy.setSavvyStatus(iStatus);
    }

    // Debug log

    public static void DbgLog(String s)
    {
        if(sDbgLog != null)
            sDbgLog.dbgLog(s);
    }

    // Debug log

    public static void DbgLog(String t, String s)
    {
        if(sDbgLog != null)
            sDbgLog.dbgLog(t + " " + s);
    }

    // called when application enters (first time)

    public static void appEnter()
    {
        YaV1.sScreenOn = true;
        YaV1.sIsLocked = false;

        if(sMemoryBoss == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        {
            sMemoryBoss = new MemoryBoss();
            sInstance.registerComponentCallbacks(sMemoryBoss);
        }
    }

    // called when application is finishing

    public static void appExit()
    {
        // we exit, reset the manual speed for savvy
        sSavvySpeedOverride = 0;
        sFontFrequencyRatio = 0;

        if(sMemoryBoss != null)
        {
            sInstance.unregisterComponentCallbacks(sMemoryBoss);
            sMemoryBoss = null;
        }
    }

    // force media MTp

    public static void forceMedia(File iFile)
    {
        if(iFile != null)
        {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            {
                sInstance.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                            Uri.fromFile(iFile)));

            }
            else
            {
                sInstance.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED,
                                        Uri.fromFile(iFile)));
            }
        }
    }

    // static member to measure an item width

    public static int measureItemWidth(Context context, int layout, int elem, float factor)
    {
        int m = 0;

        View view = null;
        FrameLayout fakeParent = new FrameLayout(context);

        // inflate the layout
        View convertView = LayoutInflater.from(context).inflate(layout, fakeParent, false);

        if(elem != 0)
        {
            View e = convertView.findViewById(elem);
            e.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            m = e.getMeasuredWidth();
        }
        else
        {
            convertView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            m = convertView.getMeasuredWidth();
        }

        return (int) (m*factor);
    }

    // utility inner class for Band Frequency boundaries

    public static class BandBoundaries
    {
        public static final Map<Integer, Pair<Integer, Integer>> mEdges;
        public static final Map<String, Integer> mBandStrToInt;

        static
        {
            Map<Integer, Pair<Integer, Integer>> mP = new HashMap<Integer, Pair<Integer, Integer>>();
            mP.put(YaV1Alert.BAND_LASER, new Pair<Integer, Integer>(0, 0));
            mP.put(YaV1Alert.BAND_KA, new Pair<Integer, Integer>( (int) (sV1Lookup.getDefaultLowerEdge(V1VersionSettingLookup.V1_Band.Ka)*1000),
                                                                  (int) (sV1Lookup.getDefaultUpperEdge(V1VersionSettingLookup.V1_Band.Ka)*1000)));

            mP.put(YaV1Alert.BAND_K, new Pair<Integer, Integer>( (int) (sV1Lookup.getDefaultLowerEdge(V1VersionSettingLookup.V1_Band.K)*1000),
                                                                 (int) (sV1Lookup.getDefaultUpperEdge(V1VersionSettingLookup.V1_Band.K)*1000)));

            mP.put(YaV1Alert.BAND_X, new Pair<Integer, Integer>( (int) (sV1Lookup.getDefaultLowerEdge(V1VersionSettingLookup.V1_Band.X)*1000),
                                                                 (int) (sV1Lookup.getDefaultUpperEdge(V1VersionSettingLookup.V1_Band.X)*1000)));

            mP.put(YaV1Alert.BAND_KU, new Pair<Integer, Integer>( (int) (sV1Lookup.getDefaultLowerEdge(V1VersionSettingLookup.V1_Band.Ku)*1000),
                                                                  (int) (sV1Lookup.getDefaultUpperEdge(V1VersionSettingLookup.V1_Band.Ku)*1000)));
            mEdges = Collections.unmodifiableMap(mP);

            Map<String, Integer> mStrInt = new HashMap<String, Integer>();
            mStrInt.put("LASER", 0);
            mStrInt.put("KA", 1);
            mStrInt.put("K", 2);
            mStrInt.put("X", 3);
            mStrInt.put("KU", 4);
            mBandStrToInt = Collections.unmodifiableMap(mStrInt);
        }

        public static Pair<Integer, Integer> getEdges(int band)
        {
            return mEdges.get(band);
        }

        public static Pair<Integer, Integer> getEdges(String band)
        {
            Integer i = mBandStrToInt.get(band.toUpperCase());

            if(i != null)
                return getEdges(i.intValue());

            return null;
        }

        public static int getBandIntFromStr(String s)
        {
            Integer i = mBandStrToInt.get(s.toUpperCase());

            if(i != null)
                return i.intValue();

            return -1;
        }

        public static String getBandStrFromInt(int i)
        {
            for(Map.Entry<String, Integer> entry: mBandStrToInt.entrySet())
            {
                if(entry.getValue() == i)
                    return entry.getKey();
            }
            return "";
        }

        public static boolean checkValid(int b, int freq)
        {
            if(mEdges.get(b) == null || freq < mEdges.get(b).first || freq > mEdges.get(b).second)
                return false;
            return true;
        }

        public static boolean checkValid(String b, int freq)
        {
            if(mBandStrToInt.get(b.toUpperCase()) != null)
                return checkValid(mBandStrToInt.get(b.toUpperCase()), freq);
            return false;
        }

        // return the integer band number from a frequency

        public static int getIntBandFromFrequency(int freq)
        {
            int i = -1;
            for(Map.Entry<Integer, Pair<Integer, Integer>> entry: mEdges.entrySet())
            {
                Pair<Integer, Integer> p = entry.getValue();
                if(freq >= p.first && freq <= p.second)
                {
                    i = entry.getKey();
                    break;
                }
            }

            return i;
        }

        public static String getStrBandFromFrequency(int freq)
        {
            int i = getIntBandFromFrequency(freq);
            if(i >= 0)
            {
                for(Map.Entry<String, Integer> entry: mBandStrToInt.entrySet())
                {
                    if(entry.getValue() == i)
                        return entry.getKey();
                }
            }
            return "";
        }
    }
}
