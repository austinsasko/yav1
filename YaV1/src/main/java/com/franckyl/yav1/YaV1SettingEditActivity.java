package com.franckyl.yav1;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.valentine.esp.data.UserSettings;

import java.util.ArrayList;

/**
 * Created by franck on 8/7/13.
 */
public class YaV1SettingEditActivity extends Activity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener
{
    private YaV1Setting  mSetting;
    private int          mSettingId;
    private UserSettings mUsr;
    private Context      mContext;
    private boolean      mView = false;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mView = this.getIntent().getBooleanExtra("view", false);

        mContext   = this;
        mSettingId = this.getIntent().getIntExtra("settingId", -1);

        mSetting   = YaV1.sV1Settings.duplicateSetting(mSettingId);

        // we can get a UserSetting from the object
        mUsr = mSetting.getV1Definition();

        // we show our view
        setContentView(R.layout.yav1_setting_edit_activity);

        // set the values
        setParts(true);
    }

    @Override
    protected void onResume()
    {
        // YaV1.superResume();
        setParts(false);
        super.onResume();
    }

    @Override

    public void onPostResume()
    {
        super.onPostResume();
        YaV1.superResume();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }


    @Override
    public void onPause()
    {
        YaV1.superPause();
        super.onPause();
    }

    @Override
    public void onClick(View view)
    {
        int id = view.getId();
        CheckBox x;
        boolean rc;

        switch(id)
        {
            case R.id.band_k:
                //Log.d("Valentine", "Band K clicked");
                mUsr.setKBandAsBoolean( ((CheckBox) view).isChecked());
                break;
            case R.id.band_ka:
                //Log.d("Valentine", "Band Ka clicked");
                mUsr.setKaBandAsBoolean(((CheckBox) view).isChecked());
                break;
            case R.id.band_laser:
                //Log.d("Valentine", "Band laser clicked");
                mUsr.setLaserAsBoolean(((CheckBox) view).isChecked());
                break;
            case R.id.band_ku:
                //Log.d("Valentine", "Band Ku clicked");
                mUsr.setKuBandAsBoolean(((CheckBox) view).isChecked());
                break;
            case R.id.band_x:
                //Log.d("Valentine", "Band X clicked");
                rc = ((CheckBox) view).isChecked();
                mUsr.setXBandAsBoolean(rc);
                // if euro mode check euro x
                //x = (CheckBox) findViewById(R.id.euro_mode);
                //if(x.isChecked())
                ((CheckBox) findViewById(R.id.eurox)).setChecked(rc);
                mUsr.setEuroXBandAsBoolean(rc);
                break;
            case R.id.un_mute_above_5:
                //Log.d("Valentine", "Unmute above 5X clicked");
                mUsr.setKPersistentUnmute6LightsAsBoolean(((CheckBox) view).isChecked());
                break;
            case R.id.no_mute_above_3:
                //Log.d("Valentine", "No mute first 3 changed");
                mUsr.setKInitialUnmute4LightsAsBoolean(((CheckBox) view).isChecked());
                break;
            case R.id.mute_rear_k:
                //Log.d("Valentine", "No mute first 3 changed");
                mUsr.setKRearMuteAsBoolean(((CheckBox) view).isChecked());
                break;
            case R.id.euro_mode:
                //Log.d("Valentine", "Euro mode changed");
                rc = (((CheckBox) view).isChecked() ? true : false);
                mUsr.setEuroAsBoolean(rc);
                // we adjust the Pop text
                TextView tvE = (TextView) findViewById(R.id.poptext);
                tvE.setText((rc ? "K-POP" : "POP"));
                // onOffLayout(R.id.pop_row, !rc);
                onOffLayout(R.id.eurox_row, rc);
                // kaguard visible only when non euro mode
                onOffLayout(R.id.kaguard_row, !rc);
                tvE = (TextView) findViewById(R.id.warn_k);
                if(rc)
                {
                    tvE.setVisibility(View.VISIBLE);
                    ( (CheckBox) findViewById(R.id.band_k)).setEnabled(false);
                }
                else
                {
                    tvE.setVisibility(View.GONE);
                    ( (CheckBox) findViewById(R.id.band_k)).setEnabled(true);
                }
                break;
            case R.id.filter:
                rc = (((CheckBox) view).isChecked() ? true : false);
                mUsr.setFilterAsBoolean(((CheckBox) view).isChecked());
                // changes: when filter is checked we disable pop
                onOffLayout(R.id.pop_row, !rc);
                if(rc)
                {
                    mUsr.setPopAsBoolean(false);
                    x = (CheckBox) findViewById(R.id.pop);
                    x.setChecked(false);
                }
                break;
            case R.id.eurox:
                //Log.d("Valentine", "Euro X band changed");
                rc = ((CheckBox) view).isChecked();
                mUsr.setEuroXBandAsBoolean(rc);
                x = (CheckBox) findViewById(R.id.band_x);
                x.setChecked(rc);
                mUsr.setXBandAsBoolean(rc);
                break;
            case R.id.pop:
                mUsr.setPopAsBoolean(((CheckBox) view).isChecked());
                break;
            case R.id.texttime:
                //Log.d("Valentine", "We should popup time list");
                final ArrayList<String> lStr = mUsr.getAllowedTimeoutValues();
                final CharSequence[] cs = lStr.toArray(new CharSequence[lStr.size()]);
                final TextView tvt = (TextView) view;
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.v1_setting_muting_period);
                builder.setItems(cs, new DialogInterface.OnClickListener()
                {

                    public void onClick(DialogInterface dialog, int item)
                    {
                        mUsr.setKMuteTimer(lStr.get(item));
                        tvt.setText(lStr.get(item));
                    }
                }).show();
                break;
            case R.id.editname:
                final EditText input = new EditText(mContext);
                final TextView tvT   = (TextView) findViewById(R.id.setting_name);
                input.setText(mSetting.getName());
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.v1_setting_edit_name_title)
                        .setMessage(R.string.v1_setting_edit_name_message)
                        .setView(input)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int whichButton)
                            {
                                // we will duplicate the given one using the id
                                String value = input.getText().toString();
                                mSetting.setName(value);
                                tvT.setText(value);
                            }
                        }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int whichButton)
                            {
                                // Do nothing.
                            }
                        }).show();
                break;
            case R.id.factory:
                //Log.d("Valentine", "Factory has been clicked");
                // we apply factory and display again
                mSetting.applyFactoryDefault();
                mUsr=mSetting.getV1Definition();
                setParts(false);
                break;
            case R.id.cancel:
            case R.id.save:
                //Log.d("valentine", "Cancel has been clicked");
                boolean modified = false;
                boolean warnPop  = false;

                if(id == R.id.save)
                {
                    mSetting.setBytes(mUsr.buildBytes());
                    // check if we did modify what affects V1
                    YaV1Setting initial = YaV1.sV1Settings.getSettingFromId(mSettingId);

                    if(!initial.isSame(mSetting))
                    {
                        modified = true;
                    }

                    YaV1.sV1Settings.updateSetting(mSettingId, mSetting);
                }
                // finish the activity with ok
                //Log.d("Valentine", "Saved has been clicked");
                Intent response = new Intent();
                setResult( (id == R.id.save ? Activity.RESULT_OK : Activity.RESULT_CANCELED), response);
                if(id == R.id.save)
                {
                    response.putExtra("modified", modified);
                }

                finish();
                return;
            //case R.id.logic_off:
            //case R.id.logic_on:
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int i)
    {
        int id = radioGroup.getId();
        switch(id)
        {
            case R.id.rdg_logic:
                //Log.d("Valentine", "Logic changed");
                mUsr.setFeatureBGKMutingAsBoolean( (i == R.id.logic_on));
                onOffLayout(R.id.kmuting, (i == R.id.logic_on));
                break;
            case R.id.rdg_kaguard:
                //Log.d("Valentine", "KaGuard changed");
                mUsr.setKaFalseGuardAsBoolean((i == R.id.kaguard_on ? true : false));
                break;
            case R.id.rdg_bogey:
                //Log.d("Valentine", "Post mute bogey changed");
                //mUsr.setPostMuteBogeyLockVolumeAsBoolean((i == R.id.bogey_lever ? true : false));
                mUsr.setPostmuteBogeyLockVolume((i == R.id.bogey_lever ? UserSettings.Constants.LEVER : UserSettings.Constants.KNOB));
                break;
            case R.id.rdg_mute:
                //Log.d("Valentine", "Mute control changed");
                //mUsr.setMuteVolumeAsBoolean((i == R.id.mute_zero ? true : false));
                mUsr.setMuteVolume((i == R.id.mute_zero ? UserSettings.Constants.ZERO : UserSettings.Constants.LEVER));
                break;
            case R.id.rdg_response:
                //Log.d("Valentine", "Response Ka changed");
                //mUsr.setBargraphAsBoolean((i == R.id.response_intensive ? true : false));
                mUsr.setBargraph((i == R.id.response_intensive ? UserSettings.Constants.RESPONSIVE : UserSettings.Constants.NORMAL));
                break;
        }
    }

    // set the different parts

    private void setParts(boolean initListener)
    {
        CheckBox   chk;
        TextView   tvT;
        RadioGroup rdg;
        Button     btn;

        if(initListener)
            adjustEuroX();

        chk = (CheckBox) findViewById(R.id.band_laser);
        chk.setChecked(mUsr.getLaserAsBoolean());

        if(initListener && !mView)
            chk.setOnClickListener(this);

        if(mView)
            chk.setEnabled(false);
        chk = (CheckBox) findViewById(R.id.band_k);
        chk.setChecked(mUsr.getKBandAsBoolean());


        if(initListener && !mView)
            chk.setOnClickListener(this);
        if(mView)
            chk.setEnabled(false);

        chk = (CheckBox) findViewById(R.id.band_ka);
        chk.setChecked(mUsr.getKaBandAsBoolean());

        if(initListener && !mView)
            chk.setOnClickListener(this);
        if(mView)
            chk.setEnabled(false);

        chk = (CheckBox) findViewById(R.id.band_ku);
        chk.setChecked(mUsr.getKuBandAsBoolean());

        if(initListener && !mView)
            chk.setOnClickListener(this);
        if(mView)
            chk.setEnabled(false);

        chk = (CheckBox) findViewById(R.id.band_x);
        chk.setChecked(mUsr.getXBandAsBoolean());

        if(initListener && !mView)
            chk.setOnClickListener(this);
        if(mView)
            chk.setEnabled(false);

        // The RadioGroups
        rdg = (RadioGroup) findViewById(R.id.rdg_kaguard);
        rdg.check((mUsr.getKaFalseGuardAsBoolean() ? R.id.kaguard_on :R.id.kaguard_off));

        if(initListener && !mView)
            rdg.setOnCheckedChangeListener(this);
        if(mView)
        {
            rdg.setEnabled(false);
            for(int i = 0; i < rdg.getChildCount(); i++)
                ((RadioButton) rdg.getChildAt(i)).setEnabled(false);
        }

        rdg = (RadioGroup) findViewById(R.id.rdg_response);
        rdg.check((mUsr.getBargraph() == UserSettings.Constants.RESPONSIVE ? R.id.response_intensive :R.id.response_normal));

        if(initListener && !mView)
            rdg.setOnCheckedChangeListener(this);

        if(mView)
        {
            rdg.setEnabled(false);
            for(int i = 0; i < rdg.getChildCount(); i++)
                ((RadioButton) rdg.getChildAt(i)).setEnabled(false);
        }

        rdg = (RadioGroup) findViewById(R.id.rdg_mute);
        rdg.check((mUsr.getMuteVolume() == UserSettings.Constants.LEVER ? R.id.mute_lever :R.id.mute_zero));

        if(initListener && !mView)
            rdg.setOnCheckedChangeListener(this);
        if(mView)
        {
            rdg.setEnabled(false);
            for(int i = 0; i < rdg.getChildCount(); i++)
                ((RadioButton) rdg.getChildAt(i)).setEnabled(false);
        }

        rdg = (RadioGroup) findViewById(R.id.rdg_bogey);
        rdg.check((mUsr.getPostmuteBogeyLockVolume() == UserSettings.Constants.KNOB ? R.id.bogey_knob :R.id.bogey_lever));

        if(initListener && !mView)
            rdg.setOnCheckedChangeListener(this);
        if(mView)
        {
            rdg.setEnabled(false);
            for(int i = 0; i < rdg.getChildCount(); i++)
                ((RadioButton) rdg.getChildAt(i)).setEnabled(false);
        }

        rdg = (RadioGroup) findViewById(R.id.rdg_logic);
        rdg.check((mUsr.getFeatureBGKMutingAsBoolean() ? R.id.logic_on :R.id.logic_off));

        if(initListener && !mView)
            rdg.setOnCheckedChangeListener(this);
        if(mView)
        {
            rdg.setEnabled(false);
            for(int i = 0; i < rdg.getChildCount(); i++)
                ((RadioButton) rdg.getChildAt(i)).setEnabled(false);
        }
        // if we do not Mute K, we can "hide" The section
        onOffLayout(R.id.kmuting, mUsr.getFeatureBGKMutingAsBoolean());

        tvT = (TextView) findViewById(R.id.texttime);
        tvT.setText(mUsr.getKMuteTimer());
        if(initListener && !mView)
            tvT.setOnClickListener(this);

        chk = (CheckBox) findViewById(R.id.no_mute_above_3);
        chk.setChecked(mUsr.getKInitialUnmute4LightsAsBoolean());
        if(initListener && !mView)
            chk.setOnClickListener(this);
        if(mView)
            chk.setEnabled(false);

        chk = (CheckBox) findViewById(R.id.un_mute_above_5);
        chk.setChecked(mUsr.getKPersistentUnmute6LightsAsBoolean());
        if(initListener && !mView)
            chk.setOnClickListener(this);
        if(mView)
            chk.setEnabled(false);

        chk = (CheckBox) findViewById(R.id.mute_rear_k);
        chk.setChecked(mUsr.getKRearMuteAsBoolean());
        if(initListener && !mView)
            chk.setOnClickListener(this);
        if(mView)
            chk.setEnabled(false);

        // special
        chk = (CheckBox) findViewById(R.id.euro_mode);
        chk.setChecked(mUsr.getEuroAsBoolean());

        tvT = (TextView) findViewById(R.id.warn_k);
        if(mUsr.getEuroAsBoolean())
        {
            ( (CheckBox) findViewById(R.id.band_k)).setEnabled(false);
            tvT.setVisibility(View.VISIBLE);
        }
        else
        {
            ( (CheckBox) findViewById(R.id.band_k)).setEnabled(true);
            tvT.setVisibility(View.GONE);
        }

        if(initListener && !mView)
            chk.setOnClickListener(this);

        if(mView)
            chk.setEnabled(false);

        chk = (CheckBox) findViewById(R.id.filter);
        chk.setChecked(mUsr.getFilterAsBoolean());
        if(initListener && !mView)
            chk.setOnClickListener(this);
        if(mView)
            chk.setEnabled(false);

        // only when not euro mode
        chk = (CheckBox) findViewById(R.id.pop);
        chk.setChecked(mUsr.getPopAsBoolean());
        if(initListener && !mView)
            chk.setOnClickListener(this);
        // changes: when filter is on, we disable pop
        if(mView || mUsr.getFilterAsBoolean())
            chk.setEnabled(false);
        onOffLayout(R.id.pop_row, !mUsr.getFilterAsBoolean());

        // Euro x mode
        chk = (CheckBox) findViewById(R.id.eurox);
        chk.setChecked(mUsr.getEuroXBandAsBoolean());
        if(initListener && !mView)
            chk.setOnClickListener(this);
        if(mView)
            chk.setEnabled(false);

        // in euro mode we do not have Pop
        // onOffLayout(R.id.pop_row, !mUsr.getEuroAsBoolean());
        tvT = (TextView) findViewById(R.id.poptext);
        tvT.setText((mUsr.getEuroAsBoolean() ? "K-POP" : "POP"));

        // in euro mode only we have eurox
        onOffLayout(R.id.eurox_row, mUsr.getEuroAsBoolean());
        // in euro mode no KaGuard option
        onOffLayout(R.id.kaguard_row, !mUsr.getEuroAsBoolean());

        //set the name
        tvT = (TextView) findViewById(R.id.setting_name);
        tvT.setText(mSetting.getName());

        if(initListener)
        {
            ImageButton edt = (ImageButton) findViewById(R.id.editname);
            if(mView)
                edt.setVisibility(View.GONE);
            else
                edt.setOnClickListener(this);

            btn = (Button) findViewById(R.id.factory);
            if(mView)
                btn.setVisibility(View.GONE);
            else
                btn.setOnClickListener(this);

            btn = (Button) findViewById(R.id.cancel);
            btn.setOnClickListener(this);


            btn = (Button) findViewById(R.id.save);
            if(mView)
                btn.setVisibility(View.GONE);
            else
                btn.setOnClickListener(this);
        }
    }

    // adjust the X to Euro_x on initial display

    private void adjustEuroX()
    {
        CheckBox chk;
        boolean  rc;

        // are we in euro mode ?
        if(mUsr.getEuroAsBoolean())
        {
            // if euro X is checked, we check X_band
            rc = mUsr.getEuroXBandAsBoolean();
            chk = (CheckBox) findViewById(R.id.band_x);
            mUsr.setXBandAsBoolean(rc);
            chk.setChecked(rc);
        }
        else
        {
            // we adjust euro x as X band
            rc = mUsr.getXBandAsBoolean();
            mUsr.setEuroXBandAsBoolean(rc);
            chk = (CheckBox) findViewById(R.id.eurox);
            chk.setChecked(rc);
        }
    }

    // turn on or off layout

    private void onOffLayout(int id, boolean onOff)
    {
        LinearLayout ll = (LinearLayout) findViewById(id);
        ll.setVisibility( (onOff ? View.VISIBLE : View.GONE));
    }
}
