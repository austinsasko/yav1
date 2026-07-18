package com.glasslsoftware.yav1.car;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.glasslsoftware.yav1.YaV1;
import com.glasslsoftware.yav1.YaV1AlertService;
import com.glasslsoftware.yav1lib.YaV1Alert;
import com.glasslsoftware.yav1lib.YaV1AlertList;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single read surface for the car (Android Auto) UI.
 *
 * The existing alert pipeline (ValentineESP ProcessingThread -> YaV1AlertService ->
 * YaV1AlertProcessor -> Otto bus -> fragments) is untouched; YaV1AlertService pushes
 * into this repository from the same places it feeds the phone UI. All pushes arrive
 * on the ESP ProcessingThread; the repository marshals to the main thread before
 * notifying listeners, and every listener only ever sees an immutable, defensively
 * copied {@link Snapshot} (the Otto AlertEvent is a reused mutable static - the car
 * screen must never read live lists).
 */
public final class V1AlertRepository
{
    private static final String LOG_TAG = "V1CarRepo";

    public enum ConnState { DISCONNECTED, CONNECTING, CONNECTED_IDLE, ALERTING }

    /** Called on the MAIN thread. */
    public interface Listener
    {
        void onChanged(Snapshot s);
    }

    /** Immutable view of the current V1 state, safe to read from any thread. */
    public static final class Snapshot
    {
        public final ConnState     state;
        public final YaV1AlertList alerts;      // defensive copy, never mutated
        public final boolean       muted;       // V1 soft-mute ack (InfDisplayInfoData aux)
        public final int           bogeyCount;
        public final String        v1Version;

        Snapshot(ConnState state, YaV1AlertList alerts, boolean muted, int bogeyCount, String v1Version)
        {
            this.state      = state;
            this.alerts     = alerts;
            this.muted      = muted;
            this.bogeyCount = bogeyCount;
            this.v1Version  = v1Version;
        }

        /** The priority alert (PROP_PRIORITY), or the first alert, or null. */
        public YaV1Alert getPriorityAlert()
        {
            for(int i = 0; i < alerts.size(); i++)
            {
                if((alerts.get(i).getProperty() & YaV1Alert.PROP_PRIORITY) > 0)
                    return alerts.get(i);
            }
            return alerts.isEmpty() ? null : alerts.get(0);
        }

        @Override
        public String toString()
        {
            return "Snapshot{state=" + state + ", alerts=" + alerts.size()
                    + ", muted=" + muted + ", bogey=" + bogeyCount + ", v1=" + v1Version + "}";
        }
    }

    private static volatile V1AlertRepository sInstance = null;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> mListeners = new CopyOnWriteArrayList<Listener>();

    // written from the ESP ProcessingThread / service callbacks, read under lock
    private final Object        mLock         = new Object();
    private YaV1AlertList       mAlerts       = new YaV1AlertList();
    private boolean             mMuted        = false;
    private int                 mBogeyCount   = 0;
    private boolean             mServiceActive= false;

    // last published snapshot (main thread)
    private volatile Snapshot   mCurrent;

    // coalesce multiple background pushes into one main-thread notification
    private volatile boolean    mNotifyPending = false;

    private final Runnable mNotifyTask = new Runnable()
    {
        @Override
        public void run()
        {
            mNotifyPending = false;
            Snapshot s = buildSnapshot();
            mCurrent = s;
            Log.d(LOG_TAG, "update on " + Thread.currentThread().getName() + ": " + s);
            for(Listener l : mListeners)
                l.onChanged(s);
        }
    };

    private V1AlertRepository()
    {
        mCurrent = buildSnapshot();

        // "V1Display" is broadcast by YaV1AlertService on connection/mute/display
        // state transitions - use it as a state refresh signal.
        if(YaV1.sContext != null)
        {
            LocalBroadcastManager.getInstance(YaV1.sContext).registerReceiver(new BroadcastReceiver()
            {
                @Override
                public void onReceive(Context context, Intent intent)
                {
                    publish();
                }
            }, new IntentFilter("V1Display"));
        }
    }

    public static V1AlertRepository get()
    {
        if(sInstance == null)
        {
            synchronized(V1AlertRepository.class)
            {
                if(sInstance == null)
                    sInstance = new V1AlertRepository();
            }
        }
        return sInstance;
    }

    /** Latest published snapshot; safe from any thread. */
    public Snapshot current()
    {
        return mCurrent;
    }

