package com.glasslsoftware.yav1.aircraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [P2-ADSB] Multi-source aggregation: rotation, merge-by-hex with
 * source counting, conflicting positions, missing fields, staleness
 * aging and per-feed backoff. All time injected, transport stubbed.
 */
public class AdsbAggregatorTest
{
    // -- test harness --------------------------------------------------------

    /** transport that answers per URL substring and records requests */
    private static class FakeTransport implements AdsbAggregator.Transport
    {
        final Map<String, String> byHost = new HashMap<String, String>();
        final List<String>        requests = new ArrayList<String>();

        void answer(String hostFragment, String body)
        {
            byHost.put(hostFragment, body);
        }

        @Override
        public String get(String url)
        {
            requests.add(url);
            for(Map.Entry<String, String> e : byHost.entrySet())
            {
                if(url.contains(e.getKey()))
                    return e.getValue();
            }
            return null;
        }
    }

    private static List<AdsbAggregator.Source> threeSources()
    {
        List<AdsbAggregator.Source> s = new ArrayList<AdsbAggregator.Source>();
        s.add(new AdsbAggregator.Source("lol", "https://lol.example/v2/point/%s/%s/%d"));
        s.add(new AdsbAggregator.Source("live", "https://live.example/v2/point/%s/%s/%d"));
        s.add(new AdsbAggregator.Source("fi", "https://fi.example/api/v2/lat/%s/lon/%s/dist/%d"));
        return s;
    }

    private static String ac(String hex, double lat, double lon, int alt)
    {
        return String.format(java.util.Locale.US,
            "{\"hex\":\"%s\",\"lat\":%f,\"lon\":%f,\"alt_baro\":%d,\"gs\":100,\"track\":90}",
            hex, lat, lon, alt);
    }

    private static String body(String... acs)
    {
        StringBuilder sb = new StringBuilder("{\"ac\":[");
        for(int i = 0; i < acs.length; i++)
        {
            if(i > 0) sb.append(',');
            sb.append(acs[i]);
        }
        return sb.append("]}").toString();
    }

    // -- rotation ------------------------------------------------------------

    @Test
    public void pollsRotateAcrossHealthySources()
    {
        FakeTransport t = new FakeTransport();
        t.answer(".example", body());

        AdsbAggregator agg = new AdsbAggregator(threeSources(), t);

        long now = 1_000_000L;
        agg.poll(30.0, -95.0, 10, now);
        agg.poll(30.0, -95.0, 10, now + 30000);
        agg.poll(30.0, -95.0, 10, now + 60000);
        agg.poll(30.0, -95.0, 10, now + 90000);

        assertEquals(4, t.requests.size());
        assertTrue(t.requests.get(0).contains("lol.example"));
        assertTrue(t.requests.get(1).contains("live.example"));
        assertTrue(t.requests.get(2).contains("fi.example"));
        assertTrue(t.requests.get(3).contains("lol.example"));   // wrapped around
    }

    @Test
    public void adsbFiUrlShapeIsBuiltCorrectly()
    {
        FakeTransport t = new FakeTransport();
        t.answer(".example", body());

        List<AdsbAggregator.Source> one = new ArrayList<AdsbAggregator.Source>();
        one.add(new AdsbAggregator.Source("fi", "https://fi.example/api/v2/lat/%s/lon/%s/dist/%d"));

        new AdsbAggregator(one, t).poll(33.64, -84.43, 25, 0);

        assertEquals("https://fi.example/api/v2/lat/33.6400/lon/-84.4300/dist/25",
                     t.requests.get(0));
    }

    // -- merging -------------------------------------------------------------

    @Test
    public void sameHexFromTwoFeedsMergesWithSourceCount2()
    {
        FakeTransport t = new FakeTransport();
        // conflicting positions for the same hex: freshest fetch must win
        t.answer("lol.example",  body(ac("abc123", 30.00, -95.00, 1500)));
        t.answer("live.example", body(ac("abc123", 30.01, -95.01, 1600)));

        AdsbAggregator agg = new AdsbAggregator(threeSources(), t);

        long now = 1_000_000L;
        agg.poll(30.0, -95.0, 10, now);                       // lol @ t0
        List<Aircraft> merged = agg.poll(30.0, -95.0, 10, now + 30000);  // live @ t0+30

        assertEquals(1, merged.size());
        Aircraft m = merged.get(0);
        assertEquals("abc123", m.hex);
        assertEquals(2, m.sourceCount);
        // freshest (live) position won the conflict
        assertEquals(30.01, m.lat, 1e-9);
        assertEquals(1600, m.altFt);
        assertEquals("live", m.feed);
        assertEquals(now + 30000, m.seenAtMs);
    }

