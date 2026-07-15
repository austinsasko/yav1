package com.franckyl.yav1.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BandPaletteTest
{
    @Test
    public void eachBandMapsToItsPaletteColor()
    {
        assertEquals(BandPalette.COLOR_LASER, BandPalette.colorForBand(BandPalette.LASER));
        assertEquals(BandPalette.COLOR_KA,    BandPalette.colorForBand(BandPalette.KA));
        assertEquals(BandPalette.COLOR_K,     BandPalette.colorForBand(BandPalette.K));
        assertEquals(BandPalette.COLOR_X,     BandPalette.colorForBand(BandPalette.X));
        assertEquals(BandPalette.COLOR_KU,    BandPalette.colorForBand(BandPalette.KU));
    }

    @Test
    public void unknownBandFallsBackToLocked()
    {
        assertEquals(BandPalette.COLOR_LOCKED, BandPalette.colorForBand(99));
        assertEquals(BandPalette.COLOR_LOCKED, BandPalette.colorForBand(-1));
    }

    @Test
    public void paletteConstantsMatchColorsXml()
    {
        // Guard against drift from res/values/colors.xml
        assertEquals(0xFFFF453A, BandPalette.COLOR_KA);
        assertEquals(0xFFFF9F0A, BandPalette.COLOR_K);
        assertEquals(0xFFFFD60A, BandPalette.COLOR_X);
        assertEquals(0xFF5AC8FA, BandPalette.COLOR_KU);
        assertEquals(0xFFFF2D55, BandPalette.COLOR_LASER);
        assertEquals(0xFF6B6560, BandPalette.COLOR_LOCKED);
    }
}
