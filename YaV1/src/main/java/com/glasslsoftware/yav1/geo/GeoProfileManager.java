package com.glasslsoftware.yav1.geo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.glasslsoftware.yav1.R;
import com.glasslsoftware.yav1.YaV1;
import com.glasslsoftware.yav1.YaV1CurrentPosition;
import com.glasslsoftware.yav1.YaV1Setting;
import com.glasslsoftware.yav1.events.GpsEvent;
import com.glasslsoftware.yav1lib.YaV1AlertList;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.io.InputStream;

/**
 * [P3-GEO] Wires location based automatic V1 profile switching together:
 *
 *  - subscribes to {@link GpsEvent} updates on the Otto bus,
 *  - resolves the position to a US state with {@link StateResolver},
 *  - lets {@link GeoProfileEngine} decide whether to push the profile the
 *    user mapped to that state, through the existing YaV1SettingSet push
 *    machinery.
 *
 * The feature is OFF by default ({@link #PREF_ENABLED}). Decisions are
 * logged under the tag "Valentine GEO".
 */
public class GeoProfileManager
{
    public static final String LOG_TAG      = "Valentine GEO";
    public static final String PREF_ENABLED = "geo_auto_profile";
    public static final String PREF_TTS     = "geo_tts";
    public static final String PREF_RULES   = "geo_profile_rules";

    private static GeoProfileManager sInstance = null;

    // the shared dataset/resolver (also used by the config activity)
    private static StateResolver sResolver       = null;
    private static boolean       sResolverFailed = false;
    private static boolean       sLoading        = false;

    private final Context          mContext;
    private final GeoProfileEngine mEngine;
    private final GeoAnnouncer     mAnnouncer;

    private boolean mWasEnabled       = false;
    private boolean mLoggedNotReady   = false;

    // strong reference required: SharedPreferences keeps listeners weakly
    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener;

