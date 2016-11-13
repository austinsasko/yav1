package com.franckyl.yav1;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.franckyl.yav1.events.GpsEvent;
import com.franckyl.yav1.lockout.LockoutParam;
import com.squareup.otto.Produce;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by franck on 7/3/13.
 */

public class YaV1GpsService extends Service
{
    // location list
    private ArrayList<Location> mPoints = new ArrayList<Location>();
    private ArrayList<Long>     mTime   = new ArrayList<Long>();

    private long mLastLocationTime      = 0;
    private float[] mResults            = {0,0,0};

    // Location manager objects

    private static LocationManager mLm   = null;

    // active state
    private static AtomicBoolean mActive = new AtomicBoolean(false);

    //private GpsStatus        mGpsStatus;
    public static boolean mCallBackInstalled = false;

    // binder for service

    private final IBinder mBinder         = new gpsBinder();

    private int           mClientCount    = 0;

    // notification builder
    // private NotificationCompat.Builder mBuilder = null;

    // time from last point when we consider valid
    public static long     GPS_UPDATE_INTERVAL = 5000;
    private static String  mUpdateIntentFilter = "YAV1_GPS_NEW_LOCATION";

    // keep last known value
    private double mLastKnownSpeed        = 0.0;
    private float  mLastKnownBearing      = 400;


    private Location              mShortListLocation = null;
    private Location              mLastLocation      = null;
    private static boolean        mFirstListDone     = false;
    private createShort           mBuildShort        = null;

    // handler for timeout

    private Handler mHandler;

    // private static Handler mHandler = null;

    private PendingIntent    mLocationIntent   = null;
    private LocationReceiver mLocationReceiver = null;

    private GpsEvent         mUpdateEvent      = new GpsEvent(GpsEvent.Type.UPDATE);

    // static member to refresh the unit

    public static void refreshSettings()
    {
        GPS_UPDATE_INTERVAL = (long) Integer.valueOf(YaV1.sPrefs.getString("gpstimeout", "5000"));
    }

    // reset the first list for lockout (when Db restored)

    public static void resetFirstListDone()
    {
        mFirstListDone = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        // suppose no valid position
        YaV1CurrentPosition.isValid = false;
        YaV1CurrentPosition.count   = 0;

        // startStopExternalGps(true);
        mHandler = YaV1.sLooper.getHandler();

        // starting location manager
        if(mLm == null)
        {
            mLm = (LocationManager) YaV1.sContext.getSystemService(Context.LOCATION_SERVICE);
            installListener(true);
            // needed for producer
            YaV1.getEventBus().register(this);
        }
        else
        {
            // Could happen if Gps has been turned on after
            if(!mCallBackInstalled)
                installListener(true);
        }

        // notify and startForeground

        notifyGps();
        Log.d("Valentine GPS", "Gps started");
        return (START_NOT_STICKY);
    }

