package com.glasslsoftware.yav1.psl;

import android.os.SystemClock;
import android.util.Log;

import com.glasslsoftware.yav1.YaV1;
import com.glasslsoftware.yav1.YaV1CurrentPosition;
import com.glasslsoftware.yav1.events.GpsEvent;
import com.squareup.otto.Subscribe;

import java.io.File;

/**
 * [P1-PSL] Glue between the alert service and the speed-limit machinery.
 *
 * shouldMute() is the single entry point called from the muting decision
 * path in YaV1AlertService. It is cheap and never blocks: prefs are
 * in-memory, limits come from the provider cache, network happens on a
 * background thread.
 *
 * On first use it registers itself on the app event bus so GPS updates
 * keep the limit cache warm while driving, even between alerts.
 *
 * Preferences (see res/xml/pref_psl.xml):
 *   psl_enable   - master toggle, default off
 *   psl_offset   - allowed speed over the limit, in the display unit
 *   psl_unknown  - behavior when the limit is unknown ("0" alert, "1" mute)
 * Debug:
 *   psl_debug_stub / psl_debug_stub_kph - fixed-limit stub provider
 */
public class PslMute
{
    public static final String TAG = "Valentine PSL";

    public static final String PREF_ENABLE     = "psl_enable";
    public static final String PREF_OFFSET     = "psl_offset";
    public static final String PREF_UNKNOWN    = "psl_unknown";
    public static final String PREF_DEBUG_STUB = "psl_debug_stub";
    public static final String PREF_DEBUG_KPH  = "psl_debug_stub_kph";

    public static final String CACHE_FILE_NAME = "psl_cache.json";

    private static final long LOG_THROTTLE_MS = 2000;

    private static final Object sLock = new Object();

    private static SpeedLimitProvider sProvider  = null;
    private static PslMuteDecider     sDecider   = new PslMuteDecider();
    private static PslMute            sBusClient = null;

    private static boolean sLastDecision = false;
    private static long    sLastLogMs    = 0;

    private PslMute()
    {
    }

    /**
     * Decide whether alerts should be muted because we are at/below the
     * posted speed limit plus the user offset. Fail soft: any problem
     * means "do not mute".
     */
    public static boolean shouldMute()
    {
        try
        {
            if(YaV1.sPrefs == null || !YaV1.sPrefs.getBoolean(PREF_ENABLE, false))
            {
                sDecider.reset();
                sLastDecision = false;
                return false;
            }

            if(!YaV1CurrentPosition.isValid)
                return false;

            ensureInit();

            double lat      = YaV1CurrentPosition.lat;
            double lon      = YaV1CurrentPosition.lon;
            float  bearing  = YaV1CurrentPosition.bearing;

            // cSpeed is kept in the current display unit (YaV1.sCurrUnit)
            double speedKph = PslMuteDecider.toKph(YaV1CurrentPosition.cSpeed, YaV1.sCurrUnit);

            // speed hint drives the provider's prefetch look-ahead
            if(sProvider instanceof OverpassSpeedLimitProvider)
                ((OverpassSpeedLimitProvider) sProvider).setSpeedHintMps(speedKph / 3.6);

            Integer limit   = sProvider.getSpeedLimitKph(lat, lon, bearing);

            double offsetKph = PslMuteDecider.toKph(getIntPref(PREF_OFFSET, 5), YaV1.sCurrUnit);
            boolean muteUnk  = "1".equals(YaV1.sPrefs.getString(PREF_UNKNOWN, "0"));

            boolean rc = sDecider.decide(SystemClock.elapsedRealtime(),
                                         speedKph, limit, offsetKph, muteUnk);

            long now = SystemClock.elapsedRealtime();
            if(rc != sLastDecision || now - sLastLogMs >= LOG_THROTTLE_MS)
            {
                sLastLogMs = now;
                Log.d(TAG, "decide mute=" + rc
                           + " speedKph=" + Math.round(speedKph)
                           + " limitKph=" + limit
                           + " offsetKph=" + Math.round(offsetKph)
                           + " unknownMutes=" + muteUnk);
            }
            sLastDecision = rc;

            return rc;
        }
        catch(Exception exc)
        {
            Log.d(TAG, "shouldMute failed soft: " + exc);
            return false;
        }
    }

    // -- internals ---------------------------------------------------------

    private static void ensureInit()
    {
        synchronized(sLock)
        {
            if(sProvider == null)
            {
                if(YaV1.sPrefs.getBoolean(PREF_DEBUG_STUB, false))
                {
                    sProvider = new StubSpeedLimitProvider(getIntPref(PREF_DEBUG_KPH, 50));
                    Log.d(TAG, "using stub provider, limit " + getIntPref(PREF_DEBUG_KPH, 50) + " kph");
                }
                else
                {
                    File root = YaV1.getStorageRootDir();
                    File f    = (root != null ? new File(root, CACHE_FILE_NAME) : null);
                    sProvider = new OverpassSpeedLimitProvider(f);
                }
            }

            if(sBusClient == null)
            {
                sBusClient = new PslMute();
                try
                {
                    YaV1.getEventBus().register(sBusClient);
                }
                catch(Exception exc)
                {
                    // bus not available: prefetch only happens from alerts
                    sBusClient = null;
                }
            }
        }
    }

    /**
     * GPS update: keep the limit cache warm while driving so the answer
     * is already local when an alert arrives. The provider applies its
     * own rate limits; this is cheap.
     */
    @Subscribe
    public void onGpsEvent(GpsEvent evt)
    {
        try
        {
            if(evt == null || evt.getType() != GpsEvent.Type.UPDATE)
                return;
            if(YaV1.sPrefs == null || !YaV1.sPrefs.getBoolean(PREF_ENABLE, false))
                return;
            if(!YaV1CurrentPosition.isValid)
                return;

            SpeedLimitProvider p = sProvider;
            if(p == null)
                return;

            if(p instanceof OverpassSpeedLimitProvider)
                ((OverpassSpeedLimitProvider) p).setSpeedHintMps(YaV1CurrentPosition.speed);

            p.getSpeedLimitKph(YaV1CurrentPosition.lat, YaV1CurrentPosition.lon,
                               YaV1CurrentPosition.bearing);
        }
        catch(Exception exc)
        {
            // never let a prefetch problem reach the bus
        }
    }

    private static int getIntPref(String key, int dflt)
    {
        try
        {
            return Integer.parseInt(YaV1.sPrefs.getString(key, String.valueOf(dflt)).trim());
        }
        catch(Exception exc)
        {
            return dflt;
        }
    }

    /** visible for the emulator functional pass / debugging */
    public static void resetForDebug()
    {
        synchronized(sLock)
        {
            sProvider = null;
            sDecider.reset();
        }
    }
}