    private GeoProfileManager(Context context)
    {
        mContext   = context.getApplicationContext();
        mAnnouncer = new GeoAnnouncer(mContext);
        mEngine    = new GeoProfileEngine(new RealGateway(), new GeoProfileEngine.Clock()
        {
            @Override
            public long now()
            {
                return System.currentTimeMillis();
            }
        });

        mEngine.setRules(GeoRuleStore.fromJson(YaV1.sPrefs.getString(PREF_RULES, "")));

        mPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener()
        {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
            {
                if(PREF_RULES.equals(key))
                {
                    Log.d(LOG_TAG, "Profile rules updated");
                    mEngine.setRules(GeoRuleStore.fromJson(prefs.getString(PREF_RULES, "")));
                }
            }
        };

        YaV1.sPrefs.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    /** Called once from YaV1.onCreate(). */
    public static synchronized void init(Context context)
    {
        if(sInstance == null && context != null && YaV1.sPrefs != null)
        {
            sInstance = new GeoProfileManager(context);
            YaV1.getEventBus().register(sInstance);
            Log.d(LOG_TAG, "Geo profile manager initialized, enabled="
                    + YaV1.sPrefs.getBoolean(PREF_ENABLED, false));
        }
    }

    public static GeoProfileManager getInstance()
    {
        return sInstance;
    }

    public GeoProfileEngine getEngine()
    {
        return mEngine;
    }

    // ------------------------------------------------------------ gps event

    @Subscribe
    public void onGpsEvent(GpsEvent evt)
    {
        if(evt.getType() != GpsEvent.Type.UPDATE)
            return;

        boolean enabled = YaV1.sPrefs.getBoolean(PREF_ENABLED, false);

        if(!enabled)
        {
            if(mWasEnabled)
            {
                mWasEnabled = false;
                mEngine.reset();
                mAnnouncer.release();

                synchronized(GeoProfileManager.class)
                {
                    if(sResolver != null)
                        sResolver.resetCache();
                }

                Log.d(LOG_TAG, "Feature disabled, engine reset");
            }
            return;
        }

        mWasEnabled = true;

        if(!YaV1CurrentPosition.isValid)
            return;

        StateResolver resolver = peekResolver();

        if(resolver == null)
        {
            loadResolverAsync(mContext);

            if(!mLoggedNotReady)
            {
                mLoggedNotReady = true;
                Log.d(LOG_TAG, "State dataset not loaded yet, skipping fix");
            }
            return;
        }

        mLoggedNotReady = false;

        double lat = YaV1CurrentPosition.lat;
        double lon = YaV1CurrentPosition.lon;

        String code = resolver.resolve(lat, lon);

        mEngine.onState(code, resolver.nameOf(code));
    }

    // ------------------------------------------------------ dataset loading

    private static synchronized StateResolver peekResolver()
    {
        return sResolver;
    }

    /** Kick a background load of the dataset if not done/underway yet. */
    private static synchronized void loadResolverAsync(final Context context)
    {
        if(sResolver != null || sLoading || sResolverFailed)
            return;

        sLoading = true;

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                StateResolver r = loadResolver(context);

                synchronized(GeoProfileManager.class)
                {
                    sResolver = r;
                    sLoading  = false;

                    if(r == null)
                        sResolverFailed = true;
                }
            }
        }, "yav1-geo-dataset").start();
    }

    /**
     * Load the dataset synchronously (used by the config activity and the
     * background loader). Returns null on failure.
     */
    public static StateResolver getResolverBlocking(Context context)
    {
        synchronized(GeoProfileManager.class)
        {
            if(sResolver != null)
                return sResolver;
        }

        StateResolver r = loadResolver(context);

        synchronized(GeoProfileManager.class)
        {
            if(sResolver == null && r != null)
                sResolver = r;

            return sResolver;
        }
    }

    private static StateResolver loadResolver(Context context)
    {
        InputStream in = null;

        try
        {
            in = context.getAssets().open("geo/us_states.json");

            StateResolver r = StateResolver.fromStream(in);

            Log.d(LOG_TAG, "Loaded state dataset, " + r.getStateCount() + " states");
            return r;
        }
        catch(IOException e)
        {
            Log.d(LOG_TAG, "Unable to load state dataset: " + e);
            return null;
        }
        finally
        {
            if(in != null)
            {
                try
                {
                    in.close();
                }
                catch(IOException e)
                {
                    // ignore
                }
            }
        }
    }

    // ------------------------------------------------------ real V1 gateway

    /** Production gateway backed by the YaV1 statics and push machinery. */
    private class RealGateway implements GeoProfileEngine.Gateway
    {
        @Override
        public boolean isConnected()
        {
            try
            {
                return YaV1.isClientStarted && YaV1.mV1Client != null
                        && YaV1.mV1Client.isConnected() && YaV1.sV1Settings != null;
            }
            catch(Exception e)
            {
                return false;
            }
        }

        @Override
        public boolean isDemo()
        {
            try
            {
                // mDemo tracks a running demo playback; isLibraryInDemoMode
                // covers the ESP library side. YaV1.sDemoEnable only means
                // "demo files exist in assets", so it is NOT used here.
                return com.glasslsoftware.yav1.YaV1AlertService.mDemo
                        || (YaV1.mV1Client != null && YaV1.mV1Client.isLibraryInDemoMode());
            }
            catch(Exception e)
            {
                // if we cannot tell, err on the safe side
                return true;
            }
        }

        @Override
        public boolean isAlertQuiet()
        {
            try
            {
                YaV1AlertList l = new YaV1AlertList();
                YaV1.getNewAlert(l);
                return l.isEmpty();
            }
            catch(Exception e)
            {
                // if we cannot tell, err on the safe side
                return false;
            }
        }

        @Override
        public boolean pushProfile(String profileName)
        {
            try
            {
                if(YaV1.sV1Settings == null)
                    return false;

                for(YaV1Setting s: YaV1.sV1Settings)
                {
                    if(s.getName() != null && s.getName().equals(profileName))
                        return YaV1.sV1Settings.pushSetting(s.getId());
                }

                Log.d(LOG_TAG, "Profile '" + profileName + "' not found in V1 settings");
                return false;
            }
            catch(Exception e)
            {
                Log.d(LOG_TAG, "Push failed: " + e);
                return false;
            }
        }

        @Override
        public int getCurrentSettingId()
        {
            YaV1SettingSetSafe s = new YaV1SettingSetSafe();
            return s.currentId();
        }

        @Override
        public void announce(String profileName, String stateName)
        {
            String phrase = mContext.getString(R.string.geo_switch_announce,
                    profileName, stateName);

            mAnnouncer.announce(phrase, YaV1.sPrefs.getBoolean(PREF_TTS, true));
        }

        @Override
        public void log(String msg)
        {
            Log.d(LOG_TAG, msg);
        }
    }

    /** Tiny null-safe accessor for the current setting id. */
    private static class YaV1SettingSetSafe
    {
        int currentId()
        {
            try
            {
                if(YaV1.sV1Settings != null && YaV1.sV1Settings.hasCurrent())
                    return YaV1.sV1Settings.getCurrentId();
            }
            catch(Exception e)
            {
                // fall through
            }

            return -1;
        }
    }
}
