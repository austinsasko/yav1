package com.franckyl.yav1;

/**
 * Created by franck on 6/28/13.
 */

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.franckyl.yav1.events.GpsEvent;
import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1lib.YaV1AlertList;
import com.valentine.esp.ValentineClient;
import com.valentine.esp.data.InfDisplayInfoData;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.franckyl.yav1.YaV1.sPrefs;


public class YaV1AlertService extends Service
{
    // Update V1 Receiver

    public static final int V1_GETMODE_DATA   = 0;
    public static final int V1_GETMODE_DATAR  = 1;
    public static final int V1_REBIND         = 5;

    // recorded/lockout color define

    public static final int BCK_COLOR_RECORDED = 0;
    public static final int BCK_COLOR_LOCKOUT  = 1;

    private static boolean mActive   = false;
    private boolean mAlertDataOn;
    private boolean mCallInstalled   = false;

    // sound / display of the V1 Device
    private boolean mMuteOn               = false;
    private AtomicBoolean  mDisplayOn     = new AtomicBoolean(true);
    private static int     mRequestSweep  = 0;

    // to manage the muting

    private boolean mMuteRequested          = false;
    private static boolean mMuteSpeed       = false;
    private static AtomicBoolean mMuteByUser= new AtomicBoolean(false);
    private boolean mCurrentMute            = false;

    // Gps control global alert flag
    private boolean mGotAlert               = false;

    // service start/stop sound
    private String mServiceSound         = sPrefs.getString("active_sound", "content://settings/system/notification_sound");

    // when laser is present
    private AtomicBoolean haveLaser         = new AtomicBoolean(false);

    public static final Handler mHandler    = new Handler();

    private YaV1AlertList mAlertList        = new YaV1AlertList();
    private YaV1AlertList mEmptyList        = new YaV1AlertList();

    private YaV1Alert laser                 = new YaV1Alert();

    private final IBinder mBinder           = new LocalBinder();

    private int   mClientCount                  = 0;

    public  static  boolean mDemo               = false;
    private boolean mTimedOut                   = false;

    // Max speed for auto turn off /on

    private static double   mSpeedMaxForSound   = 0.0;
    public  static int      mSavvySpeedAsInt    = 0;
    private static boolean  mSavvy              = false;
    private static boolean  mExcludeKaSavvy     = false;
    private static boolean  mExcludeLaserSavvy  = false;

    private boolean  mInSavvy                   = false;

    // early laser ?
    private boolean      mEarlyLaser            = true;

    // alert processor
    public  static YaV1AlertProcessor mProcessor    = new YaV1AlertProcessor();
    // sound processor
    private static YaV1SoundManager   mSoundManager = null;

    // smart dark mode
    private static boolean      mSmartDarkMode      = false;
    private static long         SmartDarkTimestamp  = 0;

    // V1View comparator
    YaV1Alert.ChainedComparator mSortAlert[]   = {null, null, null};
    private static int          mApplySort     = 0;
    private static boolean      mSortAlertView = false;

    // Update Receiver (when updating Sweeps/Usersettings)

    private BroadcastReceiver mUpdateV1Receiver = null;
    private BroadcastReceiver mScreenReceiver   = null;

    private V1EventReceiver   mV1EventReceiver  = null;

    private int     mCurrentView    = 0;

    // variable used for muting

    private int     mNbLastMuted   = 0;
    private int     mNbMuted       = 0;
    //private boolean mRetryMute     = false;
    private int     mLastCmdMute   = 0;
    private long    mLastCmdTime[] = {0, 0};
    private long    mCmdDelay[]    = {500, 1300};
    //private boolean mMuteOffResent = false;

    // our overlay view

    private static boolean mOverlayForInbox  = false;
    private static boolean mOverlayEnabled   = false;
    private static boolean mOverlayHideSavvy = false;
    private static boolean mOverlaySticky    = false;


    private boolean        mStopping        = false;

    // battery voltage request

    public  static boolean sBatteryVoltage  = false;
    private static long    sBatteryLastTime = 0;

    private GpsEvent       mSavvySpeedEvent    = new GpsEvent(GpsEvent.Type.FROMSAVVY);
    private boolean        mRequestVersionSent = false;
    private boolean        mRequestSettingSent = false;

    // for debug

    static int processAlertCount            = 0;

    // box muting settings
    public static int             mPrefMuteKa;
    public static int             mPrefMuteK;
    public static int             mPrefMuteX;
    public static int             mPrefMuteKu;


