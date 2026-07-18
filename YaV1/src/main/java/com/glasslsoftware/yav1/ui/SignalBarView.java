package com.franckyl.yav1.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * A crisp N-of-bars signal meter. Lit bars are drawn in the band color, unlit
 * bars in a dim track. Bar length encodes strength, color encodes band - one
 * visual channel per meaning.
 *
 * Vector, resolution independent: replaces the raster fr_/sr_/ss_ glyph PNGs on
 * the redesigned surfaces.
 */
public class SignalBarView extends View
{
    private static final int   DEFAULT_BARS = 6;
    private static final int   MAX_STRENGTH = 8;
    private static final int   TRACK_COLOR  = 0x33FFFFFF;

    private final Paint  mPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF  mRect   = new RectF();

    private int   mBarCount   = DEFAULT_BARS;
    private int   mStrength   = 0;
    private int   mBandColor  = BandPalette.COLOR_LOCKED;
    private boolean mMuted    = false;

    public SignalBarView(Context c)                        { super(c); }
    public SignalBarView(Context c, AttributeSet a)        { super(c, a); }
    public SignalBarView(Context c, AttributeSet a, int s) { super(c, a, s); }

    /** @param strength raw 0..8 V1 strength. */
    public void setStrength(int strength)
    {
        if(strength == mStrength) return;
        mStrength = strength;
        invalidate();
    }

    public void setBandColor(int color)
    {
        if(color == mBandColor) return;
        mBandColor = color;
        invalidate();
    }

    /** Muted/locked alerts grey out regardless of band. */
    public void setMuted(boolean muted)
    {
        if(muted == mMuted) return;
        mMuted = muted;
        invalidate();
    }

    public void setBarCount(int n)
    {
        if(n <= 0 || n == mBarCount) return;
        mBarCount = n;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        final int w = getWidth()  - getPaddingLeft() - getPaddingRight();
        final int h = getHeight() - getPaddingTop()  - getPaddingBottom();
        if(w <= 0 || h <= 0) return;

        final float gap    = Math.max(2f, w * 0.02f);
        final float barW   = (w - gap * (mBarCount - 1)) / mBarCount;
        final int   lit    = SignalMath.barsFor(mStrength, MAX_STRENGTH, mBarCount);
        final int   litClr = mMuted ? BandPalette.COLOR_LOCKED : mBandColor;
        final float radius = Math.min(barW, h) * 0.25f;

        float x = getPaddingLeft();
        final float top = getPaddingTop();
        for(int i = 0; i < mBarCount; i++)
        {
            // Taller bars towards the right -> reads as a rising meter.
            float scale = 0.45f + 0.55f * ((i + 1f) / mBarCount);
            float barH  = h * scale;
            mRect.set(x, top + (h - barH), x + barW, top + h);
            mPaint.setColor(i < lit ? litClr : TRACK_COLOR);
            canvas.drawRoundRect(mRect, radius, radius, mPaint);
            x += barW + gap;
        }
    }
}
