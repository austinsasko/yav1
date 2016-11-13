package com.franckyl.yav1;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

/**
 * Created by franck on 9/17/13.
 */
public class YaV1SoundList extends ListPreference
{
    int mSelectedIndex  = 0;
    int mCurrentStream  = 0;
    int mCurrentRate    = 0;
    int mSoundId        = 0;

    SoundPool             mSoundPool   = null;
    //private final Handler mHandler     = new Handler();
    boolean               mVolumeSignal = false;
    //int                   mDelayPlay[]  = {0, 2000, 1500, 1000, 900, 700, 500, 300, 200};

    public YaV1SoundList(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    @Override
    protected void onPrepareDialogBuilder(android.app.AlertDialog.Builder builder)
    {
        CharSequence[] values = this.getEntries();

        mSelectedIndex = this.findIndexOfValue(this.getValue());

        builder.setSingleChoiceItems(values, mSelectedIndex, mClickListener)
               .setPositiveButton(android.R.string.ok, mClickListener)
               .setNegativeButton(android.R.string.cancel, mClickListener);
    };

    // when the load is completed

    SoundPool.OnLoadCompleteListener mListener = new SoundPool.OnLoadCompleteListener()
    {
        @Override
        public void onLoadComplete(SoundPool soundPool, int i, int i2)
        {
            if(mSoundId > 0)
            {
                // short sound, just playing twice
                mCurrentStream = mSoundPool.play(mSoundId, 1.0f, 1.0f, 1, 1, 1.0f);
            }
        }
    };

    // check the value selected

    protected void onChoiceClick(int index)
    {
        if(index > 0)
        {
            if(mSoundPool != null)
                mSoundPool.release();
            // depends if long or short, we load the stream
            mSoundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 100);
            mSoundId   = mSoundPool.load(YaV1.sContext, SoundParam.sRefSound[index-1], 1);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(YaV1.sContext);
            // if short we just play the sound once at full volume and current rate
            mVolumeSignal  = pref.getBoolean("volume_signal", false);
            mSoundPool.setOnLoadCompleteListener(mListener);
        }
    }

    // our click handler

    DialogInterface.OnClickListener mClickListener = new DialogInterface.OnClickListener()
    {
        public void onClick(DialogInterface dialog, int which)
        {
            // disable the current stream
            if(mSoundPool != null)
                mSoundPool.stop(mCurrentStream);

            mCurrentRate   = 0;
            mCurrentStream = 0;
            mSoundId       = 0;

            if(which >= 0)//if which is zero or greater, one of the options was clicked
            {
                String clickedValue = (String) YaV1SoundList.this.getEntryValues()[which];
                 //get the value                mSelectedIndex = which;//update current selected index
                onChoiceClick(which);
                mSelectedIndex = which;
            }
            else
            {
                if (which == DialogInterface.BUTTON_POSITIVE) //if the positive button was pressed, persist the value.
                {
                    YaV1SoundList.this.setValueIndex(mSelectedIndex);
                    YaV1SoundList.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                }

                dialog.dismiss(); //close the dialog
            }
        }
    };

    // handle the onDismiss to avoid dimiss on neutral button

    protected void onDialogClosed(boolean positiveResult)
    {
        if(mSoundPool != null)
            mSoundPool.release();
    }
}
