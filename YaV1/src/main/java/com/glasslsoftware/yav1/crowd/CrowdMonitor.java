package com.glasslsoftware.yav1.crowd;

import android.content.Context;
import android.util.Log;

import com.glasslsoftware.yav1.YaV1;
import com.glasslsoftware.yav1.YaV1CurrentPosition;
import com.glasslsoftware.yav1.events.GpsEvent;
import com.glasslsoftware.yav1.poi.Announcer;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [CSA] Crowd-alert monitor, the Android mirror of the iOS awareness layer.
 *
 * When enabled (preference "crowd_alerts", default off) it polls the Waze
 * live-map feed around the vehicle — the same crowd pool Google Maps shows —
 * and, when a relay URL is configured (preference "csa_relay_url"), merges in
 * anonymous YaV1-to-YaV1 reports from the self-hosted relay. Police reports
 * are announced once each when relevant (within 1 km in any direction, or
 * within 5 km inside a 45° cone around the travel bearing), through the same
 * Announcer surface the aircraft watch uses.
 *
 * Same lifecycle pattern as AircraftMonitor: singleton created in
 * YaV1.onCreate, driven by GpsEvent on the event bus, network on a
 * single background thread, all failures soft.
 */
public class CrowdMonitor
{
    public static final String LOG_TAG = "Valentine CSA";

    private static final long POLL_INTERVAL_MS   = 60 * 1000L;
    /** poll delay doubles per all-source failure up to 60s << 3 = 8 min */
    private static final int  BACKOFF_MAX_SHIFT  = 3;
    private static final double MIN_SPEED_MS     = 2.0;
    private static final int  RELAY_RADIUS_KM    = 15;

    /** min gap between one-tap police reports; UI keeps the chip disabled for it */
    public static final long REPORT_COOLDOWN_MS  = 30 * 1000L;

    private static final float ANNOUNCE_ANY_M    = 1000f;
    private static final float ANNOUNCE_CONE_M   = 5000f;
    private static final float CONE_HALF_DEG     = 45f;

    private static CrowdMonitor sInstance = null;

    private final Context         mContext;
    private final WazeClient      mWaze = new WazeClient();
    private final ExecutorService mExecutor;
    private final AtomicBoolean   mInFlight = new AtomicBoolean(false);
    private volatile long         mLastPollMs = 0;
    private volatile int          mConsecutiveFailures = 0;
    private volatile long         mLastReportMs = 0;

    /** last merged + pruned batch (background thread writes, any thread reads) */
    private volatile List<CrowdAlert> mCurrent = new ArrayList<CrowdAlert>();

    /** report ids already spoken; pruned to live ids each cycle */
    private final Set<String> mAnnounced = new HashSet<String>();

