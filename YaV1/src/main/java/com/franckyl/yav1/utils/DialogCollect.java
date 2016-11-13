package com.franckyl.yav1.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1AlertProcessor;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.alert_histo.LoggedAlert;
import com.franckyl.yav1lib.YaV1Alert;

/**
 * Created by franck on 1/18/14.
 */
public class DialogCollect extends Dialog implements View.OnClickListener
{
    private YaV1Alert      mAlert;
    private boolean        mLoggedAlert;
    private YaV1GpsPos     mPos;
    private int            mNbClick = 0;
    private static int     mBlack   = Color.parseColor("#FFFFFF");
    private static int     mRed     = Color.parseColor("#ff0000");
    private boolean        mInDemo;

    private int mPosition  = 0;

    public DialogCollect(final Context context, Object alert, int pos, boolean inDemo)
    {
        super(context);
        // got alert and position

        mAlert       = (YaV1Alert) alert;
        mPos         = YaV1CurrentPosition.getPos();
        mPosition    = pos;
        mInDemo      = false;
        mLoggedAlert = false;
        if(alert.getClass() == LoggedAlert.class)
            mLoggedAlert = true;

        // no title

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // transparent background

        setContentView(R.layout.data_collect);
        if(!mLoggedAlert)
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        ( (TextView) findViewById(R.id.title)).setText(mAlert.getBandStr() + " " + mAlert.getFrequency());

        Button b = (Button) findViewById(R.id.ok);
        b.setOnClickListener(this);
        b.setEnabled(false);

        b = (Button) findViewById(R.id.cancel);
        b.setOnClickListener(this);

        b = (Button) findViewById(R.id.alert_moving);
            //b.setBackgroundColor(cF);
        b.setOnClickListener(this);

        b = (Button) findViewById(R.id.alert_static);
            //b.setBackgroundColor(cT);
        b.setOnClickListener(this);

        b = (Button) findViewById(R.id.alert_true);
        b.setOnClickListener(this);
            //b.setBackgroundColor(cT);

        b = (Button) findViewById(R.id.alert_false);
        b.setOnClickListener(this);
            //b.setBackgroundColor(cF);

        b = (Button) findViewById(R.id.alert_io);
        b.setEnabled(false);
        b.setOnClickListener(this);
    }

    public int getPosition()
    {
        return mPosition;
    }

    // the click listener

    public void onClick(View v)
    {
        int i = v.getId();
        boolean b;
        Button  bt;

        switch(i)
        {
            case R.id.ok:
                // log the alert and dismiss
                if(mNbClick > 0 && !mInDemo && !mLoggedAlert)
                    YaV1.sLog.log(mPos, mAlert);
                dismiss();
                break;
            case R.id.cancel:
                dismiss();
                break;
            case R.id.alert_moving:
                mAlert.setMoving(true);
                ( (Button) v).setTextColor(mRed);
                ( (Button) this.findViewById(R.id.alert_static)).setTextColor(mBlack);
                mNbClick++;
                break;
            case R.id.alert_static:
                mAlert.setMoving(false);
                ( (Button) v).setTextColor(mRed);
                ( (Button) this.findViewById(R.id.alert_moving)).setTextColor(mBlack);
                mNbClick++;
                break;
            case R.id.alert_true:
                mAlert.setTrue(true);
                ( (Button) v).setTextColor(mRed);
                ( (Button) this.findViewById(R.id.alert_false)).setTextColor(mBlack);
                bt = (Button) this.findViewById(R.id.alert_io);
                bt.setEnabled(true);
                bt.setTextColor(mBlack);
                mNbClick++;
                break;
            case R.id.alert_io:
                b = mAlert.isIO();
                b = !b;
                mAlert.setIO(b);
                ( (Button) v).setTextColor( (b ? mRed : mBlack));
                break;
            case R.id.alert_false:
                mAlert.setTrue(false);
                ( (Button) v).setTextColor(mRed);
                ( (Button) this.findViewById(R.id.alert_true)).setTextColor(mBlack);
                bt = (Button) this.findViewById(R.id.alert_io);
                bt.setEnabled(false);
                bt.setTextColor(mBlack);
                mNbClick++;
                break;
            default:
                return;
        }

        // enable the record button

        if(mNbClick > 0)
            ( (Button) this.findViewById(R.id.ok)).setEnabled(true);
    }
}
