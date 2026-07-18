package com.glasslsoftware.yav1.psl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * [QA] Boundary-condition component tests for PslMuteDecider, complementing
 * PslMuteDeciderTest: exact hysteresis-band edges, unknown-limit transitions
 * back to a known limit, zero/negative parameter combinations.
 *
 * Base scenario: limit 50 kph, offset 8 kph -> threshold 58, band top 60.
 */
public class PslMuteDeciderBoundaryTest
{
    private static final Integer LIMIT  = 50;
    private static final double  OFFSET = 8.0;

    private PslMuteDecider mDecider;

    @Before
    public void setUp()
    {
        mDecider = new PslMuteDecider();
    }

    private boolean decide(long now, double speed)
    {
        return mDecider.decide(now, speed, LIMIT, OFFSET, false);
    }

    @Test
    public void speedExactlyAtBandTopNeverStartsTheExitTimer()
    {
        assertTrue(decide(0, 40.0));
        // 60.0 == threshold + HYSTERESIS_KPH: not strictly above the band
        assertTrue(decide(1000, 60.0));
        assertTrue(decide(500000, 60.0));   // minutes later: still muted
    }

    @Test
    public void dippingToExactBandTopResetsTheExitTimer()
    {
        assertTrue(decide(0, 40.0));
        assertTrue(decide(1000, 61.0));     // above band: timer starts
        assertTrue(decide(2000, 60.0));     // exactly band top: timer reset
        assertTrue(decide(4500, 61.0));     // timer restarts here
        assertTrue(decide(6000, 61.0));     // 1500ms: still muted
        assertFalse(decide(6600, 61.0));    // 2100ms: released
    }

    @Test
    public void limitReturningWhileSlowKeepsTheMute()
    {
        // unknown limit, configured to mute
        assertTrue(mDecider.decide(0, 40.0, null, OFFSET, true));
        // limit comes back while below the threshold: still muted, no glitch
        assertTrue(decide(1000, 40.0));
        assertTrue(mDecider.isMuted());
    }

    @Test
    public void limitReturningWhileFastReleasesViaHysteresis()
    {
        assertTrue(mDecider.decide(0, 40.0, null, OFFSET, true));
        // the unknown-limit mute cleared the hysteresis state, so a known
        // limit with a speeding vehicle starts the exit timer from scratch
        assertTrue(decide(1000, 100.0));
        assertFalse(decide(3500, 100.0));
    }

    @Test
    public void zeroSpeedIsAValidSpeedAndMutes()
    {
        assertTrue(decide(0, 0.0));
        assertTrue(mDecider.isMuted());
    }

    @Test
    public void negativeOffsetShrinksTheThreshold()
    {
        // limit 50, offset -5: threshold 45
        assertFalse(mDecider.decide(0, 50.0, LIMIT, -5.0, false));
        assertTrue(mDecider.decide(1000, 45.0, LIMIT, -5.0, false));
    }

    @Test
    public void isMutedReportsWithoutReevaluating()
    {
        assertFalse(mDecider.isMuted());
        decide(0, 40.0);
        assertTrue(mDecider.isMuted());
        // isMuted() alone never flips the state
        assertTrue(mDecider.isMuted());
    }
}
