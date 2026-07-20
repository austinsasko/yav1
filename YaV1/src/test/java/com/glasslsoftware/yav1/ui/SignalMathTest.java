package com.glasslsoftware.yav1.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SignalMathTest
{
    @Test
    public void zeroOrNegativeStrengthLightsNoBars()
    {
        assertEquals(0, SignalMath.barsFor(0, 8, 6));
        assertEquals(0, SignalMath.barsFor(-3, 8, 6));
    }

    @Test
    public void fullStrengthLightsAllBars()
    {
        assertEquals(6, SignalMath.barsFor(8, 8, 6));
        assertEquals(6, SignalMath.barsFor(9, 8, 6)); // clamps above full scale
    }

    @Test
    public void anyPositiveStrengthLightsAtLeastOneBar()
    {
        assertEquals(1, SignalMath.barsFor(1, 8, 6));
    }

    @Test
    public void proportionalMappingUsesCeil()
    {
        // 4/8 * 6 = 3.0 -> 3
        assertEquals(3, SignalMath.barsFor(4, 8, 6));
        // 5/8 * 6 = 3.75 -> ceil 4
        assertEquals(4, SignalMath.barsFor(5, 8, 6));
        // 2/8 * 6 = 1.5 -> ceil 2
        assertEquals(2, SignalMath.barsFor(2, 8, 6));
    }

    @Test
    public void degenerateScalesReturnZero()
    {
        assertEquals(0, SignalMath.barsFor(5, 0, 6));
        assertEquals(0, SignalMath.barsFor(5, 8, 0));
    }
}
