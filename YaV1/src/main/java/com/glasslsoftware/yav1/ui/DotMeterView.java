package com.glasslsoftware.yav1.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * The V1 signal-strength meter: a left-aligned row of up to 8 red dots, one per
 * strength level. Crisp vector replacement for the ss_0..ss_8 raster row, keeping
 * the same dot style.
 */
public class DotMeterView extends View
{
    private static final int MAX_DOTS = 8;
    private static final int DOT_COLOR = 0xFFE01810;   // glossy red, matches ss_

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mCount = 0;

    public DotMeterView(Context c)                        { super(c); }
    public DotMeterView(Context c, AttributeSet a)        { super(c, a); }
    public DotMeterView(Context c, AttributeSet a, int s) { super(c, a, s); }

    /** @param count number of lit dots (0..8). */
    public void setCount(int count)
    {
        if(count < 0) count = 0;
        if(count > MAX_DOTS) count = MAX_DOTS;
        if(count == mCount) return;
        mCount = count;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        final float w = getWidth()  - getPaddingLeft() - getPaddingRight();
        final float h = getHeight() - getPaddingTop()  - getPaddingBottom();
        if(w <= 0 || h <= 0) return;

        final float cell = w / MAX_DOTS;
        final float r    = Math.min(cell, h) * 0.4f;
        final float cy   = getPaddingTop() + h / 2f;

        mPaint.setColor(DOT_COLOR);
        for(int i = 0; i < mCount; i++)
        {
            float cx = getPaddingLeft() + cell * (i + 0.5f);
            canvas.drawCircle(cx, cy, r, mPaint);
        }
    }
}