    @Test
    public void missingFieldsFromOneFeedDoNotPoisonTheMerge()
    {
        FakeTransport t = new FakeTransport();
        t.answer("lol.example",
            body("{\"hex\":\"abc123\",\"lat\":30.0,\"lon\":-95.0}"));   // no alt/gs/track
        t.answer("live.example", body(ac("abc123", 30.0, -95.0, 1500)));

        AdsbAggregator agg = new AdsbAggregator(threeSources(), t);

        long now = 1_000_000L;
        agg.poll(30.0, -95.0, 10, now);                        // sparse entry
        List<Aircraft> merged = agg.poll(30.0, -95.0, 10, now + 30000);

        assertEquals(1, merged.size());
        assertEquals(2, merged.get(0).sourceCount);
        assertEquals(1500, merged.get(0).altFt);                // fresh full entry won
    }

    @Test
    public void distinctHexesFromDifferentFeedsAllAppear()
    {
        FakeTransport t = new FakeTransport();
        t.answer("lol.example",  body(ac("aaa111", 30.0, -95.0, 1000)));
        t.answer("live.example", body(ac("bbb222", 30.1, -95.1, 2000)));

        AdsbAggregator agg = new AdsbAggregator(threeSources(), t);

        long now = 1_000_000L;
        agg.poll(30.0, -95.0, 10, now);
        List<Aircraft> merged = agg.poll(30.0, -95.0, 10, now + 30000);

        assertEquals(2, merged.size());
        for(Aircraft ac : merged)
            assertEquals(1, ac.sourceCount);
    }

    // -- staleness -----------------------------------------------------------

    @Test
    public void singleSourceEntriesAgeOutFaster()
    {
        FakeTransport t = new FakeTransport();
        t.answer("lol.example", body(ac("aaa111", 30.0, -95.0, 1000)));
        // the other feeds return an empty sky
        t.answer("live.example", body());
        t.answer("fi.example",   body());

        AdsbAggregator agg = new AdsbAggregator(threeSources(), t);

        long now = 1_000_000L;
        agg.poll(30.0, -95.0, 10, now);                        // lol sees aaa111

        // still merged before the single-source age limit
        assertEquals(1, agg.merge(now + AdsbAggregator.SINGLE_SOURCE_MAX_AGE_MS - 1).size());

        // aged out after it, even though the feed window is still open
        assertEquals(0, agg.merge(now + AdsbAggregator.SINGLE_SOURCE_MAX_AGE_MS + 1).size());
    }

    @Test
    public void twoSourceEntriesSurviveUntilTheMergeWindowCloses()
    {
        FakeTransport t = new FakeTransport();
        t.answer("lol.example",  body(ac("aaa111", 30.0, -95.0, 1000)));
        t.answer("live.example", body(ac("aaa111", 30.0, -95.0, 1000)));

        AdsbAggregator agg = new AdsbAggregator(threeSources(), t);

        long now = 1_000_000L;
        agg.poll(30.0, -95.0, 10, now);            // lol
        agg.poll(30.0, -95.0, 10, now + 1000);     // live

        // alive past the single-source limit because two feeds agree
        assertEquals(1, agg.merge(now + AdsbAggregator.SINGLE_SOURCE_MAX_AGE_MS + 5000).size());

        // gone once the whole feed window expires
        assertEquals(0, agg.merge(now + AdsbAggregator.MERGE_WINDOW_MS + 2000).size());
    }

    // -- health / backoff ----------------------------------------------------