    @Override
    public void onDestroy()
    {
        YaV1.getEventBus().unregister(this);
        stop();
        super.onDestroy();
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class gpsBinder extends Binder
    {
        public void restartGps()
        {
            resetGps();
        }
    };

    // return this service for the client

    public IBinder onBind(Intent intent)
    {
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
        mClientCount++;
    }

    //
    // we stop
    //

    private void stop()
    {
        Log.d("Valentine GPS", "Stopping Gps Service");
        // remove the timeout call back
        mHandler.removeCallbacks(mGpsTimeoutTask);

        // remove the GPS update if callBackInstalled
        installListener(false);
        mActive.set(false);

        mLm      = null;
        // mBuilder = null;

        if(mBuildShort != null && mBuildShort.isAlive())
            mBuildShort.interrupt();

        mBuildShort = null;

        // ****
        mFirstListDone     = false;
        mLocationReceiver  = null;
        mLocationIntent    = null;

        // mHandler = null;
        stopForeground(true);
        // mShortList = null;
        stopSelf();
        Log.d("Valentine", "Gps service stop");
    }

    // public function to reset GPS

    public void resetGps()
    {
        YaV1CurrentPosition.reset(true);
        YaV1CurrentPosition.count++;
        YaV1CurrentPosition.isValid = false;

        // we clear some values

        mPoints.clear();
        mTime.clear();
        mLastKnownBearing = 400;

        installListener(false);
        // recreate the Location Manager
        mLm = (LocationManager) YaV1.sContext.getSystemService(Context.LOCATION_SERVICE);

        // install Listener
        installListener(true);
    }

    // install or remove the listener

    private void installListener(boolean install)
    {
        if(install)
        {
            String s = LocationManager.GPS_PROVIDER;

            if(mLm.isProviderEnabled(s))
            {
                mLocationReceiver = new LocationReceiver();

                // register the receiver

                registerReceiver(mLocationReceiver, new IntentFilter(mUpdateIntentFilter));
                Intent intent = new Intent(mUpdateIntentFilter);
                mLocationIntent = PendingIntent.getBroadcast(this, YaV1.APP_ID, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                mLm.requestLocationUpdates(s, 0, 0, mLocationIntent);

                mCallBackInstalled = true;
                mLastLocationTime = SystemClock.elapsedRealtime();

                Log.d("Valentine", "Gps Listener installed");
                // we post a timeout
                mHandler.postDelayed(mGpsTimeoutTask, GPS_UPDATE_INTERVAL);
            }
            else
                Log.d("Valentine TEST", "Provider unavailable");
        }
        else
        {
            // remove the timeout call back
            mHandler.removeCallbacks(mGpsTimeoutTask);

            if(mLm != null)
            {
                if(mLocationIntent != null)
                    mLm.removeUpdates(mLocationIntent);
            }

            if(mLocationReceiver != null)
                unregisterReceiver(mLocationReceiver);

            // unregister
            if(mCallBackInstalled)
            {
                // from intent
                // mLm.removeUpdates(mLocationIntent);
                // unregisterReceiver(mLocationReceiver);
                mCallBackInstalled = false;
                Log.d("Valentine", "Gps Listener removed");
            }
        }
    }

    // our broadcast receiver for new Location

    private class LocationReceiver extends BroadcastReceiver
    {
        public void onReceive(Context context, Intent intent)
        {
            String s = intent.getAction();
            Location location = (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
            if(location != null)
                updateLocation(location);
        }
    }

    // This task is used when GPS does not receive for 5 secondes

    private final static Runnable mGpsTimeoutTask = new Runnable()
    {
        public void run()
        {
            Log.d("Valentine GPS", "Gps timeout " + GPS_UPDATE_INTERVAL);
            YaV1CurrentPosition.isValid = false;
            YaV1CurrentPosition.count++;
            // reset in order to search for closest list
            if(YaV1CurrentPosition.sLockout && YaV1.sAutoLockout != null)
                YaV1.sAutoLockout.mGpsLost.set(true);

            YaV1.postEvent(new GpsEvent(GpsEvent.Type.TIMEOUT));
        }
    };

    // update the location

    private void updateLocation(Location loc)
    {
        mHandler.removeCallbacks(mGpsTimeoutTask);

        // set active now, this can happen when no GPS and having a bluethooh one, we do not receive GpsStatus
        if(!mActive.get())
            mActive.set(true);

        mLastLocationTime = SystemClock.elapsedRealtime();

        Double speed    = 0.0;
        Double cspeed   = 0.0;
        float  bearing  = 0;
        float  distance = 0;
        long   t        = 0;
        int    ps       = 0;

        // check the accuracy (check if we have minimum required) ?

        int acc = (int) Math.ceil(loc.getAccuracy());

        // put the Location on the head of the list
        mPoints.add(0, loc);
        mTime.add(0, Long.valueOf(mLastLocationTime));

        ps = mPoints.size();

        // we compute the speed
        if(ps > 1)
        {
            // we can use the distance and time from previous Location
            Location.distanceBetween(mPoints.get(1).getLatitude(), mPoints.get(1).getLongitude(),
                    loc.getLatitude(), loc.getLongitude(), mResults);
            distance = mResults[0];
            bearing  = mResults[2];

            // be sure to have bearing between 0 - 360
            if(bearing < 0)
                bearing += 360;

            t = mLastLocationTime - mTime.get(1);
            //t = loc.getTime() - mPoints.get(1).getTime();
            cspeed = (double) (distance / (t / 1000));
            if(Double.isInfinite(cspeed))
            {
                if(loc.hasSpeed())
                    cspeed = loc.getSpeed() * 1.0;
                else
                    cspeed = mLastKnownSpeed;
            }

            // if current bearing is > 360, means first time
            if(mLastKnownBearing > 360)
                mLastKnownBearing = bearing;
            else
            {
                //if( (mTieBearing && distance < acc) || (!mTieBearing && distance < 5.0))
                if(distance < 5.0)
                    bearing = mLastKnownBearing;
                else
                    mLastKnownBearing = bearing;
            }
        }
        else
        {
            // we have to wait at least 2 gps points, because we can't have bearing
            YaV1CurrentPosition.isValid = false;
        }

        if(loc.hasSpeed())
            speed = loc.getSpeed() * 1.0;
        else
            speed = cspeed;

        mLastKnownSpeed = speed;

        // we fill up the current position and validate only if we have 2 data points
        if(ps > 1)
        {
            YaV1CurrentPosition.speed  = speed;

            // cSpeed will be computed in units

            YaV1CurrentPosition.lat = loc.getLatitude();
            YaV1CurrentPosition.lon = loc.getLongitude();
            YaV1CurrentPosition.accuracy = (int) Math.ceil(loc.getAccuracy());
            YaV1CurrentPosition.count++;
            YaV1CurrentPosition.bearing   = (int) bearing;
            YaV1CurrentPosition.isValid   = true;
            YaV1CurrentPosition.timeStamp = mLastLocationTime;

            YaV1CurrentPosition.cSpeed    = YaV1.getSpeedFromMs(speed);

            // post the even change

            YaV1.postEvent(mUpdateEvent);

            // if we enable lockout
            if(YaV1CurrentPosition.sLockout && YaV1.sAutoLockout != null)
            {
                if(!mFirstListDone)
                {
                    Log.d("Valentine", "Creating first short list");
                    mFirstListDone = true;

                    // refresh the list
                    if(mBuildShort == null || !mBuildShort.isAlive())
                    {
                        mBuildShort = new createShort();
                        mBuildShort.start();
                    }
                    mShortListLocation  = loc;
                }
                else
                {
                    // we check the revision
                    if(YaV1.sAutoLockout.getRevision() > 0 && (distance = loc.distanceTo(mShortListLocation)) >= LockoutParam.mUpdateDistance)
                    {
                        // we run again the shortlist
                        if(mBuildShort == null || !mBuildShort.isAlive())
                        {
                            mBuildShort         = new createShort();
                            mBuildShort.start();
                            mShortListLocation  = loc;
                        }
                    }
                }
            }
        }
        //else
        //    Log.d("Valentine", "Not enough point" + mPoints.size());

        // we keep maximum of 5 points
        if(mPoints.size() > 5)
        {
            mPoints.subList(5, mPoints.size()).clear();
            mTime.subList(5, mTime.size()).clear();
        }

        mLastLocation = loc;

        mHandler.postDelayed(mGpsTimeoutTask, GPS_UPDATE_INTERVAL);

    }

    // we are provider for the BUS

    @Produce
    public GpsEvent produceGpsEvent()
    {
        return new GpsEvent(GpsEvent.Type.UPDATE);
    }

    // notify

    private void notifyGps()
    {
        NotificationCompat.Builder lBuilder = YaV1Activity.getNotificationBuilder();
        Notification note = lBuilder.build();
        note.flags |= Notification.FLAG_FOREGROUND_SERVICE;
        startForeground(YaV1.APP_ID, note);
    }

    // a Thread to create the shortlist of lockout

    private static class createShort extends Thread
    {
        public void run()
        {
            Log.d("Valentine", "short list starting");
            long l = SystemClock.elapsedRealtime();
            YaV1.sAutoLockout.makeShortList();
            YaV1.DbgLog("Short list created " + (SystemClock.elapsedRealtime() - l) + " ms");
        }
    }
}
