package com.glasslsoftware.yav1.car;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;

import com.glasslsoftware.yav1lib.YaV1Alert;

import java.util.Locale;

/**
 * Draws the radar display on the NavigationTemplate's background surface.
 *
 * Surface drawing is NOT host-rate-limited (nav apps render maps at 60fps
 * this way), so redrawing on every repository update (~1-2 Hz alert cadence)
 * is trivial. All state is copied before drawing: lock canvas, draw, post.
 * Host callbacks arrive on binder threads and snapshots on the main thread,
 * so every surface access is serialized on mLock, and drawing errors during
 * a car disconnect are swallowed instead of crashing the process.
 */
public final class V1SurfaceRenderer implements SurfaceCallback
{
    private static final String LOG_TAG = "V1CarRender";

    // band colors: LASER, Ka, K, X, Ku (indexes match YaV1Alert.BAND_*)
    private static final int[] BAND_COLOR = {
            0xFFFF4444,     // LASER - red
            0xFFFF8800,     // Ka    - orange
            0xFF33B5E5,     // K     - blue
            0xFF99CC00,     // X     - green
            0xFFFFBB33      // Ku    - amber
    };

    private final Object mLock = new Object();

    private SurfaceContainer mSurface     = null;
    private Rect             mVisibleArea = null;

    private volatile V1AlertRepository.Snapshot mSnapshot = null;

    private final Paint mTextPaint;
    private final Paint mFillPaint;
    private final Paint mBarPaint;

    public V1SurfaceRenderer(@NonNull CarContext carContext)
    {
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        mTextPaint.setColor(Color.WHITE);

        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setStyle(Paint.Style.FILL);

        mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBarPaint.setStyle(Paint.Style.FILL);
    }

    /** Latest state from the repository (main thread); triggers a redraw. */
    public void setSnapshot(V1AlertRepository.Snapshot s)
    {
        mSnapshot = s;
        draw();
    }

