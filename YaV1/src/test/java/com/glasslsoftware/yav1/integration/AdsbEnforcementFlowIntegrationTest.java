package com.glasslsoftware.yav1.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.aircraft.AdsbAggregator;
import com.glasslsoftware.yav1.aircraft.Aircraft;
import com.glasslsoftware.yav1.aircraft.AircraftTracker;
import com.glasslsoftware.yav1.aircraft.EnforcementWatchlist;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * [QA][integration] ADS-B aircraft awareness, end to end on real
 * collaborators with a stubbed transport:
 *
 *   ADS-B JSON (incl. live-recorded fixtures) -> AdsbAggregator.poll
 *     -> EnforcementWatchlist.match -> AircraftTracker.assess
 *     -> alert gating (cooldown)
 */
public class AdsbEnforcementFlowIntegrationTest
{
    /** transport answering by URL substring */
    private static class FakeTransport implements AdsbAggregator.Transport
    {
        final Map<String, String> byHost = new HashMap<String, String>();

        void answer(String hostFragment, String body)
        {
            byHost.put(hostFragment, body);
        }

        @Override
        public String get(String url)
        {
            for(Map.Entry<String, String> e : byHost.entrySet())
            {
                if(url.contains(e.getKey()))
                    return e.getValue();
            }
            return null;
        }
    }

    private static final String WATCHLIST_CSV =
        "A255E2,N25HP,Florida Highway Patrol,Cessna 182T,faa,high\n" +
        "A00002,N2GA,Some Agency,AS350,pol,low\n";

    private static AdsbAggregator singleFeed(FakeTransport t)
    {
        List<AdsbAggregator.Source> one = new ArrayList<AdsbAggregator.Source>();
        one.add(new AdsbAggregator.Source("feed", "https://feed.example/v2/point/%s/%s/%d"));
        return new AdsbAggregator(one, t);
    }

    private String fixture(String name) throws IOException
    {
        InputStream in = getClass().getResourceAsStream(name);
        assertTrue("fixture " + name + " missing", in != null);

        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while((line = br.readLine()) != null)
            sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private static EnforcementWatchlist watchlist() throws IOException
    {
        EnforcementWatchlist wl = new EnforcementWatchlist();
        wl.load(new StringReader(WATCHLIST_CSV));
        return wl;
    }

    @Test
    public void fixtureFlowFlagsTheAirborneTrooperOnce() throws IOException
    {
        FakeTransport t = new FakeTransport();
        t.answer("feed.example", fixture("/aircraft/adsb_point_sample.json"));

        AdsbAggregator       agg     = singleFeed(t);
        EnforcementWatchlist wl      = watchlist();
        AircraftTracker      tracker = new AircraftTracker();

        long now = 1_000_000L;
        List<Aircraft> sky = agg.poll(28.55, -81.36, 10, now);
        assertEquals(3, sky.size());

        Aircraft flagged = null;
        int watchlistHits = 0;

        for(Aircraft ac : sky)
        {
            EnforcementWatchlist.Entry entry = wl.match(ac);
            AircraftTracker.Assessment as    = tracker.assess(ac, entry != null, now);

            if(as.category == AircraftTracker.CAT_WATCHLIST)
            {
                watchlistHits++;
                flagged = ac;
                assertNotNull(entry);
                assertEquals("Florida Highway Patrol", entry.agency);
                assertFalse(entry.lowConfidence());
            }
        }

        // N25HP is airborne -> flagged; N2GA is on the ground -> suppressed
        assertEquals(1, watchlistHits);
        assertNotNull(flagged);
        assertEquals("a255e2", flagged.hex);
        assertEquals("N25HP", flagged.bestIdent());

        // alert gating: once now, throttled after, open again post-cooldown
        assertTrue(tracker.shouldAlert(flagged, now));
        assertFalse(tracker.shouldAlert(flagged, now + 60_000));
        assertTrue(tracker.shouldAlert(flagged, now + AircraftTracker.ALERT_COOLDOWN_MS + 60_001));
    }

    @Test
    public void groundedWatchlistAircraftStaysSilentThroughTheWholeChain() throws IOException
    {
        FakeTransport t = new FakeTransport();
        t.answer("feed.example", fixture("/aircraft/adsb_point_sample.json"));

        AdsbAggregator       agg     = singleFeed(t);
        EnforcementWatchlist wl      = watchlist();
        AircraftTracker      tracker = new AircraftTracker();

        List<Aircraft> sky = agg.poll(28.55, -81.36, 10, 1_000_000L);

        Aircraft parked = null;
        for(Aircraft ac : sky)
        {
            if("a00002".equals(ac.hex))
                parked = ac;
        }

        assertNotNull(parked);
        assertTrue(parked.onGround);
        assertNotNull("it IS on the watchlist", wl.match(parked));

        AircraftTracker.Assessment as = tracker.assess(parked, true, 1_000_000L);
        assertEquals("grounded watchlist aircraft must not alert",
                     AircraftTracker.CAT_NONE, as.category);
    }

    @Test
    public void circlingCessnaBecomesHeuristicSuspectAcrossPolls() throws IOException
    {
        FakeTransport t = new FakeTransport();
        AdsbAggregator       agg     = singleFeed(t);
        EnforcementWatchlist wl      = watchlist();
        AircraftTracker      tracker = new AircraftTracker();

        // not on the watchlist: only the behavior heuristic can catch it
        double tracks[] = {0, 90, 180, 270, 0};
        long   now      = 1_000_000L;

        AircraftTracker.Assessment last = null;
        Aircraft                   seen = null;

        for(double trk : tracks)
        {
            t.answer("feed.example", String.format(Locale.US,
                "{\"ac\":[{\"hex\":\"abc987\",\"r\":\"N123AB\",\"lat\":28.55,\"lon\":-81.36," +
                "\"alt_baro\":1100,\"gs\":85,\"track\":%.0f}]}", trk));

            List<Aircraft> sky = agg.poll(28.55, -81.36, 10, now);
            assertEquals(1, sky.size());

            seen = sky.get(0);
            assertNull(wl.match(seen));
            last = tracker.assess(seen, false, now);

            now += 30_000;
        }

        assertTrue(last.lowSlow);
        assertTrue(last.loiter);
        assertEquals(AircraftTracker.CAT_HEURISTIC, last.category);
        assertTrue(tracker.shouldAlert(seen, now));
        assertFalse("cooldown must throttle repeats", tracker.shouldAlert(seen, now + 30_000));
    }

    @Test
    public void liveAtlantaTrafficRaisesNoAlertsOnASinglePoll() throws IOException
    {
        FakeTransport t = new FakeTransport();
        t.answer("feed.example", fixture("/aircraft/adsbfi_atl_live.json"));

        AdsbAggregator       agg     = singleFeed(t);
        EnforcementWatchlist wl      = watchlist();
        AircraftTracker      tracker = new AircraftTracker();

        long now = 1_000_000L;
        List<Aircraft> sky = agg.poll(33.64, -84.43, 10, now);
        assertEquals(36, sky.size());

        for(Aircraft ac : sky)
        {
            AircraftTracker.Assessment as = tracker.assess(ac, wl.match(ac) != null, now);
            assertEquals("no live ATL aircraft should be suspect on one poll: " + ac.hex,
                         AircraftTracker.CAT_NONE, as.category);
        }

        assertEquals(36, tracker.trackedCount());

        // everything ages out of the tracker eventually
        tracker.prune(now + AircraftTracker.STALE_MS + 1);
        assertEquals(0, tracker.trackedCount());
    }
}
