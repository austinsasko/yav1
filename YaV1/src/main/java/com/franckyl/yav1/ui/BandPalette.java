package com.franckyl.yav1.ui;

/**
 * The radar-band palette IS the semantic system: COLOR encodes band.
 *
 * These ARGB constants are the single source of truth for band colors in Java
 * (Canvas custom views can't read colors.xml without a Context). They MUST stay
 * in sync with res/values/colors.xml (band_ka, band_k, band_x, band_ku,
 * band_laser, state_locked).
 *
 * Pure logic - unit tested in BandPaletteTest. No Android dependencies.
 */
public final class BandPalette
{
    // Band indices as defined by YaV1Alert
    public static final int LASER = 0;
    public static final int KA    = 1;
    public static final int K     = 2;
    public static final int X     = 3;
    public static final int KU    = 4;

    public static final int COLOR_KA    = 0xFFFF453A;
    public static final int COLOR_K     = 0xFFFF9F0A;
    public static final int COLOR_X     = 0xFFFFD60A;
    public static final int COLOR_KU    = 0xFF5AC8FA;
    public static final int COLOR_LASER = 0xFFFF2D55;
    public static final int COLOR_LOCKED= 0xFF6B6560;

    private BandPalette() { }

    /** ARGB color for a band index. Unknown bands fall back to the locked grey. */
    public static int colorForBand(int band)
    {
        switch(band)
        {
            case LASER: return COLOR_LASER;
            case KA:    return COLOR_KA;
            case K:     return COLOR_K;
            case X:     return COLOR_X;
            case KU:    return COLOR_KU;
            default:    return COLOR_LOCKED;
        }
    }
}
