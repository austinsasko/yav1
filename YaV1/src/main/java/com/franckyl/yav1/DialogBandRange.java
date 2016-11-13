package com.franckyl.yav1;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.franckyl.yav1.utils.EditTextFrequency;

import java.util.Arrays;
import java.util.List;

import yuku.ambilwarna.AmbilWarnaDialog;

/**
 * Created by franck on 7/16/13.
 */
public class DialogBandRange extends DialogPreference
{
    EditTextFrequency lowBand;
    EditTextFrequency highBand;
    Button            mBtnColor;
    static Button     mVoiceBtn;

    private String mBand         = "";
    private int    mBandInt      = -1;
    private int    mBandNumber   = 0;
    private String mPrefName     = "";
    private boolean mValid       = false;
    private int     mColor       = Color.TRANSPARENT;
    private static String mVoice        = "";
    private static String mVoiceTitle   = "";
    private Pair<Integer, Integer> mPair = null;

    Context         mContext;

    public DialogBandRange(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DialogBandRange);
        mBand       = a.getString(R.styleable.DialogBandRange_bandname);
        mBandInt    = YaV1.BandBoundaries.getBandIntFromStr(mBand);
        mBandNumber = a.getInteger(R.styleable.DialogBandRange_bandnumber, -1);
        mPrefName   = "band_" + mBand + mBandNumber;
        // load ot xml
        setDialogLayoutResource(R.layout.band_range);

