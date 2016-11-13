package com.franckyl.yav1.utils;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.widget.EditText;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;

/**
 * Created by franck on 8/17/14.
 */
public class EditTextFrequency
{
    private EditText mFrequency;
    private int      mBand;
    private Context  mContext;
    private boolean  mLow;

    public EditTextFrequency(Context context, EditText ed, int band, boolean low)
    {
        mFrequency = ed;
        mBand      = band;
        mContext   = context;
        mLow       = low;
        // create a Text Watcher for it
        mFrequency.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s)
            {
                // if the value is not correct we make a red border
                if(!s.toString().isEmpty() && !YaV1.BandBoundaries.checkValid(mBand, Integer.valueOf(s.toString())))
                    mFrequency.setError(mContext.getString( (mLow ? R.string.band_range_too_low : R.string.band_range_too_high)));
                else
                    mFrequency.setError(null);
            }
        });
    }

    // set the frequency

    public void setFrequency(int freq)
    {
        if(freq == 0)
            mFrequency.setText("");
        else
            mFrequency.setText(new Integer(freq).toString());
    }

    // check if valid

    public boolean isValid()
    {
        String s = mFrequency.getText().toString();
        if(s.isEmpty() || !YaV1.BandBoundaries.checkValid(mBand, Integer.valueOf(s)))
        {
            mFrequency.setError(mContext.getString( (mLow ? R.string.band_range_too_low : R.string.band_range_too_high)));
            return false;
        }

        return true;
    }

    // set the error

    public void setError(CharSequence s)
    {
        mFrequency.setError(s);
    }

    public void setError(int resId)
    {
        mFrequency.setError(mContext.getString(resId));
    }

    // get the Frequency stored

    public int getFrequency()
    {
        return (isValid() ? Integer.valueOf(mFrequency.getText().toString()) : 0);
    }

    // return the pair or null if not valid

    public static Pair<Integer, Integer> finalCheck(EditTextFrequency lower, EditTextFrequency upper)
    {
        Pair <Integer, Integer> p = null;
        int low  = lower.getFrequency();
        int high = upper.getFrequency();

        if(low != 0 && high != 0)
        {
            if(low >= high)
            {
                lower.setError(R.string.band_range_low_too_high);
                upper.setError(R.string.band_range_high_too_low);
            }
            else
                p = new Pair(low, high);
        }

        return p;
    }
}
