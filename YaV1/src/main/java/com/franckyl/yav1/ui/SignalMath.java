package com.franckyl.yav1.ui;

/**
 * Pure signal-strength helpers shared by the custom drive views.
 * No Android dependencies - unit tested in SignalMathTest.
 */
public final class SignalMath
{
    private SignalMath() { }

    /**
     * Number of lit bars to draw for a raw V1 signal strength.
     *
     * @param strength    raw strength (V1 reports 0..8; 0 means "junk out")
     * @param maxStrength full-scale strength (typically 8)
     * @param barCount    how many bars the view draws (typically 6)
     * @return lit bars clamped to [0, barCount]; a positive strength always
     *         lights at least one bar (ceil), 0/negative lights none.
     */
    public static int barsFor(int strength, int maxStrength, int barCount)
    {
        if(strength <= 0 || maxStrength <= 0 || barCount <= 0)
            return 0;
        if(strength >= maxStrength)
            return barCount;
        int bars = (int) Math.ceil((double) strength / maxStrength * barCount);
        if(bars < 1)     bars = 1;
        if(bars > barCount) bars = barCount;
        return bars;
    }
}
