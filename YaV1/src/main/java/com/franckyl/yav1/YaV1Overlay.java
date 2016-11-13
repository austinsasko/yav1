package com.franckyl.yav1;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;

import com.franckyl.yav1.events.AlertEvent;
import com.franckyl.yav1lib.YaV1AlertList;
import com.squareup.otto.Subscribe;
import com.valentine.esp.data.BandAndArrowIndicatorData;

/**
 * Created by franck on 11/18/13.
 */
public class YaV1Overlay
{
    private WindowManager mWm                   = null;
    private WindowManager.LayoutParams mParams  = null;
    private View mOverlay                       = null;

    private RelativeLayout  mParent;
    private RelativeLayout  myArrowButton;
    private RelativeLayout  myFreqButton;
    private Rect            outArrowRectHit     = new Rect();
    private Rect            outFreqRectHit      = new Rect();
    private Rect            parentRectHit       = new Rect();

    private ImageView       mDotView            = null;
    private int             sOverlaySize[]      = {R.layout.yav1_overlay, R.layout.yav1_overlay_2};

    public  int             mOverlayPosition    = 0;
    public  boolean         mOverlayClick       = true;
    public  boolean         mOverlayMute        = false;
    public  boolean         mOverlayLongClick   = false;

    public  boolean         mOverlayPersist     = false;
    public  int             mOverlaySize        = 1;

    // AlertList

    private Activity        mMainActivity      = null;

    // position

    public  static         int overlayPosV[]    = {Gravity.TOP, Gravity.CENTER_VERTICAL, Gravity.BOTTOM};
    public  static         int overlayPosH[]    = {Gravity.LEFT, Gravity.CENTER_HORIZONTAL, Gravity.RIGHT};
    public  static         int mListWidth       = 0;

    // arrow images
    private ImageView mImgArrow[]               = {null, null, null};

    private BandAndArrowIndicatorData mBandArrowIndicator = new BandAndArrowIndicatorData();

    // Bg colors

    private static int mBgColor[]       = {0x00000000, 0x8800ff00, 0x88ff0000};

    // our list View

    private YaV1V1ViewAdapter  mAlertAdapter;
    private ListView           mListAlert;
    private YaV1AlertList      mAlertList;

    // long press duration
    private long               mTouchDown;
    private boolean            mArrowClick = false;

    // event bus registered ?
    private boolean            mRegistered = false;

    // constructor

    public YaV1Overlay(Activity act)
    {
        mOverlayPosition =  overlayPosH[Integer.valueOf(YaV1.sPrefs.getString("overlay_horizontal", "1"))] |
                            overlayPosV[Integer.valueOf(YaV1.sPrefs.getString("overlay_vertical", "1"))];

        mOverlaySize      = Integer.valueOf(YaV1.sPrefs.getString("overlay_size", "1"));
        mOverlayClick     = YaV1.sPrefs.getBoolean("overlay_click", false);
        mOverlayMute      = YaV1.sPrefs.getBoolean("overlay_mute", false);
        mOverlayPersist   = YaV1.sPrefs.getBoolean("overlay_sticky", false);
        mOverlayLongClick = YaV1.sPrefs.getBoolean("overlay_long_click", false);

        // security

        if(!mOverlayPersist)
            mOverlayMute = false;

        mAlertList       = new YaV1AlertList();
        // create the adapter
        mAlertAdapter    = new YaV1V1ViewAdapter(act, mOverlaySize+1, mAlertList);
        mMainActivity    = act;
        init();
    }

    // check if we need to recreate the overlay

