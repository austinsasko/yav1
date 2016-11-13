package com.franckyl.yav1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;

import com.franckyl.yav1.events.AlertEvent;
import com.franckyl.yav1.events.InfoEvent;
import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1lib.YaV1AlertList;
import com.squareup.otto.Subscribe;
import com.valentine.esp.data.BandAndArrowIndicatorData;

import java.util.concurrent.atomic.AtomicBoolean;

public class YaV1ScreenV1Fragment extends YaV1ScreenBaseAlertFragment
{
    private ImageView          mLaser;
    private ImageView          mKa;
    private ImageView          mK;
    private ImageView          mX;
    private ImageView          mDot;
    private ImageView          mBogey;
    private ImageView          mSignal;
    private ImageView          mFront;
    private ImageView          mSide;
    private ImageView          mRear;

    private YaV1V1ViewAdapter  mAlertAdapter;
    private AtomicBoolean      mStop = new AtomicBoolean(false);
    private long               mDelay = 100;

    // the info needed to display the bands

    private BandAndArrowIndicatorData mBandArrowIndicator[] = {new BandAndArrowIndicatorData(), new BandAndArrowIndicatorData()};
    private YaV1Bogey                 mBogeySrc[]           =  {new YaV1Bogey(), new YaV1Bogey()};
    //private BogeyCounterData          mBogey2 = new BogeyCounterData();
    private int     mStrength  = 0;
    private int[]   mSignalDir = {0,0,0};

    public  static int mImageS[]   = {R.drawable.ss_0, R.drawable.ss_1, R.drawable.ss_2,R.drawable.ss_3,
                                      R.drawable.ss_4, R.drawable.ss_5, R.drawable.ss_6, R.drawable.ss_7,
                                      R.drawable.ss_8};

    private static int mFrontImg[] = {R.drawable.front_0, R.drawable.front_1, R.drawable.front_1, R.drawable.front_2, R.drawable.front_2,
                                      R.drawable.front_3, R.drawable.front_3, R.drawable.front_4, R.drawable.front_4};
    private static int mRearImg[]  = {R.drawable.rear_0, R.drawable.rear_1, R.drawable.rear_1, R.drawable.rear_2, R.drawable.rear_2,
                                      R.drawable.rear_3, R.drawable.rear_3, R.drawable.rear_4, R.drawable.rear_4};
    private static int mSideImg[]  = {R.drawable.side_0, R.drawable.side_1, R.drawable.side_1, R.drawable.side_2, R.drawable.side_2,
                                      R.drawable.side_3, R.drawable.side_3, R.drawable.side_4, R.drawable.side_4};

    //handler to swap between blinking state

    private final Handler mHandler      = new Handler();
    private boolean       mIsRegistered = false;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mIntentName = "V1Frequ";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        //mFragmentView = inflater.inflate( (YaV1CurrentPosition.enabled ? R.layout.yav1_screen_v1_fragment_gps : R.layout.yav1_screen_v1_fragment), container, false);
        mFragmentView = inflater.inflate(R.layout.yav1_screen_v1_fragment, container, false);

        // get the ListView and set the Adapter

        mListAlert    = (ListView) mFragmentView.findViewById(R.id.alertList);
        mAlertAdapter  = new YaV1V1ViewAdapter(getActivity(), 0, mAlertList);
        mListAlert.setAdapter(mAlertAdapter);

        setGpsParts(false);

