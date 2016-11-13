package com.franckyl.yav1;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.franckyl.yav1.events.AlertEvent;
import com.franckyl.yav1.events.InfoEvent;
import com.franckyl.yav1.lockout.LockoutData;
import com.squareup.otto.Subscribe;

/**
 * Created by franck on 4/29/14.
 */
public class YaV1ScreenToolFragment extends Fragment
{
    private View     mFragmentView;
    private TextView mModeText      = null;
    private boolean  mIsRegistered  = false;
    // create

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        ( (YaV1ScreenActivity) getActivity()).setFragmentTag(3, getTag());
    }

    // createView

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mFragmentView = inflater.inflate(R.layout.yav1_screen_tool_fragment, container, false);

        mModeText = (TextView) mFragmentView.findViewById(R.id.v1_mode);
        mModeText.setTypeface(YaV1.sDigital);

        refreshViewCompo();

        return mFragmentView;
    }

    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if(!mIsRegistered)
        {
            YaV1.getEventBus().register(this);
            mIsRegistered = true;
        }
        // we refresh
        refreshViewCompo();
    }

    @Override
    public void onPause()
    {
        if(mIsRegistered)
        {
            YaV1.getEventBus().unregister(this);
            mIsRegistered = false;
        }

        super.onPause();
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

            refreshViewCompo();
        }
        else
        {
            if(mIsRegistered)
            {
                YaV1.getEventBus().unregister(this);
                mIsRegistered = false;
            }
        }
    }

    @Subscribe
    public void onNewAlert(InfoEvent info)
    {
        // we would redisplay the settings, or adjust the icon
        if(info.getType() == InfoEvent.Type.V1_INFO)
        {
            Log.d("Valentine", "Refresh settings in tools");
            // show the V1 info (setting / sweep / battery)
            getActivity().runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    refreshViewCompo();
                }
            });
        }
    }

    // initialize our view

    public void refreshViewCompo()
    {
        if(!isVisible())
            return;

        // check if Savvy is enabled first
        if(YaV1.sPrefs.getBoolean("use_gps", false) && YaV1.sPrefs.getBoolean("mute_v1_under_speed", false))
        {
            // set the unit
            ( (TextView) mFragmentView.findViewById(R.id.savvy_speed_unit)).setText(YaV1.sUnitLabel[YaV1.sCurrUnit]);
            //set the speed
            ( (TextView) mFragmentView.findViewById(R.id.savvy_speed)).setText(Integer.toString(YaV1AlertService.mSavvySpeedAsInt));
        }
        else
            ( (RelativeLayout) mFragmentView.findViewById(R.id.savvy)).setVisibility(View.GONE);

        // set the Type face and Text for the Mode button

        mModeText.setText(YaV1.getModeText());
        // set the setting name

        TextView txtV = (TextView) mFragmentView.findViewById(R.id.current_setting_name);

        String str = YaV1.sV1Settings.getCurrentName();

        if(txtV != null && str != null)
            txtV.setText(str);

        // button states
        ImageButton imgBtn = (ImageButton) mFragmentView.findViewById(R.id.alert_sound);

        // Phone alerts

        if(SoundParam.phoneAlertEnabled)
        {
            imgBtn.setBackgroundResource(R.drawable.btn_default_normal_lblue);
            imgBtn.setClickable(true);
            imgBtn.setEnabled(true);
            // set image according to current state
            imgBtn.setImageResource((SoundParam.mPhoneAlertOn ? R.drawable.alert_on : R.drawable.alert_off));
        }
        else
        {
            imgBtn.setBackgroundResource(R.drawable.btn_default_normal_yellow);
            imgBtn.setImageResource(R.drawable.alert_off);
            imgBtn.setClickable(false);
            imgBtn.setEnabled(false);
        }

        imgBtn = (ImageButton) mFragmentView.findViewById(R.id.voice_sound);

        // Voice alert

        if(SoundParam.sVoiceCount > 0)
        {
            imgBtn.setBackgroundResource(R.drawable.btn_default_normal_lblue);
            imgBtn.setClickable(true);
            imgBtn.setEnabled(true);
            // set image according to state
            imgBtn.setImageResource( (SoundParam.mVoiceAlertOn ? R.drawable.voice_on : R.drawable.voice_off));
        }
        else
        {
            imgBtn.setBackgroundResource(R.drawable.btn_default_normal_yellow);
            imgBtn.setImageResource(R.drawable.voice_off);
            imgBtn.setClickable(false);
            imgBtn.setEnabled(false);
        }

        // vibrator

        imgBtn = (ImageButton) mFragmentView.findViewById(R.id.vibrate_sound);

        if(SoundParam.mVibratorEnabled > SoundParam.VIB_DISABLED)
        {
            imgBtn.setBackgroundResource(R.drawable.btn_default_normal_lblue);
            imgBtn.setClickable(true);
            imgBtn.setEnabled(true);
            // set image according to state - SoundManager
            imgBtn.setImageResource( (SoundParam.mVibratorOn ? R.drawable.vibrate_on : R.drawable.vibrate_off));
        }
        else
        {
            imgBtn.setBackgroundResource(R.drawable.btn_default_normal_yellow);
            imgBtn.setImageResource(R.drawable.vibrate_off);
            imgBtn.setClickable(false);
            imgBtn.setEnabled(false);
        }

        // lockout
        Button lBtn = (Button) mFragmentView.findViewById(R.id.lockout);

        if(!YaV1CurrentPosition.sLockout || YaV1.sAutoLockout == null)
        {
            lBtn.setBackgroundResource(R.drawable.btn_default_normal_yellow);
            lBtn.setText(R.string.tool_tab_lockout_disabled);
            lBtn.setClickable(false);
            lBtn.setEnabled(false);
        }
        else
        {
            lBtn.setBackgroundResource(R.drawable.btn_default_normal_lblue);
            // check for frozen text
            lBtn.setText((YaV1.sAutoLockout.mMode == LockoutData.MODE_NORMAL ? R.string.tool_tab_lockout_normal : R.string.tool_tab_lockout_frozen));
            lBtn.setClickable(true);
            lBtn.setEnabled(true);
        }

        // check the current mode, if not euro mode remove the sweep view
        if(YaV1.sModeData == null || !YaV1.sModeData.getEuroMode())
        {
            ( (RelativeLayout) mFragmentView.findViewById(R.id.v1_sweep)).setVisibility(View.GONE);
        }
        else
        {
            ( (RelativeLayout) mFragmentView.findViewById(R.id.v1_sweep)).setVisibility(View.VISIBLE);
            txtV = (TextView) mFragmentView.findViewById(R.id.current_sweep_name);
            txtV.setText(YaV1.sSweep.getCurrentName());
        }
    }
}