    public boolean recreate()
    {
        // convert the position

        int nPos = overlayPosH[Integer.valueOf(YaV1.sPrefs.getString("overlay_horizontal", "1"))] |
                   overlayPosV[Integer.valueOf(YaV1.sPrefs.getString("overlay_vertical", "1"))];

        int     oVerSize      = Integer.valueOf(YaV1.sPrefs.getString("overlay_size", "1"));
        boolean oVerClick     = YaV1.sPrefs.getBoolean("overlay_click", false);
        boolean oVerMute      = YaV1.sPrefs.getBoolean("overlay_mute", false);
        boolean oVerPersist   = YaV1.sPrefs.getBoolean("overlay_sticky", false);
        boolean oVerLongClick = YaV1.sPrefs.getBoolean("overlay_long_click", false);

        // depends if we have already an overlay or not

        if(nPos != mOverlayPosition || oVerSize != mOverlaySize || oVerClick != mOverlayClick ||
           oVerMute != mOverlayMute || oVerPersist != mOverlayPersist || oVerLongClick != mOverlayLongClick)
        {
            // get a chance to be Garbage collected
            mOverlayPosition  = nPos;
            mOverlaySize      = oVerSize;
            mOverlayClick     = oVerClick;
            mOverlayMute      = oVerMute;
            mOverlayPersist   = oVerPersist;
            mOverlayLongClick = oVerLongClick;

            //security

            if(!mOverlayPersist)
                mOverlayMute = false;
            return true;
        }

        return false;
    }

    // initialize

