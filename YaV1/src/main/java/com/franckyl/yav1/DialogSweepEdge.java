package com.franckyl.yav1;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.valentine.esp.data.SweepDefinition;

/**
 * Created by franck on 8/3/13.
 */
public class DialogSweepEdge extends Dialog
{
    private SweepDefinition mSwpDef;
    private boolean         mIsValid = false;
    private EditText        mLower;
    private EditText        mUpper;

    public DialogSweepEdge(final Context context, SweepDefinition iDef)
    {
        super(context);
        mSwpDef = iDef;
        setContentView(R.layout.sweep_dialog);

        setTitle(R.string.sweep_edges);
        // get the textView for Index
        TextView tIndex = (TextView) findViewById(R.id.sweepIndex);
        tIndex.setText(YaV1.sSweep.getSectionString());

        // get the EditText for edges
        mLower = (EditText) findViewById(R.id.lower);
        mUpper = (EditText) findViewById(R.id.upper);

        // set the lower and upper edge
        int lower = mSwpDef.getLowerFrequencyEdge();
        int upper = mSwpDef.getUpperFrequencyEdge();

        mLower.setText( (lower == 0 ? "" : mSwpDef.getLowerFrequencyEdge().toString()));
        mUpper.setText( (upper == 0 ? "" : mSwpDef.getUpperFrequencyEdge().toString()));

        // implement the listener
        Button bReset   = (Button) findViewById(R.id.reset);
        Button bOk      = (Button) findViewById(R.id.ok);
        Button bCancel  = (Button) findViewById(R.id.cancel);

        // reset we get a valid

        bReset.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                mSwpDef.setLowerFrequencyEdge(0);
                mSwpDef.setUpperFrequencyEdge(0);
                mIsValid = true;
                // call the myDismiss
                handleDismiss(true);
            }
        });

        bCancel.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                handleDismiss(false);
            }
        });

        bOk.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                // we have to verify the value before dismiss
                int low   = 0;
                int upper = 0;

                if(mLower.getText().toString().trim().length() > 0)
                    low = Integer.valueOf(mLower.getText().toString().trim());

                if(mUpper.getText().toString().trim().length() > 0)
                    upper = Integer.valueOf(mUpper.getText().toString().trim());

                if(upper == 0 || low == 0 || upper < low || !YaV1.sSweep.checkSweep(low, upper))
                {
                    // we set the color in red
                    mLower.setTextColor(Color.parseColor("#FF0000"));
                    mUpper.setTextColor(Color.parseColor("#FF0000"));
                }
                else
                {
                    mSwpDef.setLowerFrequencyEdge(low);
                    mSwpDef.setUpperFrequencyEdge(upper);
                    mIsValid = true;
                    handleDismiss(true);
                }
            }
        });
    }

    // this function can ve over written
    public void handleDismiss(boolean isOk)
    {
        // default dismiss the dialog
        dismiss();
    }

    public SweepDefinition getSweepDefinition()
    {
        return mSwpDef;
    }
}
