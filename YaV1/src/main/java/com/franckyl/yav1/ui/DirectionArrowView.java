package com.franckyl.yav1.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * A single crisp directional threat arrow (front / side / rear), drawn in the
 * threat color. GLYPH encodes direction - one visual channel per meaning.
 *
 * Direction constants match YaV1Alert: FRONT=0, REAR=1, SIDE=2.
 */
public class DirectionArrowView extends View
{
    public static final int FRONT = 0;
    public static final int REAR  = 1;
    public static final int SIDE  = 2;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  mPath  = new Path();

    private int     mDir   = FRONT;
    private int     mColor = BandPalette.COLOR_LOCKED;
    private boolean mMuted = false;

    public DirectionArrowView(Context c)                        { super(c); }
    public DirectionArrowView(Context c, AttributeSet a)        { super(c, a); }
    public DirectionArrowView(Context c, AttributeSet a, int s) { super(c, a, s); }

    public void setDirection(int dir)
    {
        if(dir == mDir) return;
        mDir = dir;
        invalidate();
    }

    public void setColor(int color)
    {
        if(color == mColor) return;
        mColor = color;
        invalidate();
    }

    public void setMuted(boolean muted)
    {
        if(muted == mMuted) return;
        mMuted = muted;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        final float w = getWidth()  - getPaddingLeft() - getPaddingRight();
        final float h = getHeight() - getPaddingTop()  - getPaddingBottom();
        if(w <= 0 || h <= 0) return;

        final float cx = getPaddingLeft() + w / 2f;
        final float cy = getPaddingTop()  + h / 2f;
        final float r  = Math.min(w, h) / 2f * 0.9f;

        mPaint.setColor(mMuted ? BandPalette.COLOR_LOCKED : mColor);
        mPaint.setStyle(Paint.Style.FILL);
        mPath.reset();

        switch(mDir)
        {
            case REAR: // triangle pointing down
                mPath.moveTo(cx, cy + r);
                mPath.lineTo(cx - r, cy - r * 0.7f);
                mPath.lineTo(cx + r, cy - r * 0.7f);
                mPath.close();
                break;
            case SIDE: // double-headed horizontal (left + right)
                mPath.moveTo(cx - r, cy);
                mPath.lineTo(cx - r * 0.15f, cy - r * 0.7f);
                mPath.lineTo(cx - r * 0.15f, cy + r * 0.7f);
                mPath.close();
                mPath.moveTo(cx + r, cy);
                mPath.lineTo(cx + r * 0.15f, cy - r * 0.7f);
                mPath.lineTo(cx + r * 0.15f, cy + r * 0.7f);
                mPath.close();
                break;
            case FRONT: // triangle pointing up
            default:
                mPath.moveTo(cx, cy - r);
                mPath.lineTo(cx - r, cy + r * 0.7f);
                mPath.lineTo(cx + r, cy + r * 0.7f);
                mPath.close();
                break;
        }
        canvas.drawPath(mPath, mPaint);
    }
}