    @Override
    public void onCreate()
    {
        super.onCreate();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        v1Notify(true);

        // Receiver for update (when pushing sweep, or changing mode, or user settings)

        if(mUpdateV1Receiver == null)
        {
            mUpdateV1Receiver = new BroadcastReceiver()
            {
                @Override
                public void onReceive(Context context, Intent intent)
                {
                    // get the int extra, if 1 we suspend if 0, we restart
                    int        suspend   = intent.getIntExtra("Suspend", 0);

                    if( suspend == V1_REBIND)
                    {
                        turnOffOn();
                    }
                }
            };

            // receiver for v1 status update
            LocalBroadcastManager.getInstance(YaV1.sContext).registerReceiver(mUpdateV1Receiver, new IntentFilter("YaV1Push"));
        }

        // the screen receiver
        if(mScreenReceiver == null)
        {
            mScreenReceiver = new BroadcastReceiver()
            {
                @Override
                public void onReceive(Context context, Intent intent)
                {
                    if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF))
                    {
                        Log.d("Valentine", "Screen is Off");
                        // do whatever you need to do here
                        YaV1.sScreenOn = false;
                    }
                    else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON))
                    {
                        // and do whatever you need to do here
                        YaV1.sScreenOn = true;
                        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                        YaV1.sIsLocked = km.inKeyguardRestrictedInputMode();
                        Log.d("Valentine", "Screen is On Locked " + YaV1.sIsLocked);
                    }
                    else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT))
                    {
                        // and do whatever you need to do here
                        YaV1.sIsLocked = false;
                        Log.d("Valentine", "Screen is On Locked - user present action " + YaV1.sIsLocked);
                    }
                    else if (intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION"))
                    {
                        Bundle b = intent.getExtras();
                        int t = b.getInt("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                        int v = b.getInt("android.media.EXTRA_VOLUME_VALUE", -1);
                        Log.d("Valentine", "Ringer changed " + intent.toString() + " Stream type " + t + " Stream value " + v);
                    }
                }
            };

            // register the receiver
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction("android.media.VOLUME_CHANGED_ACTION");
            registerReceiver(mScreenReceiver, filter);
        }

        // sound Manager
        mSoundManager = new YaV1SoundManager();

        // Alert comparators
        if(mSortAlert[0] == null)
        {
            mSortAlert[0] = new YaV1Alert.ChainedComparator(YaV1Alert.AlertComparator.ID_EX_LOCKOUT,
                                                            YaV1Alert.AlertComparator.ID_SIGNAL,
                                                            YaV1Alert.AlertComparator.ID_BAND);
            mSortAlert[1] = new YaV1Alert.ChainedComparator(YaV1Alert.AlertComparator.ID_EX_LOCKOUT,
                                                            YaV1Alert.AlertComparator.ID_BAND,
                                                            YaV1Alert.AlertComparator.ID_SIGNAL);
            mSortAlert[2] = new YaV1Alert.ChainedComparator(YaV1Alert.AlertComparator.ID_EX_LOCKOUT,
                                                            YaV1Alert.AlertComparator.ID_INBOX,
                                                            YaV1Alert.AlertComparator.ID_SIGNAL);
        }

        mMuteByUser.set(false);

        refreshSettings(true);

        // event receiver
        if(mV1EventReceiver == null)
        {
            mV1EventReceiver = new V1EventReceiver();
            LocalBroadcastManager.getInstance(YaV1.sContext).registerReceiver(mV1EventReceiver, new IntentFilter("V1ESP"));
        }
        return (START_NOT_STICKY);
    }

    // our V1 Event receiver
    private class V1EventReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            int evt = intent.getIntExtra("event", 0);
            Log.d("Valentine", "V1 Event received " + evt);

            switch(evt)
            {
                case ValentineClient.V1_NO_DATA:
                    onV1NoDataCallback("V1 no data");
                    break;
                case ValentineClient.V1_DEMO_END:
                    Log.d("Valentine", "Stop demo event received mDemo " + mDemo);
                    if(mDemo)
                    {
                        setDemoMode(false);
                        // call stopDemo of V1 client and restart the activity
                        mHandler.post(new Runnable() {
                            public void run() {
                                Intent intent = new Intent(YaV1.sContext, YaV1Activity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.putExtra("retry", true);
                                YaV1.sContext.startActivity(intent);
                            }
                        });
                    }
                    break;
                case ValentineClient.V1_STOP:
                    break;
                default:
                    Log.d("Valentine", "Unhandled V1 event receive " + evt);
            }
        }
    }

    // request the call to check for sweep

    public static void requestSweep()
    {
        mRequestSweep  = 1;
    }

    // send a mute request in safe way

    private boolean sendMuteRequest(int onOff)
    {
        long cmd = System.currentTimeMillis();

        if(cmd -  mLastCmdTime[onOff] > mCmdDelay[onOff])
        {
            forceMute( (onOff > 0  ? true : false));
            Log.d("Valentine Sound", "Muting request " + onOff + " Has been sent last was " + (cmd - mLastCmdTime[onOff]));
            mLastCmdTime[onOff] = cmd;
            // reset last time command sent
            mLastCmdTime[(onOff > 0 ? 0 : 1)] = 0;
            mLastCmdMute = onOff;
            return true;
        }

        return false;
    }

    // return true if we have to check the requested mute

    private boolean muteSavvy(int requested)
    {
        int onOffSpeed  = (mMuteSpeed || mMuteByUser.get() ? 1 : 0);

        if(mGotAlert)
        {
            if(onOffSpeed > 0)
            {
                mInSavvy = true;
                if(!mCurrentMute)
                {
                    Log.d("Valentine Sound", "onOffSpeed > 0 - current mute false, sending mute request");
                    sendMuteRequest(onOffSpeed);
                }
                return false;
            }

            // we are under speed or requested, if we were in Savvy, un mute if we have alerts that are not all muted\

            if(mInSavvy)
            {
                // not in savvy anymore
                mInSavvy  = false;
                if(mCurrentMute && requested < 1)
                {
                    //Log.d("Valentine Sound", "We are in Savvy and now exit");
                    if(sendMuteRequest(onOffSpeed))
                    {
                        mNbLastMuted    = 0;
                        return false;
                    }
                }
            }
        }
        else
        {
            //Log.d("Valentine Sound", "In savvy not alert");
            if(onOffSpeed < 1)
                mInSavvy = false;
        }

        return true;
    }

    private void mute()
    {
        // check if we need to mute
        int onOffSpeed  = (mMuteSpeed || mMuteByUser.get() ? 1 : 0);
        int onOff       = (mMuteRequested? 1 : 0);

        mSoundManager.playAll(onOff > 0 || onOffSpeed > 0);

        boolean rc = muteSavvy(onOff);

        if(rc)
        {
            if(!mGotAlert)
            {
                mLastCmdMute = 0;
                mNbLastMuted = 0;
            }
            else
            {
                if(mLastCmdMute != onOff || (onOff > 0 && mNbMuted > mNbLastMuted))
                {
                    if(onOff > 0)
                    {
                        if(sendMuteRequest(onOff))
                        {
                            mNbLastMuted = mNbMuted;
                        }
                        else
                        {
                            // we might have less muted than before
                            if(mNbLastMuted > mNbMuted)
                                mNbLastMuted = mNbMuted;
                        }
                    }
                    else
                    {
                        if(sendMuteRequest(onOff))
                        {
                            mNbLastMuted = 0;
                        }
                    }
                }
            }
        }

        // if speed request or user requested and soundIcon not right, we broadcast

        if(mMuteOn != mMuteSpeed)
        {
            mMuteOn = mMuteSpeed;
            Intent sIntent = new Intent("V1Display");
            LocalBroadcastManager.getInstance(YaV1.sContext).sendBroadcast(sIntent);
        }
    }

    @Override
    public void onDestroy()
    {
        Log.d("Valentine", "In Service Destroy");
        // remove the V1Status Receiver
        if(mUpdateV1Receiver != null)
            LocalBroadcastManager.getInstance(YaV1.sContext).unregisterReceiver(mUpdateV1Receiver);

        if(mV1EventReceiver != null)
            LocalBroadcastManager.getInstance(YaV1.sContext).unregisterReceiver(mV1EventReceiver);

        if(mScreenReceiver != null)
            unregisterReceiver(mScreenReceiver);

        stop();
        super.onDestroy();
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder
    {
        YaV1AlertService getService()
        {
            // Return this instance of the service
            return YaV1AlertService.this;
        }
    };

    // return this service for the client

    public IBinder onBind(Intent intent)
    {
        // reset to fire an empty list
        mClientCount++;
        return mBinder;
    }

    // client unbinding
    public boolean onUnbind(Intent intent)
    {
        mClientCount--;
        return true;
    }

    // client re binding
    public void onRebind(Intent intent)
    {
        // reset to fire an empty list
        mClientCount++;
    }

    // set the current view

    public void setCurrentView(int lView)
    {
        if(mCurrentView != lView && mCurrentView != YaV1ScreenActivity.sToolTabIndex)
        {
            // we broadcast an empty list to clear the remaining alerts
            //Intent intent = new Intent(YaV1ScreenActivity.sIntentNames[mCurrentView]);
            //intent.putExtra("V1Alert", (Parcelable) mEmptyList);
            //LocalBroadcastManager.getInstance(YaV1.sContext).sendBroadcast(intent);
        }

        mCurrentView = lView;
        mResend      = true;
    }

    // set the Savvy speed

    public boolean setSavvySpeed(int s)
    {
        if(mSavvy)
        {
            mSavvySpeedAsInt         = s;
            mSpeedMaxForSound        = YaV1.getMsFromSpeed(s);
            // save the value at app level
            YaV1.sSavvySpeedOverride = s;
            return true;
        }

        return false;
    }

    // get the settings when a client bind/rebind (might have changed preferences)

    public static void refreshSettings(boolean init)
    {
        mSavvy                = sPrefs.getBoolean("use_gps", false) && sPrefs.getBoolean("mute_v1_under_speed", false) /* && mSpeedMaxForSound > 0.5*/;

        if(mSavvy)
        {
            int i = Integer.valueOf(sPrefs.getString("n_speed_max", "0"));

            // did override the savvy speed ?
            if(YaV1.sSavvySpeedOverride > 0)
                i = YaV1.sSavvySpeedOverride;

            mSavvySpeedAsInt      = i;
            mSpeedMaxForSound     = YaV1.getMsFromSpeed(mSavvySpeedAsInt);
            mExcludeKaSavvy   = sPrefs.getBoolean("exclude_ka_savvy", false);
            mExcludeLaserSavvy= sPrefs.getBoolean("exclude_laser_savvy", false);
        }
        else
        {
            mSavvySpeedAsInt   = 0;
            mSpeedMaxForSound  = 0;
            mExcludeKaSavvy    = false;
            mExcludeLaserSavvy = false;
        }

        mApplySort            = Integer.valueOf(sPrefs.getString("alert_sort", "1"));
        mSortAlertView        = sPrefs.getBoolean("sort_alert_view", false);

        String s;

        // processor refresh setting
        // mProcessor.init(YaV1.sPrefs);
        // sound manager init (must be after the processor)
        mSoundManager.init(sPrefs);

        // overlay
        mOverlayEnabled = sPrefs.getBoolean("overlay", false);

        if(mOverlayEnabled)
        {
            mOverlayForInbox  = sPrefs.getBoolean("overlay_inbox", false);
            mOverlayHideSavvy = (sPrefs.getBoolean("overlay_hide_speed", false) && mSpeedMaxForSound > 0);
            mOverlaySticky    = sPrefs.getBoolean("overlay_sticky", false);
        }

        // battery voltage
        sBatteryVoltage       = sPrefs.getBoolean("battery_voltage", false);
        if(!sBatteryVoltage)
            sBatteryLastTime = 0;

        // box muting settings
        mPrefMuteKa = Integer.valueOf(sPrefs.getString("mute_ka", "0"));
        mPrefMuteK = Integer.valueOf(sPrefs.getString("mute_k", "0"));
        mPrefMuteX = Integer.valueOf(sPrefs.getString("mute_x", "0"));
        mPrefMuteKu = Integer.valueOf(sPrefs.getString("mute_ku", "0"));
    }

    // check if the V1 Client is in Running state
    public static final boolean isClientRunning()
    {
        // we get the running state
        if(!YaV1.isClientStarted || YaV1.mV1Client == null)
            return false;
        return YaV1.mV1Client.isRunning();
    }

    // return true if service is active

    public static final boolean isActive()
    {
        return mActive;
    }

    // toggle sound

    public static boolean toggleMute()
    {
        // we can do it only if active, and this will be done by the mute function
        if(mActive)
        {
            boolean ls = mMuteByUser.get();
            // keep track if user muted manually or not
            mMuteByUser.set(!ls);
        }

        return mMuteByUser.get();
    }

    // toggle Display

    public boolean toggleDisplay()
    {
        // we can do it only if active
        if(mActive)
        {
            boolean ls = mDisplayOn.get();
            YaV1.mV1Client.turnMainDisplayOnOff(!ls);
            mDisplayOn.set(!ls);
        }

        return mDisplayOn.get();
    }

    public boolean forceDisplay(boolean onOff)
    {
        if(mActive)
        {
            mDisplayOn.set(onOff);
            YaV1.mV1Client.turnMainDisplayOnOff(onOff);
            return true;
        }

        return false;
    }

    public boolean forceMute(boolean onOff)
    {
        if(mActive)
        {
            YaV1.mV1Client.mute(onOff);
            return true;
        }

        return false;
    }

    public static boolean getMute()
    {
        return mMuteSpeed || mMuteByUser.get();
    }

    public static boolean getMuteSpeed()
    {
        return mMuteSpeed;
    }

    public static boolean isMuteByUser()
    {
        return mMuteByUser.get();
    }

    public boolean getDisplay()
    {
        return mDisplayOn.get();
    }

    // check if we can bind/unbind the callback

    public void turnOffOn()
    {
        Log.d("Valentine", "Turn on/off clientRunning " + isClientRunning() + " Active " + mActive);
        // if not running, and not active, we start
        if(isClientRunning() && !isActive())
        {
            // if not already running, we start the thread and update the status
            setCallBack(true);
            Log.d("Valentine", "Turning call back true");
        }
        else
        {
            // if no connection with ESP remove  callBacks
            if(!isClientRunning())
            {
                Log.d("Valentine", "Turning call back false");
                setCallBack(false);
            }
            else
                Log.d("Valentine", "Turning on/off Nothing to do");
        }
    }

    // Handler for no data

    public void onV1NoDataCallback(String s)
    {
        // we already wait ? (should not happen)
        if(YaV1.mInWait.getAndSet(true))
            return;

        Log.d("Valentine", "No data call back + s");
        final String str = s;
        mHandler.post(new Runnable()
        {
            public void run()
            {
                YaV1.isClientStarted = false;
                // stop the callBack
                mTimedOut = true;
                setCallBack(false);
                mTimedOut = false;

                // suppose not active
                mActive = false;
                if(YaV1.mV1Client == null)
                {
                    Log.d("Valentine", "Error handleBroadcastError, v1Client is null");
                    return;
                }
                // close a dialog that might have been open for manual operation
                if(YaV1.sLockoutOverride != null)
                    YaV1.sLockoutOverride.cleanup(false);
                // we shut down in order the WaitForV1 to be restart
                // to handle correctly the unplug of the BlueTooth of the V1
                YaV1.mV1Client.Shutdown();
                //Log.d("Valentine", "About to post message error " + str);
                YaV1.DbgLog("V1 data timeout " + str);
                //Toast.makeText(YaV1.sContext, "Connect with V1 lost, reconnecting", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(YaV1.sContext, YaV1Activity.class);
                intent.putExtra("restart", true);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                YaV1.sContext.startActivity(intent);
            }
        });
    }

    // Not used

    public void onV1StopCallback()
    {
        /*
        if(YaV1.mInWait.getAndSet(true))
            return;
        */
        // in demo mode we restart the
        if(mDemo)
        {
            Log.d("Valentine", "Stop call back in library mode Active " + mActive);
            // call stopDemo of V1 client and restart the activity
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    setDemoMode(false);
                    Intent intent = new Intent(YaV1.sContext, YaV1Activity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("retry", true);
                    YaV1.sContext.startActivity(intent);
                }
            }, 10);
        }
        else
        {
            Log.d("Valentine", "Stop call NOT In demo back Active " + mActive);
            if(mActive) {
                onV1NoDataCallback("V1 stop");
            }
        }
    }


    // broadcast the error on clients and stop the callbacks

    private void handleBroadcastError(String str_error)
    {
        // demo mode, means demo is finish, we restart the main activity
        if(mDemo)
        {
            setDemoMode(false);
            // restart the main activty
            mHandler.post(new Runnable()
            {
                public void run()
                {
                    Intent intent = new Intent(YaV1.sContext, YaV1Activity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("retry", true);
                    YaV1.sContext.startActivity(intent);
                }
            });
            return;
        }
        else
        {
            Log.d("Valentine", "Not in demo, has stop");
        }
    }

    // callback for Savvy speed

    public void savvySpeedCallback(Integer s)
    {
        YaV1RealSavvy.setSpeed(s.intValue());
        // we issue a Gps Event for Savvy speed
        YaV1.postEvent(mSavvySpeedEvent);
    }

    // check if we should mute according to speed

    private boolean checkMutingSpeed()
    {
        boolean rc = false;

        // we check the Gps for muting or not, also real savvy if we have it
        if(YaV1CurrentPosition.enabled || YaV1RealSavvy.enabled)
        {
            if(mSavvy)
            {
                if(YaV1CurrentPosition.isValid)
                    // set the speed muting
                    rc = YaV1CurrentPosition.speed < mSpeedMaxForSound;
                else if(YaV1RealSavvy.enabled && YaV1RealSavvy.speedMs < mSpeedMaxForSound)
                    rc = true;
            }
        }

        return rc;
    }

    private int NbDisplay = 0;

    //  Handle the display data from the V1

    public void displayCallback(InfDisplayInfoData dispData)
    {
        boolean isLaser     = false;

        // Already active ?

        if(!mActive)
        {
            // This is the first time we have received display data
            if(!dispData.getAuxData().getSysStatus())
                return;
            // get display status
            mDisplayOn.set(dispData.getAuxData().getDisplayOn());
            // get sound status
            mMuteOn = dispData.getAuxData().getSoft();
            mCurrentMute= mMuteOn;
            // now active
            mActive = true;
            // reset the settings
            refreshSettings(false);
            // check if we go dark
            if(sPrefs.getBoolean("auto_dark", false))
                forceDisplay(false);
            // notify for adjusting icons
            Intent sIntent = new Intent("V1Display");
            LocalBroadcastManager.getInstance(YaV1.sContext).sendBroadcast(sIntent);
            // YaV1.postEvent(mUpdateUIEvent);
            v1Notify(false);
        }
        else
        {
            // this can happen when starting the engine
            if(!dispData.getAuxData().getSysStatus())
            {
                Log.d("Valentine", "Packet not sys status in");
                onV1NoDataCallback("V1 Restarted");
                return;
            }

            //if(YaV1.mV1Client.isLibraryInDemoMode())
            //    mHandler.removeCallbacks(mDataTimeoutTask);

            // if we do not start receiving alert, let starts
            if(!mAlertDataOn)
            {
                YaV1.mV1Client.registerForAlertData(this, "alertDataCallback");
                YaV1.mV1Client.sendAlertData(true);
                mAlertDataOn = true;
                // notify and toast ?
                // YaV1.postEvent(new MessageEvent(MessageEvent.Type.MESSAGE_TOAST, "Yav1 is now in active mode scanning"));
                Intent sIntent = new Intent("V1Display");
                sIntent.putExtra("Message", "Yav1 is now in active mode scanning");
                LocalBroadcastManager.getInstance(YaV1.sContext).sendBroadcast(sIntent);
            }
            else
                isLaser = dispData.getBandAndArrowIndicator1().getLaser();

            // set the current View values

            YaV1CurrentView.sBogey0 = dispData.getBogeyCounterData1().getRawData();
            YaV1CurrentView.sBogey1 = dispData.getBogeyCounterData2().getRawData();
            YaV1CurrentView.sArrow0 = dispData.getBandAndArrowIndicator1().getRawData();
            YaV1CurrentView.sArrow1 = dispData.getBandAndArrowIndicator2().getRawData();
            YaV1CurrentView.sSignal = dispData.getSignalStrengthData().getNumberOfLEDs();

            // get current sound status

            mCurrentMute = dispData.getAuxData().getSoft();

            if(!YaV1.mV1Client.isLibraryInDemoMode())
            {
                NbDisplay++;
                // first we request the V1 version
                if(YaV1.sV1Version.isEmpty() && !mRequestVersionSent)
                {
                    if(NbDisplay > 8)
                    /*if(YaV1.mV1Client.m_valentineType == Devices.VALENTINE1_LEGACY ||
                       YaV1.mV1Client.m_valentineType == Devices.VALENTINE1_WITH_CHECKSUM ||
                       YaV1.mV1Client.m_valentineType == Devices.VALENTINE1_WITHOUT_CHECKSUM)*/
                    {
                        Log.d("Valentine", "Valtype " + YaV1.mV1Client.m_valentineType);
                        ((YaV1) getApplication()).getV1Info();
                        mRequestVersionSent = true;
                        YaV1.sLegacy = dispData.getAuxData().getLegacy();
                    }
                }
                else
                {
                    // then other info
                    if(!YaV1.sV1Version.isEmpty())
                    {
                        YaV1.sModeData = dispData.getMode(null);
                        // we check if we need to request the custom sweeps
                        if(mRequestSweep > 0)
                        {
                            ((YaV1) getApplication()).getSweeps();
                            mRequestSweep = 0;
                        }

                        if(!mRequestSettingSent)
                        {
                            ((YaV1) getApplication()).getV1Setting();
                            mRequestSettingSent = true;
                        }

                        // check if we need the battery voltage
                        if ((sBatteryLastTime == 0 || (sBatteryVoltage && (SystemClock.elapsedRealtime() - sBatteryLastTime) / 1000 >= 180)))
                        {
                            // make a call to request the battery Voltage
                            YaV1.mV1Client.getBatteryVoltage(YaV1.sInstance, "v1BatteryCallBack");
                            sBatteryLastTime = SystemClock.elapsedRealtime();
                        }
                    }
                }
            }

            // Pull the laser data out

            if(isLaser)
            {
                haveLaser.set(true);
                laser.setFrequency(0);
                laser.setBand(YaV1Alert.BAND_LASER);
                if (dispData.getBandAndArrowIndicator1().getFront())
                    laser.setArrowDir(YaV1Alert.ALERT_FRONT);
                else
                    laser.setArrowDir(YaV1Alert.ALERT_REAR);
                laser.setSignal(8);
                laser.setDeltaSignal(0);
                // play early
                //if(mPhoneSound && mEarlyLaser && mrAlert[2] != null)
                if(mEarlyLaser)
                    mSoundManager.playEarlyLaser(mMuteByUser.get() || mMuteSpeed);
            }
        }
    }

    // We wait for the mode data to change

    public static boolean renewModeData()
    {
        byte b     = YaV1.getModeByte();
        long lTime = SystemClock.elapsedRealtime();
        byte c;
        int  i = 0;
        YaV1.sModeData = null;

        while(true)
        {
            try
            {
                Thread.sleep(500);
                if(YaV1.sModeData != null && ( (c = YaV1.getModeByte()) == b || c == 0))
                    YaV1.sModeData = null;
                else
                    break;

                // after 5 seconds we stop waiting
                if(SystemClock.elapsedRealtime() - lTime >= 5000)
                    break;

            }catch(InterruptedException e)
            {
                break;
            }
        }

        return YaV1.sModeData != null;
    }

    // receive the alerts
    public void alertDataCallback(YaV1AlertList alert_data)
    {
        // I don't think this is ever the case, but Francky had it here
        if(alert_data == null) {
            return;
        }

        ++processAlertCount;
        mAlertList = alert_data;

        // add the laser back here

        int nb = mAlertList.size();

        if(haveLaser.getAndSet(false))
        {
            laser.setTn(processAlertCount);
            laser.setOrder(16);
            mAlertList.add(laser);
            ++nb;
        }

        // process the alert persistence
        mResend = mProcessor.processAlert(mAlertList, processAlertCount);

        // reverse order to have laser on top and most recent on top
        if(nb > 1)
            Collections.reverse(mAlertList);

        // we have different alerts
        mGotAlert = (nb > 0);

        // last process and send
        processAlert();

        // sound process
        mute();
    }

    // notification

    private void v1Notify(boolean start)
    {
        NotificationCompat.Builder lBuilder = YaV1Activity.getNotificationBuilder();
        lBuilder.setContentText(getText( (mActive ? R.string.alert_run_v1_active : R.string.alert_run_v1_inactive)));
        lBuilder.setSound(Uri.parse(mServiceSound));
        lBuilder.setSmallIcon( (mActive ? R.drawable.ic_notify :R.drawable.ic_notify_off));
        Notification note = lBuilder.build();
        note.flags |= Notification.FLAG_FOREGROUND_SERVICE;

        if(start)
            startForeground(YaV1.APP_ID, note);
        else
        {
            NotificationManager nm = (NotificationManager) getSystemService(YaV1.sContext.NOTIFICATION_SERVICE);
            nm.notify(YaV1.APP_ID, note);
        }
    }

    public void setDemoMode(boolean demo)
    {
        // we install the callBack
        if(!demo)
        {
            YaV1.mV1Client.stopDemoMode(false);
            YaV1.mV1Client.Shutdown();
            //mSoundManager.releaseAll();
        }

        // clear the persistence alerts
        mProcessor.stop();
        setCallBack(demo);
        mDemo = demo;

        // in demo mode, we register for END DEMO
        if(mDemo)
            YaV1.mV1Client.setBroadcastEvent(ValentineClient.V1_DEMO_END, true);
    }

    // we install the callBacks
    private void setCallBack(boolean install)
    {
        if(install)
        {
            // been installed already or not
            if(!mCallInstalled)
            {
                // install the display callback
                YaV1.mV1Client.registerForDisplayData(this, "displayCallback");
                // register for no data received
                YaV1.mV1Client.setBroadcastEvents(ValentineClient.V1_NO_DATA);

                mCallInstalled  = true;
                Log.d("Valentine", "Call back installed");
            }
        }
        else
        {
            // been installed ? Remove them
            if(mCallInstalled)
            {
                // clear all events
                YaV1.mV1Client.clearBroadcastEvents();

                boolean saveActive = mActive;

                NbDisplay = 0;
                
                // reset local variables
                mCallInstalled = false;
                mActive        = false;
                mAlertDataOn   = false;

                // unregister the callbacks

                if(YaV1.mV1Client != null)
                {
                    YaV1.mV1Client.sendAlertData(false);
                    YaV1.mV1Client.deregisterForAlertData(this);
                    YaV1.mV1Client.deregisterForDisplayData(this);
                }

                // Shutdown sound

                mSoundManager.playAll(true);

                // reset the YaV1 value for system info, we do not do if timeout

                if(!YaV1.mV1Client.isLibraryInDemoMode() && !mTimedOut)
                    YaV1.sModeData = null;

                // notify running but not active
                if(saveActive && !mStopping)
                    v1Notify(false);

                Log.d("Valentine", "CallBack uninstalled");
            }
        }
    }

    //
    // we stop
    // this is call when the main activity destroy
    //
    private void stop()
    {
        mStopping = true;

        // stop sound
        mSoundManager.releaseAll();

        // remove all callbacks
        // mHandler.removeCallbacksAndMessages(null);

        // remove the callbacks
        setCallBack(false);

        if(YaV1.mV1Client != null)
        {
            YaV1.mV1Client.clearVersionCallbacks();
            YaV1.mV1Client.Shutdown();
            //PacketQueue.clearQueues();
        }

        YaV1.isClientStarted  = false;
        mActive = false;

        // stop  foreground
        stopForeground(true);

        NotificationManager nm = (NotificationManager) getSystemService(YaV1.sContext.NOTIFICATION_SERVICE);
        nm.cancel(YaV1.APP_ID);

        // reset the alert list for next starting
        mAlertList.clear();
        // clear the persistent alerts
        mProcessor.stop();

        // reset the muting stuff

        mNbLastMuted        = 0;
        mNbMuted            = 0;
        mLastCmdMute        = 0;
        mLastCmdTime[0]     = 0;
        mLastCmdTime[1]     = 0;
        mInSavvy            = false;
        processAlertCount   = 0;
        YaV1.mV1Client      = null;

        mInOverride         = false;
        mRequestOverride    = false;

        stopSelf();
        //mHandler            = null;

        Log.d("Valentine", "Alert service stop");
    }

    // Alert processing
    // Variable used in alert processing;

    private boolean   isMuted;
    private boolean   isExcep;
    private int       mNbAlert;
    private YaV1Alert mCurrentAlert;
    private int       mCurrSignal;
    private int       mCurrBand;
    private int       mAlertProp;

    private boolean   mMute;
    private boolean   mHideOverlay;
    private int       mExceptNumber;

    // private int       mNbInBox;

    private int       mLoop;
    private boolean   mWasMuted          = false;
    private boolean   mResend            = false;

    // static flag for override

    public static boolean mInOverride       = false;
    public static boolean mRequestOverride  = false;

    // process the alerts

    private void processAlert()
    {
        // set / reset some variables

        mNbAlert       = mAlertList.size();
        mMute          = false;
        mHideOverlay   = false;
        mNbMuted       = 0;
        mLoop          = 0;
        mExceptNumber  = 0;

        // reset some values in the CurrentView and SoundManager

        YaV1CurrentView.resetBeforeProcess();
        mSoundManager.resetBeforeProcess();

        // have we got to mute because of speed
        mMute      = false;
        mMuteSpeed = checkMutingSpeed();

        // Mute depends of savvy or wired savvy if no GPS and wired savvy present

        if(YaV1CurrentPosition.isValid)
        {
            if(mOverlayEnabled && mOverlayHideSavvy && YaV1CurrentPosition.speed < mSpeedMaxForSound)
                mHideOverlay = true;
            mMute = mMuteSpeed;
        }
        else
        {
            if(YaV1RealSavvy.enabled)
            {
                // we request Savvy speed - again
                YaV1.mV1Client.getVehicleSpeed(this, "savvySpeedCallback");
                mMute = mMuteSpeed;
            }
        }

        // loop over the alerts

        for(; mLoop < mNbAlert; mLoop++)
        {
            try
            {
                mCurrentAlert  = mAlertList.get(mLoop);

                mCurrBand    = mCurrentAlert.getBand();
                mCurrSignal  = mCurrentAlert.getSignal();
                mAlertProp   = mCurrentAlert.getProperty();

                YaV1CurrentView.setDirectionStrength(mCurrentAlert.getArrowDir(), mCurrSignal);

                YaV1CurrentView.setAlertBand(mCurrBand);

                // if it's locked out
                if(!mCurrentAlert.isLaser())
                {
                    // is not locked out
                    if((mAlertProp & YaV1Alert.PROP_LOCKOUT) < 1)
                    {
                        // we classify the "white" has being inBox
                        //Log.d("Valentine", "inBox: " + YaV1Alert.PROP_INBOX);
                        if((mAlertProp & (YaV1Alert.PROP_INBOX | YaV1Alert.PROP_WHITE)) > 0)
                            YaV1CurrentView.setInboxBand(mCurrBand);

                        YaV1CurrentView.sAlertOverlay++;

                        // ITB testing
                        mPrefMuteK = Integer.valueOf(sPrefs.getString("mute_k", "0"));
                        //Log.d("Valentine", "inBox mute_k: " + mPrefMuteK);
                        //Log.d("Valentine", "YaV1Alert.PROP_INBOX: " + YaV1Alert.PROP_INBOX);
                    }

                    isMuted = (mAlertProp & YaV1Alert.PROP_MUTE) > 0;
                    isExcep = (!isMuted && (mCurrBand == YaV1Alert.BAND_KA && mExcludeKaSavvy));
                }
                else
                {
                    YaV1CurrentView.sAlertOverlay++;
                    isMuted = isExcep = false;
                }

                if(isExcep)
                    mExceptNumber++;

                // if the alert is not muted, we set some values
                if((mMute && !isExcep) || isMuted)
                {
                    mNbMuted++;
                }
                else
                {
                    // send our alerts to SoundManager in case we're using the V1 for audio
                    mSoundManager.setAlertBand(mCurrBand, mCurrSignal);
                    mSoundManager.mInBox += (mAlertProp & (YaV1Alert.PROP_INBOX | YaV1Alert.PROP_WHITE)) > 0 ? 1 : 0;

                    // if we're already in smart dark mode, bump our timestamp if the alert isn't muted
                    if (mSmartDarkMode) {
                        //Log.d("Valentine", "smart dark: mSmartDarkMode, bumping SmartDarkTimestamp");
                        SmartDarkTimestamp = System.currentTimeMillis()/1000;
                    }

                    // if auto dark and smart dark are enabled, turn on the V1 display during an alert and start/bump our smart dark timer
                    if (!mSmartDarkMode && sPrefs.getBoolean("auto_dark", false) && sPrefs.getBoolean("smart_dark", false) && (!mMute || !isMuted)) {
                        //Log.d("Valentine", "smart dark: received an alert, forceDisplay true");
                        forceDisplay(true);
                        // notify for adjusting icons
                        Intent sIntent = new Intent("V1Display");
                        LocalBroadcastManager.getInstance(YaV1.sContext).sendBroadcast(sIntent);
                        mSmartDarkMode = true;
                        SmartDarkTimestamp = System.currentTimeMillis()/1000;
                    }
                }
            }
            catch(IndexOutOfBoundsException exc)
            {
                // log and break
                Log.d("Valentine", "Process Alerts " + processAlertCount + " current " + mLoop + " - Out of bound getting alerts Exc: " + exc.toString());
                YaV1.DbgLog("Valentine process, process alert out of bound current " + mLoop + " Size " + mNbAlert);
                // make sure we do not mute V1 in case of exception
                if(mSoundManager.mMaxSignal < 1)
                    mSoundManager.mMaxSignal = 1;

                mResend = true;
                break;
            }
            catch(NullPointerException exc)
            {
                // log and break
                YaV1.DbgLog("Valentine process, process alert null pointer exception " + mLoop + " Size " + mNbAlert);
                Log.d("Valentine", "Process alerts " + processAlertCount + " current " + mLoop + " - Null pointer Exc: " + exc.toString());
                Log.e("Valentine", "Exception: " + Log.getStackTraceString(exc));
                if(mSoundManager.mMaxSignal < 1)
                    mSoundManager.mMaxSignal = 1;
                mResend = true;
                break;
            }
        }

        // we fire for the view, if client connected
        // avoid to fire empty packets, except for the firstTime empty in order to clear the view

        int nbSend = 0;

        // we have muting speed but exception on Ka, set has not muted

        if(mMute && mExceptNumber > 0)
        {
            mMute      = false;
            mMuteSpeed = false;
        }


        if(mClientCount > 0)
        {
            // we send only if needed and not in tools
            if(mResend && !YaV1ScreenActivity.sInTools)
            {
                // try to synchronize the new Alert
                if(mSortAlertView && mNbAlert > 1)
                    Collections.sort(mAlertList, mSortAlert[mApplySort]);
                YaV1.newAlert(mAlertList);
                mResend = false;
            }
        }
        else
        {
            // for overlay we always send (if needed when not sticky)
            // if overlay enabled and we are not in the app
            if(mOverlayEnabled && YaV1.isInBackground() && (!YaV1.mV1Client.isLibraryInDemoMode() || YaV1.sInTestingMode))
            {
                // if(!mHideOverlay  && mAlertOverlay > 0 && (!mOverlayForInbox || mNbInBox > 0 || hasLaser))
                if(!mHideOverlay && YaV1CurrentView.sAlertOverlay > 0 && (!mOverlayForInbox || YaV1CurrentView.sTotalInbox > 0 || YaV1CurrentView.sHasLaser))
                {
                    // make sure to update the overlay
                    nbSend = mNbAlert;
                }
                else if(mOverlaySticky && YaV1Activity.sOverlay != null && (!YaV1Activity.sOverlay.isVisible() || mWasMuted != mMute))
                {
                    mResend = true;
                }

                // we broadcast for overlay
                // Intent intent = new Intent("V1Overlay");

                //
                // check if something to show in overlay
                // if sticky or something to send, we broadcast
                // Not broadcasting the alert, will make the overlay hide / show
                //

                if(mOverlaySticky || nbSend > 0)
                {
                    if(mNbAlert > 1)
                        Collections.sort(mAlertList, mSortAlert[mApplySort]);

                    YaV1.newAlertOverlay(mAlertList);
                }
                else
                {
                    // we send an empty alert list
                    Log.d("Valentine", "AlertOverlay: sending an empty list");
                    YaV1.newAlertOverlay(mEmptyList);
                }

                mResend = false;
            }
        }

        mWasMuted = mMute;

        if(mNbAlert > 0)
        {
            Log.d("Valentine", "Mute requested from mMute " + mMute + " Max Signal " + mSoundManager.mMaxSignal);
            mMuteRequested = (mMute || mSoundManager.mMaxSignal < 1);
            //Log.d("Valentine", "mMuteRequested = " + mMuteRequested);
        }
        else {
            mMuteRequested = false;
        }

        //
        // we might have to copy the alerts for manual operation
        // check if we are not in the dialog already
        //

        if(mRequestOverride && !mInOverride && mNbAlert > 0 && mProcessor.getOverrideAbleAlert() > 0)
        {
            mInOverride = true;
            if((mInOverride = YaV1.sLockoutOverride.initList(mAlertList)))
            {
                // Broadcast for dialog
                Intent intent = new Intent("YaV1AlertOverride");
                LocalBroadcastManager.getInstance(YaV1.sContext).sendBroadcast(intent);
            }
            // reset the request
            mRequestOverride = false;
        }

        // if we're in SmartDarkMode check our SmartDarkTimestamp for expiration
        if (mSmartDarkMode && sPrefs.getBoolean("auto_dark", false) && (sPrefs.getBoolean("smart_dark", false))) {
            if (SmartDarkTimestamp < (System.currentTimeMillis() / 1000 - 1)) {
                //Log.d("Valentine", "smart dark: SmartDarkTimestamp expired, forceDisplay true");
                forceDisplay(false);
                // notify for adjusting icons
                Intent sIntent = new Intent("V1Display");
                LocalBroadcastManager.getInstance(YaV1.sContext).sendBroadcast(sIntent);
                mSmartDarkMode = false;
            }
        }
    }

    // we get a request to override the data

    public static boolean requestOverride()
    {
        if(YaV1CurrentPosition.sLockout && YaV1.sAutoLockout != null && !mRequestOverride && !mInOverride)
        {
            mRequestOverride = true;
            return true;
        }

        return false;
    }
}
