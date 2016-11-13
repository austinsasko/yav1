package com.franckyl.yav1;

//import android.app.Fragment;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.franckyl.yav1.events.GpsEvent;
import com.franckyl.yav1lib.YaV1Alert;
import com.squareup.otto.Subscribe;

/**
 * Created by franck on 2/27/14.
 */
public class YaV1GpsFragment extends Fragment
{
    protected View          mFragmentView;

    // GPS component

    TextView  mTvSpeed;
    TextView  mTvUnit;
    TextView  mTvBearing;
    ImageView mIvOld;

    private final   Handler mHandler  = new Handler();
    private boolean mRun              = false;
    private long    mDelay            = 1000;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mFragmentView = inflater.inflate(R.layout.yav1_gps_fragment, container, false);
        setGpsParts();
        return mFragmentView;
    }

    @Override
    public void onResume()
    {
        Log.d("Valentine", "Gps fragment resumed");
        super.onResume();
        YaV1.getEventBus().register(this);
        mRun = true;
        // run();
    }

    @Override
    public void onPause()
    {
        Log.d("Valentine", "Gps fragment paused");
        super.onPause();
        YaV1.getEventBus().unregister(this);
        mRun = false;
    }

    @Subscribe
    public void updatePosition(GpsEvent evt)
    {
        // Log.d("Valentine GPS", "Position updated");
        getActivity().runOnUiThread(updateGps);
    }

    // run this view
    public void run()
    {
        if(mRun)
            mHandler.post(updateGps);
    }
    // set the gpd view parts

    protected boolean setGpsParts()
    {
        mTvSpeed   = (TextView) mFragmentView.findViewById(R.id.SPEED)            ;
        mTvUnit    = (TextView) mFragmentView.findViewById(R.id.UNIT);
        mTvBearing = (TextView) mFragmentView.findViewById(R.id.BEARING);
        mIvOld     = (ImageView) mFragmentView.findViewById(R.id.OLD);

        RelativeLayout lGps = (RelativeLayout) mFragmentView.findViewById(R.id.gps_row);

        if(YaV1.sPrefs.getBoolean("reset_gps", false) || YaV1CurrentPosition.sLockout)
        {
            lGps.setClickable(true);
            lGps.setFocusableInTouchMode(true);

            if( YaV1.sPrefs.getBoolean("reset_gps", false))
            {
                lGps.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        ((YaV1ScreenActivity) getActivity()).resetGps();
                    }
                });
            }
        }

        return true;
    }
    // update the Gps part
    public final Runnable updateGps = new Runnable()
    {
        public void run()
        {
            // if speed is < 0, means we are searching position
            if(YaV1CurrentPosition.speed < 0)
            {
                if(YaV1RealSavvy.enabled)
                    showSavvySpeed();
                else
                {
                    mTvSpeed.setText("");
                    mIvOld.setImageResource(YaV1Alert.GPS_OFF);
                    mTvBearing.setText("");
                    mTvUnit.setText("");
                }
            }
            else
            {
                mIvOld.setImageResource(YaV1CurrentPosition.isValid ? YaV1Alert.GPS_ON : YaV1Alert.GPS_OFF);
                if(!YaV1CurrentPosition.isValid && YaV1RealSavvy.enabled)
                    showSavvySpeed();
                else
                {
                    mTvBearing.setText(YaV1CurrentPosition.getBearingString());
                    mTvSpeed.setText(String.format("% 3d", (int) YaV1CurrentPosition.cSpeed));
                    mTvUnit.setText(YaV1.sUnitLabel[YaV1.sCurrUnit]);
                }
            }

            //if(mRun)
            //    mHandler.postDelayed(updateGps, mDelay);
        }
    };

    // display the savvy speed

    private void showSavvySpeed()
    {
        mIvOld.setImageResource(YaV1Alert.GPS_OFF);
        mTvUnit.setText(YaV1.sUnitLabel[YaV1.sCurrUnit]);
        mTvSpeed.setText(String.format("% 3d", YaV1RealSavvy.speed));
        mTvBearing.setText("Savvy");
    }
}
