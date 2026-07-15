package com.glasslsoftware.yav1.psl;

/**
 * [P1-PSL] Debug provider returning a fixed limit everywhere.
 *
 * Enabled with the hidden boolean pref "psl_debug_stub" (limit from
 * "psl_debug_stub_kph", default 50). Lets the whole mute path be
 * exercised on an emulator without network access.
 */
public class StubSpeedLimitProvider implements SpeedLimitProvider
{
    private final int mLimitKph;

    public StubSpeedLimitProvider(int limitKph)
    {
        mLimitKph = limitKph;
    }

    @Override
    public Integer getSpeedLimitKph(double lat, double lon, float bearing)
    {
        return Integer.valueOf(mLimitKph);
    }
}