    private void init()
    {
        mParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        mParams.gravity = mOverlayPosition;

        LayoutInflater inflater = (LayoutInflater) YaV1.sContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mOverlay = inflater.inflate(sOverlaySize[mOverlaySize], null);
        mAlertAdapter.setOverlay(mOverlaySize+1);

        mParent = (RelativeLayout) mOverlay.findViewById(R.id.container);

        myArrowButton = (RelativeLayout) mOverlay.findViewById(R.id.arrow_container);
        myFreqButton  = (RelativeLayout) mOverlay.findViewById(R.id.alert_container);

        myFreqButton.setBackgroundColor(mBgColor[0]);

        mDotView      = (ImageView) mOverlay.findViewById(R.id.dots);

        //myButton.getHitRect(outRectHit);

        mWm = (WindowManager) YaV1.sContext.getSystemService(Context.WINDOW_SERVICE);
        mWm.addView(mOverlay, mParams);

        hideView();

        //mOverlayVisible.set(false);

        // get the list view
        mListAlert = (ListView) mOverlay.findViewById(R.id.alertList);
        mListAlert.setAdapter(mAlertAdapter);

        // check for the width
        // if(mListWidth == 0)
        mListWidth = mAlertAdapter.getWidestRowSize();

        mListAlert.getLayoutParams().width =  (int) (mListWidth*1.01);

        mImgArrow[0]        = (ImageView) mOverlay.findViewById(R.id.arrow_front);
        mImgArrow[1]        = (ImageView) mOverlay.findViewById(R.id.arrow_side);
        mImgArrow[2]        = (ImageView) mOverlay.findViewById(R.id.arrow_rear);

        // color for background

        if(mOverlayClick || mOverlayMute || mOverlayLongClick)
        {
            // handler for touching image
            mOverlay.setOnTouchListener(new View.OnTouchListener()
            {
                @Override
                public boolean onTouch(View v, MotionEvent event)
                {
                    float x = event.getX();
                    float y = event.getY();

                    // we want the hit rect of parent

                    mParent.getHitRect(parentRectHit);
                    myArrowButton.getHitRect(outArrowRectHit);
                    myFreqButton.getHitRect(outFreqRectHit);


                    if(event.getAction() == MotionEvent.ACTION_DOWN)
                    {
                        mArrowClick = false;

                        if(arrowClicked(x, y))
                        {
                            mTouchDown = SystemClock.elapsedRealtime();
                            mArrowClick = true;
                        }

                        return false;

                    }

                    if(event.getAction() != MotionEvent.ACTION_UP)
                        return false;

                    if(arrowClicked(x, y) && mArrowClick)
                    {
                        mArrowClick = false;
                        long duration = SystemClock.elapsedRealtime() - mTouchDown;

                        if(duration > 1000)
                        {
                            if(mOverlayLongClick)
                            {
                                if(YaV1AlertService.requestOverride())
                                {
                                    // Activity to run the dialog on
                                    YaV1.sLockoutOverride.setActivity(mMainActivity);
                                }

                                return true;
                            }
                        }
                        else
                        {
                            if(mOverlayClick)
                            {
                                Intent intent = new Intent(YaV1.sContext, YaV1Activity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.putExtra("restart", true);
                                YaV1.sContext.startActivity(intent);
                                return true;
                            }
                        }

                        return false;
                    }

                    // touch the frequency ?

                    if(mOverlayMute && x > outFreqRectHit.left + parentRectHit.left && x < outFreqRectHit.right + parentRectHit.left &&
                       y > outFreqRectHit.top + parentRectHit.top && y < outFreqRectHit.bottom + parentRectHit.top)
                    {
                        YaV1AlertService.toggleMute();
                        adjustBgColor();
                        return true;
                    }

                    return false;
                }
            });
        }
        else
            mOverlay.setOnTouchListener(null);

        //LocalBroadcastManager.getInstance(YaV1.sContext).registerReceiver(mAlertReceiver, new IntentFilter("V1Overlay"));
    }

    // check if the arrow has been clicked

    private boolean arrowClicked(float x, float y)
    {
        if(x > outArrowRectHit.left + parentRectHit.left && x < outArrowRectHit.right + parentRectHit.left &&
                y > outArrowRectHit.top + parentRectHit.top && y < outArrowRectHit.bottom + parentRectHit.top)
            return true;
        return false;
    }

    // visible ?

    public boolean isVisible()
    {
        return mOverlay != null && mOverlay.getVisibility() == View.VISIBLE;
    }

    // remove the overlay

    public void removeOverlay()
    {
        // stop listening for alert
        registerAlert(false);

        // remove the receiver
        // LocalBroadcastManager.getInstance(YaV1.sContext).unregisterReceiver(mAlertReceiver);

        if(mOverlay != null)
            mWm.removeView(mOverlay);

        mOverlay = null;
    }

    // hide the view, this is called by the application when not in BackGround any more

    public void hideView()
    {
        // mOverlayVisible.set(false);
        if(mOverlay != null)
            mOverlay.setVisibility(View.GONE);
        YaV1.sOverlayVisible = false;
        Log.d("Valentine Overlay", "Overlay hide view");
    }

    private void showView()
    {
        if(mOverlay != null)
        {
            mOverlay.setVisibility(View.VISIBLE);
            YaV1.sOverlayVisible = true;
        }
        Log.d("Valentine Overlay", "Overlay show view");
    }

    public void hideFromExternal()
    {
        mMainActivity.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if(mOverlay != null)
                    mOverlay.setVisibility(View.GONE);
                YaV1.sOverlayVisible = false;
            }
        });
    }

    // register the receiver for alerts

    public void registerAlert(boolean onOff)
    {
        if(onOff)
        {
            if(!mRegistered) {
                YaV1.getEventBus().register(this);
                Log.d("Valentine Overlay", "Event bus registered");
                mRegistered = true;
            }
        }
        else
        {
            if(mRegistered)
            {
                YaV1.getEventBus().unregister(this);
                Log.d("Valentine Overlay", "Event bus unregistered");
                mRegistered = false;
            }
        }
    }

    @Subscribe
    public void onNewAlert(AlertEvent evt)
    {
        if(evt.getType() == AlertEvent.Type.V1_ALERT_OVERLAY)
        {
            Log.d("Valentine Overlay", "Alert received");

            mMainActivity.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    if(mOverlay == null || !YaV1.isInBackground())
                    {
                        registerAlert(false);
                        hideView();
                        return;
                    }

                    int v = mOverlay.getVisibility();
                    // get the alert list
                    YaV1.getNewAlert(mAlertList);

                    //
                    if(!mOverlayPersist && mAlertList.size() < 1)
                    {
                        if(v == View.VISIBLE)
                            hideView();
                        return;
                    }

                    if(v != View.VISIBLE)
                        showView();

                    mDotView.setImageResource(YaV1ScreenV1Fragment.mImageS[YaV1CurrentView.sSignal]);

                    adjustBgColor();

                    int i = 0;
                    int c;
                    int j = Math.min(mAlertList.size(), 3);

                    mBandArrowIndicator.setFromByte(YaV1CurrentView.sArrow0);

                    // make secure
                    int bandCount = (mBandArrowIndicator.getFront() ? 1 : 0 ) +
                            (mBandArrowIndicator.getSide() ? 2 : 0) +
                            (mBandArrowIndicator.getRear() ? 4 : 0);

                    // adjust the arrow
                    if(j > 0 && bandCount < 1)
                        bandCount = 5;
                    else if(j < 1)
                        bandCount = 0;

                    mImgArrow[0].setVisibility( (bandCount & 1) > 0 ?  View.VISIBLE : View.INVISIBLE);
                    mImgArrow[1].setVisibility( (bandCount & 2) > 0 ? View.VISIBLE : View.INVISIBLE);
                    mImgArrow[2].setVisibility( (bandCount & 4) > 0 ? View.VISIBLE : View.INVISIBLE);

                    mAlertAdapter.notifyDataSetChanged();
                }
            });
        }
    }
            // receiver for broadcast overlay

    private BroadcastReceiver mAlertReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            // alerts
            String action = intent.getAction();

            // this could happen when rotating
            if(!action.equals("V1Overlay"))
                return;

            final YaV1AlertList lal = intent.getParcelableExtra("V1Alert");
            mMainActivity.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    updateAlert(lal);
                }
            });
        }
    };

    private void updateAlert(final YaV1AlertList lal)
    {
        if(mOverlay == null)
            return;

        int v = mOverlay.getVisibility();

        if(!YaV1.isInBackground() || (!mOverlayPersist && (lal == null || lal.size() < 1)))
        {
            if(v == View.VISIBLE)
                hideView();
            return;
        }

        if(v != View.VISIBLE)
        {
            showView();
        }

        mDotView.setImageResource(YaV1ScreenV1Fragment.mImageS[YaV1CurrentView.sSignal]);

        adjustBgColor();

        int i = 0;
        int c;
        int j = (lal != null ? Math.min(lal.size(), 3) : 0);

        mBandArrowIndicator.setFromByte(YaV1CurrentView.sArrow0);

        // make secure
        int bandCount = (mBandArrowIndicator.getFront() ? 1 : 0 ) +
                (mBandArrowIndicator.getSide() ? 2 : 0) +
                (mBandArrowIndicator.getRear() ? 4 : 0);

        // adjust the arrow
        if(j > 0 && bandCount < 1)
            bandCount = 5;
        else if(j < 1)
            bandCount = 0;

        mImgArrow[0].setVisibility( (bandCount & 1) > 0 ?  View.VISIBLE : View.INVISIBLE);
        mImgArrow[1].setVisibility( (bandCount & 2) > 0 ? View.VISIBLE : View.INVISIBLE);
        mImgArrow[2].setVisibility( (bandCount & 4) > 0 ? View.VISIBLE : View.INVISIBLE);

        // we would refresh the list view
        if(lal != null)
        {
            mAlertList.clear();
            mAlertList.addAll(lal);
            mAlertAdapter.notifyDataSetChanged();
        }
    }

    private void adjustBgColor()
    {
        if(!YaV1AlertService.getMute())
            myFreqButton.setBackgroundColor(mBgColor[0]);
        else if(YaV1AlertService.isMuteByUser())
            myFreqButton.setBackgroundColor(mBgColor[2]);
        else
            myFreqButton.setBackgroundColor(mBgColor[1]);
    }
}