        Pair<Integer, Integer> p = YaV1.BandBoundaries.getEdges(mBandInt);
        setTitle(String.format("Box %d %s Band (%d - %d)", mBandNumber, mBand.toUpperCase(), p.first, p.second));
        mContext = context;
    }

    protected void showDialog(Bundle bundle)
    {
        super.showDialog(bundle);

        Button pos = ((AlertDialog) getDialog()).getButton(DialogInterface.BUTTON_POSITIVE);

        pos.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                mPair = lowBand.finalCheck(lowBand, highBand);

                /*
                String sLow  = lowBand.getText().toString();
                String sHigh = highBand.getText().toString();

                // case of removing a range

                if(sLow.isEmpty() && sHigh.isEmpty())
                {
                    mValid = true;
                }
                else
                {
                    int low     = Integer.valueOf(lowBand.getText().toString());
                    int high    = Integer.valueOf(highBand.getText().toString());

                    mValid = true;

                    if(!YaV1.BandBoundaries.checkValid(mBand, low) || low > high)
                    {
                        lowBand.setTextColor(Color.parseColor("#FF0000"));
                        mValid = false;
                    }

                    if(!YaV1.BandBoundaries.checkValid(mBand, high) || low > high)
                    {
                        highBand.setTextColor(Color.parseColor("#FF0000"));
                        mValid = false;
                    }
                }

                if(mValid)
                    getDialog().dismiss();
                */
                if(mPair != null)
                {
                    mValid = true;
                    getDialog().dismiss();
                }
            }
        });

        mBtnColor = (Button) getDialog().findViewById(R.id.color);

        mBtnColor.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view)
            {
                AmbilWarnaDialog dialog = new AmbilWarnaDialog(getContext(), mColor, new AmbilWarnaDialog.OnAmbilWarnaListener()
                {
                    @Override
                    public void onOk(AmbilWarnaDialog dialog, int color)
                    {
                        // color is the color selected by the user.
                        mColor = color;
                        mBtnColor.setBackgroundColor(mColor);
                    }

                    @Override
                    public void onCancel(AmbilWarnaDialog dialog)
                    {
                        // cancel was selected by the user
                    }
                });
                dialog.show();
            }
        });

        Button rmv = (Button) getDialog().findViewById(R.id.remove);

        // the voice picker

        mVoiceBtn = (Button) getDialog().findViewById(R.id.voice);

        mVoiceBtn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View view)
            {
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                        RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone");
                if(!mVoice.isEmpty())
                {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    Uri.parse(mVoice));
                }
                else
                {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
                }
                // start the activity
                ((Activity) mContext).startActivityForResult(intent, YaV1Activity.GET_VOICEALERT);
            }
        });

        // the remove button

        rmv.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View view)
            {
                /*
                lowBand.setText("");
                highBand.setText("");
                */
                mPair = null;
                mValid = true;
                getDialog().dismiss();
            }
        });

    }

    // static function to retrieve the alert voice

    public static void setVoice(String nVoice, String nTitle)
    {
        mVoice      = nVoice;
        mVoiceTitle = nTitle;

        if(mVoice.isEmpty())
            mVoiceBtn.setText(R.string.pref_box_voice_alert);
        else
            mVoiceBtn.setText(mVoiceTitle);
    }

    protected void onBindDialogView(View view)
    {
        super.onBindDialogView(view);

        lowBand     = new EditTextFrequency(mContext, (EditText) view.findViewById(R.id.lowBand), mBandInt, true);
        highBand    = new EditTextFrequency(mContext, (EditText) view.findViewById(R.id.highBand), mBandInt, false);
        mBtnColor   = (Button)   view.findViewById(R.id.color);
        mVoiceBtn   = (Button)   view.findViewById(R.id.voice);

        //mSample     = (LinearLayout) view.findViewById(R.id.sample);

        // TextView mText = (TextView) view.findViewById(R.id.explain);
        // mText.setText("From " + YaV1.BandBoundaries.mFreq.get(mBand)[0] + " MHz to " + YaV1.BandBoundaries.mFreq.get(mBand)[1] + " MHz");
        mValid = false;

        // set the current values from preferences
        String str = getSharedPreferences().getString(mPrefName, "");

        lowBand.setFrequency(0);
        highBand.setFrequency(0);

        if(!str.isEmpty())
        {
            List<String> items = Arrays.asList(str.split("\\s*,\\s*"));
            if(items.size() >= 3)
            {
                lowBand.setFrequency(Integer.valueOf(items.get(0)));
                highBand.setFrequency(Integer.valueOf(items.get(1)));

                mColor = Integer.valueOf(items.get(2));

                if(items.size() > 3)
                {
                    // we got a voice
                    mVoice = items.get(3);
                    // check if it matches a ring tone and set the title
                    Uri uri = Uri.parse(mVoice);
                    Ringtone ringtone;

                    if(uri != null && (ringtone = RingtoneManager.getRingtone(mContext, uri)) != null)
                    {
                        mVoiceTitle = ringtone.getTitle(mContext);
                        mVoiceBtn.setText(mVoiceTitle);
                    }
                    else
                    {
                        mVoice      = "";
                        mVoiceTitle = "";
                        mVoiceBtn.setText(R.string.pref_box_voice_alert);
                    }
                }
            }
        }
        else
        {
            mColor      = Color.TRANSPARENT;
            mVoice      = "";
            mVoiceTitle = "";
            mVoiceBtn.setText(R.string.pref_box_voice_alert);
        }

        // set the button color
        // mBtnColor.setBackgroundColor(mColor);
        if(mColor != Color.TRANSPARENT)
            mBtnColor.setBackgroundColor(mColor);
    }

    protected void onDialogClosed(boolean positiveResult)
    {
        if(mValid)
        {
            String s = "";

            if(mPair != null)
            {
                s = String.format("%d,%d,%d", lowBand.getFrequency(), highBand.getFrequency(),mColor);
                if(!mVoice.isEmpty())
                    s = s + "," + mVoice;
            }
            /*
            // if one of the string is empty means we removed the setting
            if(!lowBand.getText().toString().isEmpty())
            {
                // Build the preference string
                s = Integer.valueOf(lowBand.getText().toString()) + "," + Integer.valueOf(highBand.getText().toString()) + "," + mColor;
                if(!mVoice.isEmpty())
                    s = s + "," + mVoice;
            }
            */
            SharedPreferences.Editor editor = getEditor();
            editor.putString(mPrefName, s);
            editor.commit();
            super.onDialogClosed(positiveResult);
            persistString(s);
        }
        else
            super.onDialogClosed(positiveResult);
    }
}