    private CrowdMonitor(Context context)
    {
        mContext  = context;
        mExecutor = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "YaV1Crowd");
                t.setDaemon(true);
                return t;
            }
        });
    }

    /** Create the singleton and register it on the event bus. */
    public static synchronized void init(Context context)
    {
        if(sInstance != null)
            return;

        sInstance = new CrowdMonitor(context);
        YaV1.getEventBus().register(sInstance);
        Log.d(LOG_TAG, "CrowdMonitor registered");
    }

    public static CrowdMonitor getInstance()
    {
        return sInstance;
    }

    /** Current reports near the vehicle (for a future board/status surface). */
    public List<CrowdAlert> getCurrent()
    {
        return new ArrayList<CrowdAlert>(mCurrent);
    }

    /**
     * Post an anonymous police report at the current position (relay only).
     * Returns false with no fix or inside the cooldown window, so a held or
     * repeated tap can't flood the relay.
     */
    public synchronized boolean reportPoliceHere()
    {
        if(!YaV1CurrentPosition.isValid)
            return false;

        long now = System.currentTimeMillis();
        if(!cooldownElapsed(now, mLastReportMs))
            return false;
        mLastReportMs = now;

        final double lat = YaV1CurrentPosition.lat;
        final double lon = YaV1CurrentPosition.lon;

        mExecutor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                CrowdRelayClient relay = new CrowdRelayClient(
                        YaV1.sPrefs.getString("csa_relay_url", ""));
                relay.report(CrowdAlert.KIND_POLICE, lat, lon);
            }
        });
        return true;
    }

    /** Report cooldown decision. Pure, unit tested. */
    public static boolean cooldownElapsed(long nowMs, long lastReportMs)
    {
        return lastReportMs == 0 || nowMs - lastReportMs >= REPORT_COOLDOWN_MS;
    }

    // ------------------------------------------------------------ bus event

    @Subscribe
    public void onGpsEvent(GpsEvent event)
    {
        if(event.getType() != GpsEvent.Type.UPDATE)
            return;

        if(YaV1.sPrefs == null || !YaV1.sPrefs.getBoolean("crowd_alerts", false))
            return;

        if(!YaV1CurrentPosition.isValid || YaV1CurrentPosition.speed < MIN_SPEED_MS)
            return;

        long now = System.currentTimeMillis();
        if(now - mLastPollMs < nextPollDelayMs(mConsecutiveFailures))
            return;

        if(!mInFlight.compareAndSet(false, true))
            return;

        mLastPollMs = now;

        final double lat     = YaV1CurrentPosition.lat;
        final double lon     = YaV1CurrentPosition.lon;
        final int    bearing = YaV1CurrentPosition.bearing;

        mExecutor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    poll(lat, lon, bearing);
                }
                finally
                {
                    mInFlight.set(false);
                }
            }
        });
    }

    // ------------------------------------------------------------ polling

    private void poll(double lat, double lon, int bearing)
    {
        long now = System.currentTimeMillis();

        List<CrowdAlert> merged     = new ArrayList<CrowdAlert>();
        boolean          anySuccess = false;

        String feed = mWaze.fetch(lat, lon);
        if(feed != null)
        {
            anySuccess = true;
            merged.addAll(WazeFeedParser.parse(feed));
        }

        CrowdRelayClient relay = new CrowdRelayClient(
                YaV1.sPrefs.getString("csa_relay_url", ""));
        if(relay.isConfigured())
        {
            List<CrowdAlert> fromRelay = relay.fetch(lat, lon, RELAY_RADIUS_KM);
            if(fromRelay != null)
            {
                anySuccess = true;
                merged.addAll(fromRelay);
            }
        }

        // back the poll off while every source fails, to save radio/battery
        // when e.g. the unofficial Waze feed is persistently down
        mConsecutiveFailures = anySuccess ? 0 : mConsecutiveFailures + 1;

        List<CrowdAlert> pruned = WazeFeedParser.prune(merged, now);
        mCurrent = pruned;

        announceRelevant(pruned, lat, lon, bearing, now);
    }

    /**
     * Poll delay with failure backoff: the usual 60 s while healthy, doubling
     * per consecutive all-source failure up to 8 min. Pure, unit tested.
     */
    public static long nextPollDelayMs(int consecutiveFailures)
    {
        if(consecutiveFailures <= 0)
            return POLL_INTERVAL_MS;
        return POLL_INTERVAL_MS << Math.min(consecutiveFailures, BACKOFF_MAX_SHIFT);
    }

    /**
     * Announce police reports once each when relevant. Pure decision logic is
     * in {@link #isRelevant} so it can be unit tested.
     */
    private void announceRelevant(List<CrowdAlert> alerts, double lat, double lon,
                                  int bearing, long now)
    {
        List<CrowdAlert> selected;
        synchronized(mAnnounced)
        {
            selected = selectAnnouncements(alerts, mAnnounced, lat, lon, bearing);
        }

        for(CrowdAlert alert: selected)
        {
            long ageMin = alert.reportedAtMs > 0
                            ? Math.max(0, (now - alert.reportedAtMs) / 60000) : -1;
            String banner = alert.kindText()
                          + (ageMin >= 0 ? " · " + ageMin + " min ago" : "")
                          + (alert.thumbsUp > 0 ? " · +" + alert.thumbsUp : "");

            String spoken = "hidden".equals(alert.detail)
                          ? "Hidden police reported ahead" : "Police reported ahead";

            Announcer.announce(mContext, spoken, banner, styleFromPref());
        }
    }

    /**
     * Pure selection of the police alerts to announce this cycle: not yet in
     * announced and relevant per {@link #isRelevant}. Selected ids are added
     * to announced, and announced is pruned to the live ids so a report that
     * expired can announce again if it returns. Unit tested.
     */
    public static List<CrowdAlert> selectAnnouncements(List<CrowdAlert> alerts,
                                                       Set<String> announced,
                                                       double lat, double lon, int bearing)
    {
        List<CrowdAlert> selected = new ArrayList<CrowdAlert>();
        Set<String>      live     = new HashSet<String>();

        for(CrowdAlert alert: alerts)
        {
            live.add(alert.id);

            if(alert.kind != CrowdAlert.KIND_POLICE)
                continue;

            if(announced.contains(alert.id))
                continue;

            if(!isRelevant(lat, lon, bearing, alert.lat, alert.lon))
                continue;

            announced.add(alert.id);
            selected.add(alert);
        }

        announced.retainAll(live);
        return selected;
    }

    /**
     * A police report matters when it is within 1 km in any direction, or
     * within 5 km inside a 45° cone around the travel bearing.
     * Pure math (no android.location) so it is unit-testable.
     */
    public static boolean isRelevant(double lat, double lon, int travelBearing,
                                     double alertLat, double alertLon)
    {
        double distance = distanceM(lat, lon, alertLat, alertLon);

        if(distance <= ANNOUNCE_ANY_M)
            return true;

        if(distance > ANNOUNCE_CONE_M || travelBearing < 0)
            return false;

        double bearingTo = bearingDeg(lat, lon, alertLat, alertLon);
        double diff      = Math.abs(bearingTo - travelBearing) % 360.0;
        if(diff > 180.0)
            diff = 360.0 - diff;

        return diff <= CONE_HALF_DEG;
    }

    /** equirectangular distance in meters, fine at announce ranges */
    public static double distanceM(double lat1, double lon1, double lat2, double lon2)
    {
        double dLat = (lat2 - lat1) * 111320.0;
        double dLon = (lon2 - lon1) * 111320.0 * Math.cos(Math.toRadians(lat1));
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    /** initial bearing in degrees (0 = north) from point 1 to point 2 */
    public static double bearingDeg(double lat1, double lon1, double lat2, double lon2)
    {
        double dLon = Math.toRadians(lon2 - lon1);
        double la1  = Math.toRadians(lat1);
        double la2  = Math.toRadians(lat2);
        double y    = Math.sin(dLon) * Math.cos(la2);
        double x    = Math.cos(la1) * Math.sin(la2) - Math.sin(la1) * Math.cos(la2) * Math.cos(dLon);
        double deg  = Math.toDegrees(Math.atan2(y, x));
        return deg < 0 ? deg + 360.0 : deg;
    }

    private int styleFromPref()
    {
        String s = YaV1.sPrefs.getString("crowd_alert_style", "0");
        if("1".equals(s))
            return Announcer.STYLE_TTS_ONLY;
        if("2".equals(s))
            return Announcer.STYLE_SOUND_ONLY;
        return Announcer.STYLE_TTS_AND_SOUND;
    }
}