    @Test
    public void failingFeedBacksOffAndRotationSkipsIt()
    {
        FakeTransport t = new FakeTransport();
        // lol always fails (no answer configured), the others work
        t.answer("live.example", body(ac("aaa111", 30.0, -95.0, 1000)));
        t.answer("fi.example",   body());

        AdsbAggregator agg = new AdsbAggregator(threeSources(), t);

        long now = 1_000_000L;
        agg.poll(30.0, -95.0, 10, now);            // lol fails -> 60s backoff
        agg.poll(30.0, -95.0, 10, now + 10000);    // live
        agg.poll(30.0, -95.0, 10, now + 20000);    // fi
        agg.poll(30.0, -95.0, 10, now + 30000);    // lol in backoff -> skipped, live again

        assertEquals(4, t.requests.size());
        assertTrue(t.requests.get(3).contains("live.example"));

        AdsbAggregator.Source lol = agg.getSources().get(0);
        assertFalse(lol.healthy(now + 30000));
        assertEquals(1, lol.getFailures());
    }

    @Test
    public void failedFeedRecoversAfterBackoffExpires()
    {
        FakeTransport t = new FakeTransport();
        t.answer("live.example", body());
        t.answer("fi.example",   body());

        AdsbAggregator agg = new AdsbAggregator(threeSources(), t);

        long now = 1_000_000L;
        agg.poll(30.0, -95.0, 10, now);            // lol fails, 60s backoff

        // after the backoff expires the rotation offers lol again
        t.answer("lol.example", body(ac("ccc333", 30.0, -95.0, 900)));

        agg.poll(30.0, -95.0, 10, now + 30000);    // live
        agg.poll(30.0, -95.0, 10, now + 60000);    // fi
        List<Aircraft> merged =
            agg.poll(30.0, -95.0, 10, now + AdsbAggregator.BACKOFF_BASE_MS + 61000); // lol again

        assertTrue(t.requests.get(3).contains("lol.example"));
        assertEquals(1, merged.size());
        assertEquals("ccc333", merged.get(0).hex);
        assertEquals(0, agg.getSources().get(0).getFailures());
    }

    @Test
    public void allFeedsDownYieldsEmptyMergeNotAnError()
    {
        FakeTransport t = new FakeTransport();      // answers nothing

        AdsbAggregator agg = new AdsbAggregator(threeSources(), t);

        long now = 1_000_000L;
        assertTrue(agg.poll(30.0, -95.0, 10, now).isEmpty());
        assertTrue(agg.poll(30.0, -95.0, 10, now + 30000).isEmpty());
        assertTrue(agg.poll(30.0, -95.0, 10, now + 60000).isEmpty());

        // every source is now in backoff: poll must not throw
        assertTrue(agg.poll(30.0, -95.0, 10, now + 61000).isEmpty());
    }

    @Test
    public void garbageBodyCountsAsFeedFailure()
    {
        FakeTransport t = new FakeTransport();
        t.answer("lol.example", "<html>captive portal</html>");
        t.answer("live.example", body(ac("aaa111", 30.0, -95.0, 1000)));
        t.answer("fi.example",   body());

        AdsbAggregator agg = new AdsbAggregator(threeSources(), t);

        long now = 1_000_000L;
        agg.poll(30.0, -95.0, 10, now);

        assertEquals(1, agg.getSources().get(0).getFailures());
        assertFalse(agg.getSources().get(0).healthy(now + 1000));
    }

    // -- live-recorded adsb.fi dialect ----------------------------------------

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

    @Test
    public void liveAdsbFiPayloadFlowsThroughTheAggregator() throws IOException
    {
        // recorded live 2026-07-14 from opendata.adsb.fi (Atlanta, 10nm):
        // readsb dialect with the "aircraft" array key
        final String fiBody = fixture("/aircraft/adsbfi_atl_live.json");

        FakeTransport t = new FakeTransport();
        t.answer("fi.example", fiBody);

        List<AdsbAggregator.Source> one = new ArrayList<AdsbAggregator.Source>();
        one.add(new AdsbAggregator.Source("adsb.fi",
                    "https://fi.example/api/v2/lat/%s/lon/%s/dist/%d"));

        AdsbAggregator agg = new AdsbAggregator(one, t);
        List<Aircraft> merged = agg.poll(33.64, -84.43, 10, 1_000_000L);

        assertEquals(36, merged.size());
        for(Aircraft ac : merged)
        {
            assertTrue(ac.hasPosition());
            assertEquals("adsb.fi", ac.feed);
            assertEquals(1, ac.sourceCount);
        }
    }
}
