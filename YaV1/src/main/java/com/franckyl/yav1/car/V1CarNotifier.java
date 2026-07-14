package com.franckyl.yav1.car;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.car.app.connection.CarConnection;
import androidx.car.app.notification.CarAppExtender;
import androidx.car.app.notification.CarPendingIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.Observer;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1lib.YaV1Alert;

import java.util.Locale;

/**
 * Background alerting while Google Maps / Waze own the car screen.
 *
 * On each NEW priority alert (never per packet - identity is tracked across
 * cycles) this posts a phone notification extended with CarAppExtender at
 * IMPORTANCE_HIGH; Android Auto renders that as a transient heads-up card
 * over the foreground app without stopping or muting its guidance. The card
 * auto-cancels when the alert expires and tapping it foregrounds YaV1's car
 * screen. Only active while an Android Auto (projection) session is
 * connected, and gated behind the "aa_hun_alerts" preference.
 */
public final class V1CarNotifier implements V1AlertRepository.Listener
{
    private static final String LOG_TAG         = "V1CarNotify";
    private static final String CHANNEL_ID      = "yav1_car_alert";
    private static final int    NOTIFICATION_ID = 2908;
    public  static final String PREF_KEY        = "aa_hun_alerts";

    private static V1CarNotifier sInstance = null;

    private final Context mContext;

    private volatile boolean mCarConnected = false;
    private long             mLastAlertKey = 0;
    private boolean          mPosted       = false;

    /** Call on the main thread (CarConnection requires it). Safe to call repeatedly. */
    public static void init(Context context)
    {
        if(sInstance == null)
            sInstance = new V1CarNotifier(context.getApplicationContext());
    }

    private V1CarNotifier(Context context)
    {
        mContext = context;

        createChannel();

        // only post while projected to a head unit; on the phone alone the
        // existing sounds/overlay cover alerting and nothing changes
        try
        {
            new CarConnection(mContext).getType().observeForever(new Observer<Integer>()
            {
                @Override
                public void onChanged(Integer type)
                {
                    mCarConnected = (type != null && type == CarConnection.CONNECTION_TYPE_PROJECTION);
                    Log.d(LOG_TAG, "car connection type " + type + " -> connected=" + mCarConnected);
                    if(!mCarConnected)
                        cancel();
                }
            });
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "CarConnection unavailable: " + e);
        }

        V1AlertRepository.get().addListener(this);
    }

    private void createChannel()
    {
        if(Build.VERSION.SDK_INT >= 26)
        {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "V1 alerts (Android Auto)", NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(false);
            channel.setSound(null, null);   // alert audio comes from the existing sound pipeline
            NotificationManager nm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm != null)
                nm.createNotificationChannel(channel);
        }
    }

    // main thread (repository contract)
    @Override
    public void onChanged(V1AlertRepository.Snapshot s)
    {
        if(s.state != V1AlertRepository.ConnState.ALERTING || s.alerts.isEmpty())
        {
            // alert expired - auto-cancel the card
            mLastAlertKey = 0;
            cancel();
            return;
        }

        if(!mCarConnected || !YaV1.sPrefs.getBoolean(PREF_KEY, true))
            return;

        YaV1Alert prio = s.getPriorityAlert();
        if(prio == null)
            return;

        long key = alertKey(prio);
        if(key == mLastAlertKey)
            return;     // same alert, just a signal/packet update - never re-post

        mLastAlertKey = key;
        post(prio, s);
    }

    private static long alertKey(YaV1Alert a)
    {
        if(a.getPersistentId() != 0)
            return ((long) a.getBand() << 32) ^ a.getPersistentId();
        // no persistent id: identity = band + 50 MHz frequency bucket
        return ((long) a.getBand() << 32) | (a.getFrequency() / 50);
    }

    private void post(YaV1Alert prio, V1AlertRepository.Snapshot s)
    {
        String title = YaV1Alert.getBandStr(V1SurfaceRenderer.clampBand(prio.getBand())) + " "
                + V1SurfaceRenderer.arrowChar(prio.getArrowDir());
        String freq  = V1SurfaceRenderer.formatFrequency(prio);
        String text  = (freq.isEmpty() ? "" : freq + "  ") + prio.getSignal() + "/8"
                + (s.bogeyCount > 1 ? "  (" + s.bogeyCount + " bogeys)" : "");

        PendingIntent contentIntent = null;
        try
        {
            // tap -> bring YaV1's car screen to the foreground on the head unit
            Intent carIntent = new Intent(Intent.ACTION_VIEW)
                    .setComponent(new ComponentName(mContext, YaV1CarAppService.class));
            contentIntent = CarPendingIntent.getCarApp(mContext, 0, carIntent, 0);
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "CarPendingIntent failed: " + e);
        }

        CarAppExtender.Builder extender = new CarAppExtender.Builder()
                .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notify);
        if(contentIntent != null)
            extender.setContentIntent(contentIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setOnlyAlertOnce(false)
                .setAutoCancel(true)
                .extend(extender.build());

        try
        {
            NotificationManagerCompat nm = NotificationManagerCompat.from(mContext);
            if(nm.areNotificationsEnabled())
            {
                nm.notify(NOTIFICATION_ID, builder.build());
                mPosted = true;
                Log.d(LOG_TAG, "posted HUN: " + title + " " + text);
            }
        }
        catch(SecurityException e)
        {
            Log.d(LOG_TAG, "notify rejected: " + e);
        }
    }

    private void cancel()
    {
        if(mPosted)
        {
            mPosted = false;
            NotificationManagerCompat.from(mContext).cancel(NOTIFICATION_ID);
        }
    }
}
