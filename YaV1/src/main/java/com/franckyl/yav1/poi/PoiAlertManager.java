package com.franckyl.yav1.poi;

import android.content.Context;
import android.util.Log;

import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.events.GpsEvent;
import com.squareup.otto.Subscribe;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [P2-POI] Runtime glue for camera / POI alerts.
 *
 * Subscribes to {@link GpsEvent} on the app event bus; on every position
 * update (when the feature preference is on) it runs the {@link PoiAlertEngine}
 * against the spatial index built from the enabled files of the
 * {@link PoiStore}, and routes resulting alerts to TTS / tone / Toast via
 * {@link Announcer}.
 *
 * Loading and index building happen on a background thread; the GPS-event
 * path only does an in-memory grid query.
 */
public class PoiAlertManager
{
    public static final String LOG_TAG = "Valentine POI";

    // defaults / constants
    private static final double CONE_DEG      = 60.0;
    private static final double MIN_SPEED_MS  = 3.0;      // ~11 km/h: "driving"
    private static final long   RESET_MS      = 3 * 60 * 1000L;
    private static final int    DEF_RADIUS_M  = 500;

    private static PoiAlertManager sInstance = null;

    private final Context        mContext;
    private final PoiStore       mStore;
    private final PoiAlertEngine mEngine;

    private volatile PoiGridIndex mIndex   = null;
    private final AtomicBoolean   mLoading = new AtomicBoolean(false);
    private volatile boolean      mLoaded  = false;

    private PoiAlertManager(Context context)
    {
        mContext = context;
        mStore   = new PoiStore(new File(YaV1.getStorageRootDir(), "poi"));
        mEngine  = new PoiAlertEngine(DEF_RADIUS_M, CONE_DEG, MIN_SPEED_MS, RESET_MS);
    }

    /** Create the singleton and register it on the event bus. */
    public static synchronized void init(Context context)
    {
        if(sInstance != null)
            return;

        sInstance = new PoiAlertManager(context);
        YaV1.getEventBus().register(sInstance);
        Log.d(LOG_TAG, "PoiAlertManager registered");
    }

    public static PoiAlertManager getInstance()
    {
        return sInstance;
    }

    /** The store (for the preference UI). May trigger a lazy load. */
    public PoiStore getStore()
    {
        ensureLoaded();
        return mStore;
    }

    public boolean isLoaded()
    {
        return mLoaded;
    }

    /** Rebuild the index after imports / enable changes (async). */
    public void reload()
    {
        if(!mLoading.compareAndSet(false, true))
            return;

        new Thread("YaV1PoiLoad")
        {
            @Override
            public void run()
            {
                try
                {
                    mStore.load();
                    PoiGridIndex idx = PoiGridIndex.build(mStore.enabledPois());
                    mIndex  = idx;
                    mLoaded = true;
                    mEngine.reset();
                    Log.d(LOG_TAG, "index built: " + idx.size() + " POIs from "
                                    + mStore.getFiles().size() + " file(s)");
                }
                catch(Exception e)
                {
                    Log.d(LOG_TAG, "load failed: " + e);
                }
                finally
                {
                    mLoading.set(false);
                }
            }
        }.start();
    }

    /**
     * Rebuild the index synchronously from already-loaded store content
     * (used by the preference dialog right after an import).
     */
    public void rebuildIndexNow()
    {
        PoiGridIndex idx = PoiGridIndex.build(mStore.enabledPois());
        mIndex  = idx;
        mLoaded = true;
        mEngine.reset();
        Log.d(LOG_TAG, "index rebuilt: " + idx.size() + " POIs");
    }

    private void ensureLoaded()
    {
        if(!mLoaded)
            reload();
    }

    // ------------------------------------------------------------ bus event

    @Subscribe
    public void onGpsEvent(GpsEvent event)
    {
        if(event.getType() != GpsEvent.Type.UPDATE)
            return;

        if(YaV1.sPrefs == null || !YaV1.sPrefs.getBoolean("poi_enable", false))
            return;

        if(!YaV1CurrentPosition.isValid)
            return;

        if(!mLoaded)
        {
            ensureLoaded();
            return;
        }

        PoiGridIndex index = mIndex;
        if(index == null || index.size() == 0)
            return;

        // pick up current preference values (cheap in-memory reads)
        int radius = DEF_RADIUS_M;
        try
        {
            radius = Integer.parseInt(YaV1.sPrefs.getString("poi_radius", String.valueOf(DEF_RADIUS_M)));
        }
        catch(NumberFormatException ignored)
        {
        }
        mEngine.setRadius(radius);

        List<PoiAlertEngine.Alert> alerts = mEngine.update(
                YaV1CurrentPosition.lat, YaV1CurrentPosition.lon,
                YaV1CurrentPosition.bearing, YaV1CurrentPosition.speed,
                System.currentTimeMillis(), index);

        if(alerts.isEmpty())
            return;

        int unit  = (YaV1.sCurrUnit == 1 ? PoiPhrases.UNIT_IMPERIAL : PoiPhrases.UNIT_METRIC);
        int style = styleFromPref();

        for(PoiAlertEngine.Alert a: alerts)
        {
            String phrase = PoiPhrases.alertPhrase(a.poi, a.distanceM, a.stage, unit);

            Log.d(LOG_TAG, "ALERT stage " + a.stage + ": " + phrase
                            + " (" + (int) a.distanceM + " m, type '" + a.poi.type + "')");

            Announcer.announce(mContext, phrase, phrase, style);
        }
    }

    private int styleFromPref()
    {
        String s = YaV1.sPrefs.getString("poi_alert_style", "0");
        if("1".equals(s))
            return Announcer.STYLE_TTS_ONLY;
        if("2".equals(s))
            return Announcer.STYLE_SOUND_ONLY;
        return Announcer.STYLE_TTS_AND_SOUND;
    }
}
