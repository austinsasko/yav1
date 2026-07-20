package com.glasslsoftware.yav1.crowd;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
    public interface ReportCallback
    {
        /** Called on the main thread after the relay accepts or rejects the report. */
        void onComplete(boolean success);
    }

    public static final String LOG_TAG = "Valentine CSA";

    private static final long POLL_INTERVAL_MS   = 60 * 1000L;
    private static final double MIN_SPEED_MS     = 2.0;
    private static final int  RELAY_RADIUS_KM    = 15;

    private static final float ANNOUNCE_ANY_M    = 1000f;
    private static final float ANNOUNCE_CONE_M   = 5000f;
    private static final float CONE_HALF_DEG     = 45f;

    private static CrowdMonitor sInstance = null;

    private final Context         mContext;
    private final WazeClient      mWaze = new WazeClient();
    private final ExecutorService mExecutor;
    private final Handler         mMainHandler;
    private final AtomicBoolean   mInFlight = new AtomicBoolean(false);
    private volatile long         mLastPollMs = 0;

    /** last merged + pruned batch (background thread writes, any thread reads) */
    private volatile List<CrowdAlert> mCurrent = new ArrayList<CrowdAlert>();

    /** Last successful source batches; a soft network failure must not erase them. */
    private List<CrowdAlert> mWazeCurrent  = new ArrayList<CrowdAlert>();
    private List<CrowdAlert> mRelayCurrent = new ArrayList<CrowdAlert>();

    /** report ids already spoken; pruned to live ids each cycle */
    private final Set<String> mAnnounced = new HashSet<String>();

    private CrowdMonitor(Context context)
    {
        mContext  = context;
        mMainHandler = new Handler(Looper.getMainLooper());
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

    /** Post an anonymous police report at the current position (relay only). */
    public void reportPoliceHere(final ReportCallback callback)
    {
        if(!YaV1CurrentPosition.isValid)
        {
            deliverReportResult(callback, false);
            return;
        }

        final double lat = YaV1CurrentPosition.lat;
        final double lon = YaV1CurrentPosition.lon;

        mExecutor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                CrowdRelayClient relay = new CrowdRelayClient(
                        YaV1.sPrefs.getString("csa_relay_url", ""));
                deliverReportResult(callback,
                        relay.report(CrowdAlert.KIND_POLICE, lat, lon));
            }
        });
    }

    private void deliverReportResult(final ReportCallback callback, final boolean success)
    {
        if(callback == null)
            return;

        mMainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onComplete(success);
            }
        });
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
        if(now - mLastPollMs < POLL_INTERVAL_MS)
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

        String feed = mWaze.fetch(lat, lon);
        List<CrowdAlert> fromWaze = feed == null ? null : WazeFeedParser.parse(feed);
        mWazeCurrent = batchAfterFetch(mWazeCurrent, fromWaze, now);

        CrowdRelayClient relay = new CrowdRelayClient(
                YaV1.sPrefs.getString("csa_relay_url", ""));
        if(relay.isConfigured())
        {
            List<CrowdAlert> fromRelay = relay.fetch(lat, lon, RELAY_RADIUS_KM);
            mRelayCurrent = batchAfterFetch(mRelayCurrent, fromRelay, now);
        }
        else
            mRelayCurrent = new ArrayList<CrowdAlert>();

        List<CrowdAlert> merged = new ArrayList<CrowdAlert>();
        merged.addAll(mWazeCurrent);
        merged.addAll(mRelayCurrent);

        List<CrowdAlert> pruned = WazeFeedParser.prune(merged, now);
        mCurrent = pruned;

        announceRelevant(pruned, lat, lon, bearing, now);
    }

    /** Replace a source only after a successful fetch; always age out stale reports. */
    static List<CrowdAlert> batchAfterFetch(List<CrowdAlert> previous,
                                            List<CrowdAlert> fetched, long now)
    {
        List<CrowdAlert> source = fetched != null ? fetched : previous;
        if(source == null)
            source = new ArrayList<CrowdAlert>();
        return WazeFeedParser.prune(source, now);
    }

    /**
     * Announce police reports once each when relevant. Pure decision logic is
     * in {@link #isRelevant} so it can be unit tested.
     */
    private void announceRelevant(List<CrowdAlert> alerts, double lat, double lon,
                                  int bearing, long now)
    {
        Set<String> live = new HashSet<String>();

        for(CrowdAlert alert: alerts)
        {
            live.add(alert.id);

            if(alert.kind != CrowdAlert.KIND_POLICE)
                continue;

            synchronized(mAnnounced)
            {
                if(mAnnounced.contains(alert.id))
                    continue;
            }

            if(!isRelevant(lat, lon, bearing, alert.lat, alert.lon))
                continue;

            synchronized(mAnnounced)
            {
                mAnnounced.add(alert.id);
            }

            long ageMin = alert.reportedAtMs > 0
                            ? Math.max(0, (now - alert.reportedAtMs) / 60000) : -1;
            String banner = alert.kindText()
                          + (ageMin >= 0 ? " · " + ageMin + " min ago" : "")
                          + (alert.thumbsUp > 0 ? " · +" + alert.thumbsUp : "");

            String spoken = "hidden".equals(alert.detail)
                          ? "Hidden police reported ahead" : "Police reported ahead";

            Announcer.announce(mContext, spoken, banner, styleFromPref());
        }

        // a report that expired can announce again if it returns
        synchronized(mAnnounced)
        {
            mAnnounced.retainAll(live);
        }
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