        return mFragmentView;
    }

    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        //setUserVisibleHint(true);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser)
    {
        super.setUserVisibleHint(isVisibleToUser);
        if(isVisibleToUser)
        {
            if(!mIsRegistered)
            {
                YaV1.getEventBus().register(this);
                mIsRegistered = true;
            }
        }
        else
        {
            if(mIsRegistered)
            {
                YaV1.getEventBus().unregister(this);
                mIsRegistered = false;
                clearRemaining();
            }
        }
    }

    @Override
    public void onResume()
    {
        //YaV1.superResume();
        super.onResume();
        //LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mAlertReceiver, new IntentFilter(mIntentName));
        setViewObject();
        mStop.set(false);
        if(!mIsRegistered)
        {
            YaV1.getEventBus().register(this);
            mIsRegistered = true;
        }
        //Log.d("Valentine", "V1 view is resumed, visible will run");
        run();
    }

    @Override
    public void onPause()
    {
        // YaV1.superPause();
        //LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mAlertReceiver);
        mStop.set(true);
        //Log.d("Valentine", "V1 view is paused, not visible");
        super.onPause();
        if(mIsRegistered)
        {
            YaV1.getEventBus().unregister(this);
            mIsRegistered = false;
        }
    }

    @Subscribe
    public void onNewAlert(AlertEvent evt)
    {
        if(evt.getType() == AlertEvent.Type.V1_ALERT)
        {
            getActivity().runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    YaV1.getNewAlert(mAlertList);
                    mAlertAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    private void clearRemaining()
    {

        getActivity().runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mAlertList.clear();
                mAlertAdapter.notifyDataSetChanged();
            }
        });
    }

    // run this view
    public void run()
    {
        mHandler.postDelayed(runMain,1 );
    }
    // run the application refresh for 1

    public final Runnable runMain = new Runnable()
    {
        public void run()
        {
            // we set the objects
            mBogeySrc[0].setBogey(YaV1CurrentView.sBogey0);
            mBogeySrc[1].setBogey(YaV1CurrentView.sBogey1);
            mBandArrowIndicator[0].setFromByte(YaV1CurrentView.sArrow0);
            mBandArrowIndicator[1].setFromByte(YaV1CurrentView.sArrow1);
            mStrength     = YaV1CurrentView.sSignal;
            for(int i = 0; i <= YaV1Alert.ALERT_SIDE; i++)
                mSignalDir[i] = YaV1CurrentView.sDirectionStrength[i];
            //mSignalDir[0] = YaV1CurrentView.sSignalDir[0];
            //mSignalDir[1] = YaV1CurrentView.sSignalDir[1];
            //mSignalDir[2] = YaV1CurrentView.sSignalDir[2];

            // update the view and set the runSecond
            mBogey.setImageResource(mBogeySrc[0].getImageNotDot());

            // dot
            mDot.setImageResource(mBogeySrc[0].getDot());
            // strength
            mSignal.setImageResource(mImageS[mStrength]);

            adjustBand(0);

            adjustArrows(0);

            // update Gps only in main update
            //if(YaV1CurrentPosition.enabled)
            //    updateGps();

            if(!mStop.get())
            {
                // run second on handler
                mHandler.postDelayed(runBlink, mDelay);
            }
        }
    };

    public final Runnable runBlink = new Runnable()
    {
        @Override
        public void run()
        {
            // update the view and set the runSecond
            // update bogey
            mBogey.setImageResource(mBogeySrc[1].getImageNotDot());
            // dots
            mDot.setImageResource(mBogeySrc[1].getDot());
            // update bands
            adjustBand(1);
            // update arrows
            adjustArrows(1);
            if(!mStop.get())
            {
                // run second on handler
                mHandler.postDelayed(runMain, mDelay);
            }
        }
    };

    // receiver for alerts

    private BroadcastReceiver mAlertReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if(getActivity() == null || !action.equals("V1Frequ"))
                return;

            //mAlertList = intent.getParcelableExtra("V1Alert");
            //getActivity().runOnUiThread(mRefreshAlert);
            final YaV1AlertList lal = intent.getParcelableExtra("V1Alert");
            getActivity().runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    if(lal != null)
                    {
                        mAlertList.clear();
                        mAlertList.addAll(lal);
                        mAlertAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
    };
    /*
    // refresh the alerts

    private Runnable mRefreshAlert = new Runnable()
    {
        @Override
        public void run()
        {
            if(mAlertList != null)
            {
                //Log.d("Valentine", "Received packet on " + IntentName + " Size " + mAlertList.size());
                mAlertAdapter.refreshData(mAlertList);
            }
        }
    };
    */
    // update the view objects

    private void setViewObject()
    {
        mLaser  = (ImageView) mFragmentView.findViewById(R.id.band_laser);
        mKa     = (ImageView) mFragmentView.findViewById(R.id.band_ka);
        mK      = (ImageView) mFragmentView.findViewById(R.id.band_k);
        mX      = (ImageView) mFragmentView.findViewById(R.id.band_x);

        mBogey  = (ImageView) mFragmentView.findViewById(R.id.bogey);
        mDot    = (ImageView) mFragmentView.findViewById(R.id.bogey_dot);
        mSignal = (ImageView) mFragmentView.findViewById(R.id.signal);

        // arrows
        mFront  = (ImageView) mFragmentView.findViewById(R.id.front);
        mSide   = (ImageView) mFragmentView.findViewById(R.id.side);
        mRear   = (ImageView) mFragmentView.findViewById(R.id.rear);
    }

    // update the arrows

    private void adjustArrows(int i)
    {
        int z = 0;

        if(mBandArrowIndicator[i].getFront())
            z = (mSignalDir[0] > 0 ? mSignalDir[0] : mStrength);
        mFront.setImageResource(mFrontImg[z]);

        z = 0;
        if(mBandArrowIndicator[i].getSide())
            z = (mSignalDir[2] > 0 ? mSignalDir[2] : mStrength);

        mSide.setImageResource(mSideImg[z]);

        z = 0;
        if(mBandArrowIndicator[i].getRear())
            z = (mSignalDir[1] > 0 ? mSignalDir[1] : mStrength);

        mRear.setImageResource(mRearImg[z]);
    }

    // update the bands

    private void adjustBand(int i)
    {
        mLaser.setImageResource(mBandArrowIndicator[i].getLaser() ? R.drawable.red_dot : R.drawable.no_dot);
        mX.setImageResource(mBandArrowIndicator[i].getXBand() ? R.drawable.red_dot : R.drawable.no_dot);
        mK.setImageResource(mBandArrowIndicator[i].getKBand() ? R.drawable.red_dot : R.drawable.no_dot);
        mKa.setImageResource(mBandArrowIndicator[i].getKaBand() ? R.drawable.red_dot : R.drawable.no_dot);
    }
}