    /** Registers a listener; it receives the current snapshot immediately (main thread). */
    public void addListener(final Listener l)
    {
        mListeners.add(l);
        mMainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if(mListeners.contains(l))
                {
                    Snapshot s = buildSnapshot();
                    mCurrent = s;
                    Log.d(LOG_TAG, "initial snapshot on " + Thread.currentThread().getName() + ": " + s);
                    l.onChanged(s);
                }
            }
        });
    }

    public void removeListener(Listener l)
    {
        mListeners.remove(l);
    }

    /**
     * User mute request from the car screen. Routes through the existing service
     * logic (mMuteByUser + throttled YaV1.mV1Client.mute(...)), exactly like the
     * phone UI sound toggle - never a raw client call.
     */
    public void setUserMute(boolean on)
    {
        if(YaV1AlertService.isMuteByUser() != on)
        {
            boolean now = YaV1AlertService.toggleMute();
            Log.d(LOG_TAG, "setUserMute(" + on + ") -> muteByUser=" + now);
            publish();
        }
    }

    public boolean isUserMuted()
    {
        return YaV1AlertService.isMuteByUser();
    }

    // ---------------------------------------------------------------------
    // Push API - called by YaV1AlertService (ESP ProcessingThread / main)
    // ---------------------------------------------------------------------

    /** New processed alert list for this cycle; list is copied before returning. */
    public void onAlerts(YaV1AlertList alerts)
    {
        synchronized(mLock)
        {
            YaV1AlertList copy = copyList(alerts);
            if(listEquals(mAlerts, copy))
                return;
            mAlerts = copy;
        }
        publish();
    }

    /** V1 display (inf) packet state: soft-mute ack + decoded bogey counter. */
    public void onDisplayData(boolean softMute, int bogeyCount)
    {
        synchronized(mLock)
        {
            if(mMuted == softMute && mBogeyCount == bogeyCount)
                return;
            mMuted      = softMute;
            mBogeyCount = bogeyCount;
        }
        publish();
    }

    /** Service alert-processing became active/inactive (callbacks installed or removed). */
    public void onServiceActive(boolean active)
    {
        synchronized(mLock)
        {
            mServiceActive = active;
            if(!active)
            {
                mAlerts     = new YaV1AlertList();
                mBogeyCount = 0;
            }
        }
        publish();
    }

    // ---------------------------------------------------------------------

    private void publish()
    {
        if(!mNotifyPending)
        {
            mNotifyPending = true;
            mMainHandler.post(mNotifyTask);
        }
    }

    private Snapshot buildSnapshot()
    {
        YaV1AlertList alerts;
        boolean muted;
        int bogey;
        boolean active;

        synchronized(mLock)
        {
            alerts = mAlerts;   // never mutated after swap-in; safe to share
            muted  = mMuted;
            bogey  = mBogeyCount;
            active = mServiceActive;
        }

        ConnState state = deriveState(active, !alerts.isEmpty());

        if(state != ConnState.ALERTING && state != ConnState.CONNECTED_IDLE)
        {
            alerts = new YaV1AlertList();
            bogey  = 0;
        }

        if(bogey <= 0)
            bogey = alerts.size();

        return new Snapshot(state, alerts, muted, bogey, YaV1.sV1Version);
    }

    private static ConnState deriveState(boolean serviceActive, boolean hasAlerts)
    {
        // the service actively processing V1 display data is the strongest
        // signal - it also covers demo mode, where isClientRunning() is false
        // because the demo replay never marks the client as started
        if(serviceActive || YaV1AlertService.isActive())
            return hasAlerts ? ConnState.ALERTING : ConnState.CONNECTED_IDLE;

        if(YaV1AlertService.isClientRunning())
            return ConnState.CONNECTING;

        return ConnState.DISCONNECTED;
    }

    private static boolean listEquals(YaV1AlertList a, YaV1AlertList b)
    {
        if(a.size() != b.size())
            return false;

        for(int i = 0; i < a.size(); i++)
        {
            YaV1Alert x = a.get(i);
            YaV1Alert y = b.get(i);

            if(x.getFrequency() != y.getFrequency() || x.getArrowDir() != y.getArrowDir()
                    || x.getSignal() != y.getSignal() || x.getProperty() != y.getProperty()
                    || x.getBand() != y.getBand() || x.getDeltaSignal() != y.getDeltaSignal())
                return false;
        }

        return true;
    }

    private static YaV1AlertList copyList(YaV1AlertList src)
    {
        YaV1AlertList dst = new YaV1AlertList();

        if(src != null)
        {
            for(int i = 0; i < src.size(); i++)
            {
                YaV1Alert a = src.get(i);
                dst.add(new YaV1Alert(a.getFrequency(), a.getArrowDir(), a.getSignal(),
                                      a.getProperty(), a.getBand(), a.getColor(), a.getTn(),
                                      a.getOrder(), a.getDeltaSignal(), a.getTimestamp(),
                                      a.getPersistentId()));
            }
        }

        return dst;
    }
}
