package com.glasslsoftware.yav1.aircraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * [QA] Component tests for AdsbAggregator failure handling beyond
 * AdsbAggregatorTest: exponential backoff growth with its cap, and the
 * guarantee that no transport request happens while every feed is down.
 */
public class AdsbAggregatorBackoffTest
{
    /** transport that always fails and counts calls */
    private static class DeadTransport implements AdsbAggregator.Transport
    {
        int calls = 0;

        @Override
        public String get(String url)
        {
            calls++;
            return null;
        }
    }

    private static List<AdsbAggregator.Source> oneSource()
    {
        List<AdsbAggregator.Source> s = new ArrayList<AdsbAggregator.Source>();
        s.add(new AdsbAggregator.Source("only", "https://only.example/v2/point/%s/%s/%d"));
        return s;
    }

    @Test
    public void backoffDoublesPerFailureAndCapsAtFifteenMinutes()
    {
        DeadTransport t = new DeadTransport();
        AdsbAggregator agg = new AdsbAggregator(oneSource(), t);
        AdsbAggregator.Source src = agg.getSources().get(0);

        long now = 1_000_000L;
        long expected[] = {
            AdsbAggregator.BACKOFF_BASE_MS,           // 60s
            AdsbAggregator.BACKOFF_BASE_MS * 2,       // 120s
            AdsbAggregator.BACKOFF_BASE_MS * 4,       // 240s
            AdsbAggregator.BACKOFF_BASE_MS * 8,       // 480s
            AdsbAggregator.BACKOFF_MAX_MS,            // 960s > cap -> 900s
            AdsbAggregator.BACKOFF_MAX_MS,            // stays capped
        };

        for(int i = 0; i < expected.length; i++)
        {
            agg.poll(30.0, -95.0, 10, now);

            assertEquals(i + 1, src.getFailures());
            assertFalse("failure " + (i + 1) + ": must be in backoff",
                        src.healthy(now + expected[i] - 1));
            assertTrue("failure " + (i + 1) + ": backoff must end on time",
                       src.healthy(now + expected[i]));

            // next poll exactly when the backoff expires
            now += expected[i];
        }

        assertEquals(expected.length, t.calls);
    }

    @Test
    public void noTransportCallsWhileEveryFeedIsBackedOff()
    {
        DeadTransport t = new DeadTransport();

        List<AdsbAggregator.Source> three = new ArrayList<AdsbAggregator.Source>();
        three.add(new AdsbAggregator.Source("a", "https://a.example/v2/point/%s/%s/%d"));
        three.add(new AdsbAggregator.Source("b", "https://b.example/v2/point/%s/%s/%d"));
        three.add(new AdsbAggregator.Source("c", "https://c.example/v2/point/%s/%s/%d"));

        AdsbAggregator agg = new AdsbAggregator(three, t);

        long now = 1_000_000L;
        agg.poll(30.0, -95.0, 10, now);
        agg.poll(30.0, -95.0, 10, now + 1000);
        agg.poll(30.0, -95.0, 10, now + 2000);
        assertEquals(3, t.calls);

        // all three now in their 60s backoff: polls are free of requests
        assertTrue(agg.poll(30.0, -95.0, 10, now + 10_000).isEmpty());
        assertTrue(agg.poll(30.0, -95.0, 10, now + 30_000).isEmpty());
        assertEquals("no feed may be contacted during backoff", 3, t.calls);
    }

    @Test
    public void emptySourceListNeverPollsAndNeverThrows()
    {
        DeadTransport t = new DeadTransport();
        AdsbAggregator agg = new AdsbAggregator(new ArrayList<AdsbAggregator.Source>(), t);

        assertTrue(agg.poll(30.0, -95.0, 10, 1_000_000L).isEmpty());
        assertEquals(0, t.calls);
    }
}
