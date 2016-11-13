package com.franckyl.yav1;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.valentine.esp.constants.Devices;
import com.valentine.esp.data.SavvyStatus;

/**
 * Created by franck on 4/17/14.
 */
public class YaV1RealSavvyDialog extends Dialog implements View.OnClickListener
{
    private boolean mSavvyUnmute;
    private boolean mSavvyOverride;
    private int     mSavvySpeed;
    private SavvyStatus mSavvyStatus;

    public YaV1RealSavvyDialog(Context context)
    {
        super(context);
        setContentView(R.layout.yav1_savvy_dialog);
        // we simulate in test mode
        mSavvyStatus   = YaV1RealSavvy.sSavvyStatus;
        Log.d("Valentine", "Initialize Real Savvy  byte is  " + String.format("%02X", mSavvyStatus.getControlByte()));

        // initialize the variables

        mSavvyUnmute   = mSavvyStatus.getUnmuteEnabled();
        mSavvyOverride = mSavvyStatus.getThresholdOverriddenByUser();
        mSavvySpeed    = mSavvyStatus.getSpeedThreshold();
        setTitle(R.string.wired_savvy);

        // initialize the state and listener

        Button b = (Button) findViewById(R.id.ok);
        b.setOnClickListener(this);
        b.setEnabled(false);

        b = (Button) findViewById(R.id.cancel);
        b.setOnClickListener(this);

        CheckBox c = (CheckBox) findViewById(R.id.unmute);
        c.setChecked(mSavvyUnmute);
        c.setOnClickListener(this);

        c = (CheckBox) findViewById(R.id.override);
        c.setChecked(mSavvyOverride);
        c.setOnClickListener(this);

        final EditText et = (EditText) findViewById(R.id.speed);
        TextView t = (TextView) findViewById(R.id.unit);

        if(mSavvyOverride)
        {
            Log.d("Valentine", "Initialize Speed in Unit " + makeSpeedInUnit(mSavvyStatus.getSpeedThreshold()));
            et.setText(String.format("%d", makeSpeedInUnit(mSavvySpeed)));
            t.setText(YaV1.sUnitLabel[YaV1RealSavvy.mCurrUnit]);
        }
        else
        {
            et.setText("");
            et.setEnabled(false);
            t.setText("");
            mSavvySpeed = -1;
        }

        // if override is set, we set the edit text before to
        et.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s)
            {
                String st = s.toString();
                int i = -1;
                if(st != null && st.matches("^[\\d]+$"))
                    i = Integer.valueOf(st);

                if(i < 0 || i > 254)
                {
                    // set the edit text background to red
                    et.setBackgroundColor(Color.RED);
                    mSavvySpeed = 0;
                }
                else
                {
                    mSavvySpeed = getSpeedInUnit(i);
                    et.setBackgroundColor(Color.TRANSPARENT);
                    Log.d("Valentine", "After speed change i " + i + " In unit " + mSavvySpeed);
                }

                checkChanges();
            }
        });
    }

    public void onClick(View v)
    {
        switch(v.getId())
        {
            case R.id.ok:
            {
                // Push default for Savvy
                YaV1RealSavvy.setSavvyStatus(null);
                YaV1.mV1Client.doFactoryDefault(Devices.SAVVY);
                Log.d("Valentine", "Factory reset Savvy");
                //
                try
                {
                    // sleep 250 ms for Savvy to adjust
                    Thread.sleep(250L);
                    // push our changes
                    if(mSavvyOverride)
                    {
                        YaV1.mV1Client.setOverrideThumbwheel((byte) (mSavvySpeed & 0xFF));
                        Log.d("Valentine", "Speed override set " + ((byte) (mSavvySpeed & 0xFF)));
                    }

                    // push the mute status
                    YaV1.mV1Client.setSavvyMute(mSavvyUnmute);
                    Log.d("Valentine", "Muting Savvy set " + mSavvyUnmute);

                    // wait 250 ms before requesting status again
                    Thread.sleep(250L);
                }
                catch(InterruptedException ex)
                {
                    Log.d("Valentine", "Exception when setting Savvy ex " + ex.toString());
                }

                // request new status

                YaV1.mV1Client.getSavvyStatus(YaV1.sInstance, "setCurrentSavvyCallback");
                Log.d("Valentine", "Request Savvy status again");
                dismiss();
                break;
            }

            case R.id.cancel:
            {
                dismiss();
                break;
            }

            case R.id.unmute:
            {
                mSavvyUnmute = ((CheckBox) v).isChecked();
                break;
            }

            case R.id.override:
            {
                mSavvyOverride = ((CheckBox) v).isChecked();
                boolean rc = ((CheckBox) v).isChecked();
                ((TextView) findViewById(R.id.speed)).setEnabled(rc);
                // initialize the speed unit
                TextView t = (TextView) findViewById(R.id.unit);
                t.setText( rc ? YaV1.sUnitLabel[YaV1RealSavvy.mCurrUnit] : "");
                break;
            }
        }

        checkChanges();
    }

    // check if some value has changed

    private void checkChanges()
    {
        Button bt = (Button) findViewById(R.id.ok);
        if(mSavvyOverride != mSavvyStatus.getThresholdOverriddenByUser() || mSavvyUnmute != mSavvyStatus.getUnmuteEnabled() ||
                (mSavvyOverride && mSavvySpeed != mSavvyStatus.getSpeedThreshold() && mSavvySpeed >= 0))
            bt.setEnabled(true);
        else
            bt.setEnabled(false);
    }

    private int makeSpeedInUnit(int speed)
    {
        if(YaV1RealSavvy.mCurrUnit == 1)
            return (int)  Math.round(0.6214D * ( (byte) speed & 0xFF));
        return speed;
    }

    private int getSpeedInUnit(int speed)
    {
        if(YaV1RealSavvy.mCurrUnit == 1)
            return (int) Math.floor(1.6093D * ( (byte) speed & 0xFF));
        return speed;
    }

}
