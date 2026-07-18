package com.franckyl.yav1.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * A faithful crisp 7-segment display for the V1 bogey counter. The V1 sends a
 * raw segment byte (bits 0-6 = segments A..G, bit 7 = decimal dot) - the same
 * encoding YaV1Bogey decodes - so we render exactly what the detector lights
 * rather than approximating with a font or a raster glyph.
 *
 *      AAAA
 *     F    B
 *     F    B
 *      GGGG
 *     E    C
 *     E    C
 *      DDDD  .
 */
public class SegmentDisplayView extends View
{
    private static final int SEG_A = 0x01;
    private static final int SEG_B = 0x02;
    private static final int SEG_C = 0x04;
    private static final int SEG_D = 0x08;
    private static final int SEG_E = 0x10;
    private static final int SEG_F = 0x20;
    private static final int SEG_G = 0x40;
    private static final int SEG_DOT = 0x80;

    // Classic red LED. Off segments are drawn very faintly for an instrument feel.
    private int mOnColor  = 0xFFFF2A20;
    private int mOffColor = 0x1AFF2A20;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect  = new RectF();

    private int mBits = 0;

    public SegmentDisplayView(Context c)                        { super(c); }
    public SegmentDisplayView(Context c, AttributeSet a)        { super(c, a); }
    public SegmentDisplayView(Context c, AttributeSet a, int s) { super(c, a, s); }

    /** @param bogey raw V1 bogey byte (segments in bits 0-6, dot in bit 7). */
    public void setSegments(byte bogey)
    {
        int b = bogey & 0xFF;
        if(b == mBits) return;
        mBits = b;
        invalidate();
    }

    public void setOnColor(int color)  { mOnColor = color; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas)
    {
        final float w = getWidth()  - getPaddingLeft() - getPaddingRight();
        final float h = getHeight() - getPaddingTop()  - getPaddingBottom();
        if(w <= 0 || h <= 0) return;

        // Reserve room on the right for the decimal dot.
        final float dotSpace = w * 0.18f;
        final float dw = w - dotSpace;               // digit width
        final float left = getPaddingLeft();
        final float top  = getPaddingTop();

        final float t   = Math.min(dw, h) * 0.16f;   // segment thickness
        final float gap = t * 0.35f;                 // gap between segments
        final float hx0 = left + t * 0.5f + gap;     // horizontal seg x start
        final float hx1 = left + dw - t * 0.5f - gap;// horizontal seg x end
        final float midY = top + h / 2f;
        final float vTop0 = top + t * 0.5f + gap;
        final float vTop1 = midY - gap;
        final float vBot0 = midY + gap;
        final float vBot1 = top + h - t * 0.5f - gap;

        // horizontals: A (top), G (middle), D (bottom)
        drawH(canvas, hx0, hx1, top + t * 0.5f, t, (mBits & SEG_A) != 0);
        drawH(canvas, hx0, hx1, midY,           t, (mBits & SEG_G) != 0);
        drawH(canvas, hx0, hx1, top + h - t*0.5f, t, (mBits & SEG_D) != 0);
        // verticals: F (top-left), B (top-right), E (bottom-left), C (bottom-right)
        drawV(canvas, left + t * 0.5f,  vTop0, vTop1, t, (mBits & SEG_F) != 0);
        drawV(canvas, left + dw - t*0.5f, vTop0, vTop1, t, (mBits & SEG_B) != 0);
        drawV(canvas, left + t * 0.5f,  vBot0, vBot1, t, (mBits & SEG_E) != 0);
        drawV(canvas, left + dw - t*0.5f, vBot0, vBot1, t, (mBits & SEG_C) != 0);

        // decimal dot
        mPaint.setColor((mBits & SEG_DOT) != 0 ? mOnColor : mOffColor);
        float dotR = t * 0.6f;
        canvas.drawCircle(left + dw + dotSpace * 0.5f, top + h - dotR, dotR, mPaint);
    }

    private void drawH(Canvas c, float x0, float x1, float cy, float t, boolean on)
    {
        mPaint.setColor(on ? mOnColor : mOffColor);
        mRect.set(x0, cy - t / 2f, x1, cy + t / 2f);
        c.drawRoundRect(mRect, t / 2f, t / 2f, mPaint);
    }

    private void drawV(Canvas c, float cx, float y0, float y1, float t, boolean on)
    {
        mPaint.setColor(on ? mOnColor : mOffColor);
        mRect.set(cx - t / 2f, y0, cx + t / 2f, y1);
        c.drawRoundRect(mRect, t / 2f, t / 2f, mPaint);
    }
}