    // ------------------------------------------------------------------
    // SurfaceCallback (host binder threads)
    // ------------------------------------------------------------------

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer surfaceContainer)
    {
        Log.d(LOG_TAG, "surface available " + surfaceContainer.getWidth() + "x" + surfaceContainer.getHeight());
        synchronized(mLock)
        {
            mSurface = surfaceContainer;
        }
        draw();
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer surfaceContainer)
    {
        Log.d(LOG_TAG, "surface destroyed");
        synchronized(mLock)
        {
            mSurface = null;
        }
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect visibleArea)
    {
        synchronized(mLock)
        {
            mVisibleArea = new Rect(visibleArea);
        }
        draw();
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect stableArea)
    {
        synchronized(mLock)
        {
            if(mVisibleArea == null)
                mVisibleArea = new Rect(stableArea);
        }
        draw();
    }

    // ------------------------------------------------------------------

    private void draw()
    {
        synchronized(mLock)
        {
            if(mSurface == null || mSurface.getSurface() == null || !mSurface.getSurface().isValid())
                return;

            Canvas canvas = null;
            try
            {
                canvas = mSurface.getSurface().lockCanvas(null);
                render(canvas, mSnapshot);
            }
            catch(Exception e)
            {
                // car disconnected mid-draw or surface torn down by the host -
                // never crash the (shared) app process for a lost frame
                Log.d(LOG_TAG, "draw failed: " + e);
            }
            finally
            {
                if(canvas != null)
                {
                    try
                    {
                        mSurface.getSurface().unlockCanvasAndPost(canvas);
                    }
                    catch(Exception e)
                    {
                        Log.d(LOG_TAG, "unlock failed: " + e);
                    }
                }
            }
        }
    }

    private void render(Canvas c, V1AlertRepository.Snapshot s)
    {
        c.drawColor(0xFF101010);

        Rect area = (mVisibleArea != null && !mVisibleArea.isEmpty())
                ? mVisibleArea
                : new Rect(0, 0, c.getWidth(), c.getHeight());

        if(s == null || s.state == V1AlertRepository.ConnState.DISCONNECTED)
        {
            drawStatus(c, area, "YaV1", "Open YaV1 on your phone to connect", 0xFF888888);
            return;
        }

        switch(s.state)
        {
            case CONNECTING:
                drawStatus(c, area, "YaV1", "Connecting to V1...", 0xFFFFBB33);
                break;

            case CONNECTED_IDLE:
                String sub = "No alerts" + (s.v1Version != null && !s.v1Version.isEmpty() ? "  -  V1 " + s.v1Version : "");
                drawStatus(c, area, "V1 connected", sub, 0xFF99CC00);
                if(s.muted)
                    drawMutedTag(c, area);
                break;

            case ALERTING:
                drawAlerts(c, area, s);
                break;

            default:
                break;
        }
    }

    private void drawStatus(Canvas c, Rect area, String title, String subtitle, int accent)
    {
        float cx = area.exactCenterX();
        float cy = area.exactCenterY();
        float titleSize = Math.min(area.height() * 0.18f, area.width() * 0.10f);

        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(titleSize);
        mTextPaint.setColor(accent);
        c.drawText(title, cx, cy - titleSize * 0.3f, mTextPaint);

        mTextPaint.setTextSize(titleSize * 0.45f);
        mTextPaint.setColor(0xFFCCCCCC);
        c.drawText(subtitle, cx, cy + titleSize * 0.7f, mTextPaint);
    }

    private void drawAlerts(Canvas c, Rect area, V1AlertRepository.Snapshot s)
    {
        YaV1Alert prio = s.getPriorityAlert();

        if(prio == null)
        {
            drawStatus(c, area, "V1 connected", "No alerts", 0xFF99CC00);
            return;
        }

        int   pad    = Math.max(8, area.width() / 60);
        float x      = area.left + pad * 2;
        int   band   = clampBand(prio.getBand());
        int   color  = BAND_COLOR[band];

        // --- header row: bogey count (left) + muted tag (right) ---------
        float headSize = area.height() * 0.09f;
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setTextSize(headSize);
        mTextPaint.setColor(0xFFCCCCCC);
        c.drawText("Bogeys " + s.bogeyCount, x, area.top + pad + headSize, mTextPaint);

        if(s.muted)
            drawMutedTag(c, area);

        // --- priority alert: band, frequency, arrow, signal bars ---------
        float bigSize  = Math.min(area.height() * 0.30f, area.width() * 0.14f);
        float bigBase  = area.top + area.height() * 0.42f;

        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setTextSize(bigSize);
        mTextPaint.setColor(color);
        String bandStr = YaV1Alert.getBandStr(band);
        c.drawText(bandStr, x, bigBase, mTextPaint);
        float bandWidth = mTextPaint.measureText(bandStr);

        String freq = formatFrequency(prio);
        if(!freq.isEmpty())
        {
            mTextPaint.setTextSize(bigSize * 0.9f);
            mTextPaint.setColor(Color.WHITE);
            c.drawText(freq, x + bandWidth + pad * 2, bigBase, mTextPaint);
        }

        // arrow on the right side, vertically centered on the big row
        float arrowSize = bigSize * 0.9f;
        float arrowCx   = area.right - pad * 2 - arrowSize / 2f;
        float arrowCy   = bigBase - bigSize * 0.35f;
        drawArrow(c, prio.getArrowDir(), arrowCx, arrowCy, arrowSize, color);

        // signal bars 0-8 under the big row
        float barTop    = bigBase + area.height() * 0.06f;
        float barHeight = area.height() * 0.13f;
        drawSignalBars(c, prio.getSignal(), x, barTop, area.width() - pad * 4 - arrowSize, barHeight, color);

        // --- secondary alerts stacked small ------------------------------
        float lineSize = area.height() * 0.085f;
        float y        = barTop + barHeight + lineSize * 1.6f;
        int   shown    = 0;

        for(int i = 0; i < s.alerts.size() && shown < 3; i++)
        {
            YaV1Alert a = s.alerts.get(i);
            if(a == prio)
                continue;

            int    b   = clampBand(a.getBand());
            String row = String.format(Locale.US, "%-5s %s  %s  %d/8",
                    YaV1Alert.getBandStr(b), formatFrequency(a), arrowChar(a.getArrowDir()), a.getSignal());

            mTextPaint.setTextAlign(Paint.Align.LEFT);
            mTextPaint.setTextSize(lineSize);
            mTextPaint.setColor(BAND_COLOR[b]);
            c.drawText(row, x, y, mTextPaint);

            y += lineSize * 1.4f;
            shown++;
        }

        int extra = s.alerts.size() - 1 - shown;
        if(extra > 0)
        {
            mTextPaint.setTextSize(lineSize);
            mTextPaint.setColor(0xFF888888);
            c.drawText("+" + extra + " more", x, y, mTextPaint);
        }
    }

    private void drawSignalBars(Canvas c, int signal, float x, float y, float width, float height, int color)
    {
        int   bars = 8;
        float gap  = width / (bars * 8f);
        float bw   = (width - gap * (bars - 1)) / bars;

        for(int i = 0; i < bars; i++)
        {
            // bars grow taller left -> right, like a classic signal meter
            float bh = height * (0.35f + 0.65f * (i + 1) / bars);
            float bx = x + i * (bw + gap);

            mBarPaint.setColor(i < signal ? color : 0xFF333333);
            c.drawRect(bx, y + height - bh, bx + bw, y + height, mBarPaint);
        }
    }

    private void drawArrow(Canvas c, int dir, float cx, float cy, float size, int color)
    {
        float h = size / 2f;
        Path p  = new Path();

        mFillPaint.setColor(color);

        if(dir == YaV1Alert.ALERT_FRONT)
        {
            p.moveTo(cx, cy - h);
            p.lineTo(cx + h, cy + h);
            p.lineTo(cx - h, cy + h);
        }
        else if(dir == YaV1Alert.ALERT_REAR)
        {
            p.moveTo(cx, cy + h);
            p.lineTo(cx + h, cy - h);
            p.lineTo(cx - h, cy - h);
        }
        else // SIDE: opposing left/right arrows
        {
            p.moveTo(cx - h, cy);
            p.lineTo(cx - h * 0.1f, cy - h * 0.6f);
            p.lineTo(cx - h * 0.1f, cy + h * 0.6f);
            p.close();
            p.moveTo(cx + h, cy);
            p.lineTo(cx + h * 0.1f, cy - h * 0.6f);
            p.lineTo(cx + h * 0.1f, cy + h * 0.6f);
        }

        p.close();
        c.drawPath(p, mFillPaint);
    }

    private void drawMutedTag(Canvas c, Rect area)
    {
        float size = area.height() * 0.09f;
        mTextPaint.setTextAlign(Paint.Align.RIGHT);
        mTextPaint.setTextSize(size);
        mTextPaint.setColor(0xFFFF4444);
        c.drawText("MUTED", area.right - size, area.top + size * 1.8f, mTextPaint);
    }

    /** Frequency in GHz with 3 decimals (matches the phone UI), empty for laser. */
    static String formatFrequency(YaV1Alert a)
    {
        if(a.getBand() == YaV1Alert.BAND_LASER || a.getFrequency() <= 0)
            return "";
        return String.format(Locale.US, "%.3f", a.getFrequency() / 1000.0);
    }

    static String arrowChar(int dir)
    {
        if(dir == YaV1Alert.ALERT_FRONT)
            return "▲";        // up triangle
        if(dir == YaV1Alert.ALERT_REAR)
            return "▼";        // down triangle
        return "◀▶";      // side
    }

    static int clampBand(int band)
    {
        return (band < 0 || band >= BAND_COLOR.length) ? 0 : band;
    }
}
