package com.franckyl.yav1;

import androidx.fragment.app.Fragment;
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

        // audio chips: colour = state (green tint when sounding, dim when unavailable)

        applyAudioChip(R.id.chip_alert, R.id.alert_sound, R.id.alert_state,
                       SoundParam.phoneAlertEnabled, SoundParam.mPhoneAlertOn,
                       R.drawable.alert_on, R.drawable.alert_off);

        applyAudioChip(R.id.chip_voice, R.id.voice_sound, R.id.voice_state,
                       SoundParam.sVoiceCount > 0, SoundParam.mVoiceAlertOn,
                       R.drawable.voice_on, R.drawable.voice_off);

        applyAudioChip(R.id.chip_vibrate, R.id.vibrate_sound, R.id.vibrate_state,
                       SoundParam.mVibratorEnabled > SoundParam.VIB_DISABLED, SoundParam.mVibratorOn,
                       R.drawable.vibrate_on, R.drawable.vibrate_off);

        // lockout state pill: Off grey / On green / Frozen amber
        Button lBtn = (Button) mFragmentView.findViewById(R.id.lockout);

        if(!YaV1CurrentPosition.sLockout || YaV1.sAutoLockout == null)
        {
            lBtn.setText(R.string.tool_tab_lockout_disabled);
            lBtn.setTextColor(getResources().getColor(R.color.state_locked));
            lBtn.setClickable(false);
            lBtn.setEnabled(false);
        }
        else
        {
            boolean normal = (YaV1.sAutoLockout.mMode == LockoutData.MODE_NORMAL);
            lBtn.setText(normal ? R.string.tool_tab_lockout_normal : R.string.tool_tab_lockout_frozen);
            lBtn.setTextColor(getResources().getColor(normal ? R.color.status_good : R.color.status_warn));
            lBtn.setClickable(true);
            lBtn.setEnabled(true);
        }

        // check the current mode, if not euro mode remove the sweep view
        if(YaV1.mV1Client != null && YaV1.mV1Client.isGen2())
        {
            // The V1 Gen2 has no custom sweeps: say so instead of offering the
            // quick sweep push list.
            ( (RelativeLayout) mFragmentView.findViewById(R.id.v1_sweep)).setVisibility(View.VISIBLE);
            txtV = (TextView) mFragmentView.findViewById(R.id.current_sweep_name);
            txtV.setText(R.string.gen2_no_custom_sweeps);
            txtV.setEnabled(false);
        }
        else if(YaV1.sModeData == null || !YaV1.sModeData.getEuroMode())
        {
            ( (RelativeLayout) mFragmentView.findViewById(R.id.v1_sweep)).setVisibility(View.GONE);
        }
        else
        {
            ( (RelativeLayout) mFragmentView.findViewById(R.id.v1_sweep)).setVisibility(View.VISIBLE);
            txtV = (TextView) mFragmentView.findViewById(R.id.current_sweep_name);
            txtV.setText(YaV1.sSweep.getCurrentName());
            txtV.setEnabled(true);
        }
    }

    // style one audio chip: green-tinted when sounding, neutral when off,
    // dimmed when the feature is unavailable
    private void applyAudioChip(int chipId, int btnId, int stateId,
                                boolean available, boolean on, int iconOn, int iconOff)
    {
        View        chip = mFragmentView.findViewById(chipId);
        ImageButton btn  = (ImageButton) mFragmentView.findViewById(btnId);
        TextView    st   = (TextView) mFragmentView.findViewById(stateId);

        if(chip == null || btn == null || st == null)
            return;

        if(available)
        {
            chip.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_tool_off);
            chip.setAlpha(1f);
            btn.setImageResource(on ? iconOn : iconOff);
            btn.setClickable(true);
            btn.setEnabled(true);
            st.setText(on ? R.string.tools_state_on : R.string.tools_state_off);
            st.setTextColor(getResources().getColor(on ? R.color.status_good : R.color.ink2));
        }
        else
        {
            chip.setBackgroundResource(R.drawable.bg_tool_off);
            chip.setAlpha(0.45f);
            btn.setImageResource(iconOff);
            btn.setClickable(false);
            btn.setEnabled(false);
            st.setText(R.string.tools_state_off);
            st.setTextColor(getResources().getColor(R.color.state_locked));
        }
    }
}
