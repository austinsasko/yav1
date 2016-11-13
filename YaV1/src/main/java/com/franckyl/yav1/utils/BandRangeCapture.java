package com.franckyl.yav1.utils;

import android.app.Dialog;
import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;

/**
 * Created by franck on 8/14/14.
 */
public class BandRangeCapture extends Dialog implements View.OnClickListener
{
    public Pair<Integer, Integer> mRange;
    public int                    mBand;
    public int                    mId;
    public Context                mContext;
    public boolean                misValid = false;

    private EditTextFrequency     mLower;
    private EditTextFrequency     mUpper;

    // constructor

    public BandRangeCapture(final Context context, Pair<Integer, Integer> range, int band, int position)
    {
        super(context);
        setContentView(R.layout.bande_range_capture);

        mId         = position;
        mRange      = range;
        mContext    = context;
        misValid    = false;
        mBand       = band;

        // set the title
        Pair<Integer, Integer> edges = YaV1.BandBoundaries.getEdges(mBand);
        setTitle(YaV1.BandBoundaries.getBandStrFromInt(mBand).toUpperCase() + String.format(" Band  (%d - %d)", edges.first, edges.second));
        setCancelable(false);

        // get the EditText for edges

        mLower = new EditTextFrequency(mContext, (EditText) findViewById(R.id.lower), mBand, true);
        mUpper = new EditTextFrequency(mContext, (EditText) findViewById(R.id.upper), mBand, false);

        // we can set the values (if we have them)
        Button b = (Button) findViewById(R.id.remove);

        if(mRange == null)
        {
            mRange = new Pair(0, 0);
            b.setVisibility(View.GONE);
            b.setOnClickListener(null);
        }
        else
        {
            b.setVisibility(View.VISIBLE);
            b.setOnClickListener(this);
        }

        b = (Button) findViewById(R.id.ok);
        b.setOnClickListener(this);

        b = (Button) findViewById(R.id.cancel);
        b.setOnClickListener(this);

        mLower.setFrequency(mRange.first);
        mUpper.setFrequency(mRange.second);
    }

    // click on the button

    public void onClick(View v)
    {
        int id = v.getId();

        switch(id)
        {
            case R.id.cancel:
                misValid = false;
                handleDismiss(false);
                break;

            case R.id.ok:
                Pair <Integer, Integer> p = mLower.finalCheck(mLower, mUpper);

                if(p != null)
                {
                    misValid = true;
                    mRange   = p;
                    handleDismiss(true);
                }
                break;

            case R.id.remove:
                misValid = true;
                mRange = null;
                handleDismiss(true);
                break;
        }
    }

    // this function can ve over written

    public void handleDismiss(boolean isOk)
    {
        // default dismiss the dialog
        dismiss();
    }

}
