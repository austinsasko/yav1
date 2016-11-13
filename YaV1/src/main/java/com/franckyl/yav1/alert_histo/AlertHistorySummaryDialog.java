package com.franckyl.yav1.alert_histo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.YaV1Logger;
import com.franckyl.yav1.lockout.Lockout;
import com.franckyl.yav1.lockout.LockoutParam;
import com.franckyl.yav1.utils.DialogCollect;
import com.franckyl.yav1.utils.GMapUtils;

/**
 * Created by franck on 11/6/14.
 */
public class AlertHistorySummaryDialog implements View.OnClickListener
{
    private Activity     mContext;
    private AlertHistory mHistory;
    private AlertDialog  mAD;
    private Lockout      mLockout;
    private String       mFileName;
    private int          mFormat;
    private View         mCmdLayout;
    private View         mRecordLayout;

    public AlertHistorySummaryDialog(Activity context, AlertHistory al, String filename, int fmt)
    {
        mContext  = context;
        mHistory  = al;
        mLockout  = null;
        mFileName = filename;
        mFormat   = fmt;

        // se use the alert dialog build
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setTitle(mHistory.getDialogTitle())
                .setMessage(mHistory.getDialogMessage())
                .setNegativeButton(R.string.ok, null);

        // check if we need the implement the view for lockout management
        // we would if lockout are enabled
        // and the lockout id > 0 OR the frequency / band is lockout able
        // if the lockout id > 0, we read from  DB

        boolean needView =  (mHistory.mRecordOption == 0 && !(mHistory.get(0)).isLaser() && YaV1.sPrefs.getBoolean("data_collect", false));

        if(YaV1.sAutoLockout != null && mHistory.isOption(mHistory.H_LOCKOUT_ABLE))
        {
            // we can show the inner part
            if(mHistory.mLockoutId > 0)
            {
                // query the DB, show a progress Icon
                mLockout = YaV1.sAutoLockout.getLockoutForManagement(mHistory.mLockoutId);

                if (mLockout != null)
                {
                    needView = true;
                }
            }
        }

        mAD = builder.create();

        // Manage to add the view

        if(needView)
        {
            createLockoutPart();
        }
    }

    // show the dialog

    public void show()
    {
        mAD.show();
    }

    // create the Lockout part of the dialog

    private void createLockoutPart()
    {
        View v = mContext.getLayoutInflater().inflate(R.layout.alert_histo_summary_dialog, null, false);
        mAD.setView(v);
        View x = v.findViewById(R.id.lockout_info);
        if(mLockout != null)
        {
            x.setVisibility(View.VISIBLE);
            int signal = mLockout.getMaxSignal();

            TextView txt = (TextView) v.findViewById(R.id.lockout_id);
            txt.setText(String.format("Id: %d", mLockout.getId()));
            txt = (TextView) v.findViewById(R.id.lockout_status);
            txt.setText(mLockout.getStatusString());
            txt.setTextColor(mLockout.getStatusColor());
            txt = (TextView) v.findViewById(R.id.lockout_time);
            txt.setText(String.format("%s\n%s", GMapUtils.getShortDateString(mLockout.mTimeStamp), GMapUtils.getTimeString(mLockout.mTimeStamp)));
            txt = (TextView) v.findViewById(R.id.lockout_seen);
            txt.setText(String.format("Seen: %d", mLockout.mSeen));
            txt = (TextView) v.findViewById(R.id.lockout_missed);
            txt.setText(String.format("Miss: %d", mLockout.mMissed));
            txt = (TextView) v.findViewById(R.id.lockout_direction);
            txt.setText(String.format("B: %s", YaV1CurrentPosition.bearingToString(mLockout.mBearing)));
            txt = (TextView) v.findViewById(R.id.lockout_angle);
            txt.setText(String.format("A: %d °", mLockout.mParam2));
        }
        else
        {
            // remove the lockout info part
            x.setVisibility(View.GONE);
        }

        Button btn;

        mCmdLayout    = v.findViewById(R.id.cmd_lockout);
        mRecordLayout = v.findViewById(R.id.cmd_record);

        if(mLockout == null || !YaV1.sPrefs.getBoolean("logging_allow_lockout", false) || !mHistory.isOption(mHistory.H_LOCKOUT_ABLE))
        {
            mCmdLayout = v.findViewById(R.id.cmd_lockout);
            mCmdLayout.setVisibility(View.GONE);
        }
        else
        {
            // show the command part
            if (mLockout != null)
            {
                if ((mLockout.mFlag & (mLockout.LOCKOUT_WHITE | mLockout.LOCKOUT_MANUAL)) > 0 || mLockout.mSeen >= LockoutParam.mMinSeen) {
                    // we would handle the remove button
                    btn = (Button) v.findViewById(R.id.alert_remove);
                    if ((mLockout.mFlag & mLockout.LOCKOUT_WHITE) > 0) {
                        btn.setBackgroundResource(R.drawable.btn_default_normal_yellow);
                    } else {
                        btn.setBackgroundResource(R.drawable.btn_default_normal_lblue);
                        btn.setText(R.string.unlock);
                    }

                    btn.setOnClickListener(this);
                    btn = (Button) v.findViewById(R.id.alert_lockout);
                    btn.setVisibility(View.GONE);
                    btn = (Button) v.findViewById(R.id.alert_white);
                    btn.setVisibility(View.GONE);
                } else {
                    btn = (Button) v.findViewById(R.id.alert_remove);
                    btn.setOnClickListener(this);
                    btn.setText(R.string.unlearn);
                    btn = (Button) v.findViewById(R.id.alert_lockout);
                    btn.setOnClickListener(this);
                    btn = (Button) v.findViewById(R.id.alert_white);
                    btn.setOnClickListener(this);
                }
            } else {
                // we would create the lockout (we remove the remove button
                btn = (Button) v.findViewById(R.id.alert_remove);
                btn.setVisibility(View.GONE);
            }
        }

        if(mHistory.mRecordOption == 0 && !(mHistory.get(0)).isLaser() && YaV1.sPrefs.getBoolean("data_collect", false))
        {
            // we show the collector dialog and attach a dismiss listener
            mRecordLayout.setVisibility(View.VISIBLE);
            btn = (Button) v.findViewById(R.id.alert_record);
            btn.setOnClickListener(this);
        }
    }

    // we did click on a button

    public void onClick(View v)
    {
        int id = v.getId();
        boolean hideCmd = true;

        switch(id)
        {
            case R.id.alert_remove:
                YaV1.sAutoLockout.removeLockout(mLockout);
                break;
            case R.id.alert_lockout:
                YaV1.sAutoLockout.setManual(mLockout, false);
                break;
            case R.id.alert_white:
                YaV1.sAutoLockout.setManual(mLockout, true);
                break;
            case R.id.alert_record:
                // show the record dialog
                final LoggedAlert la = mHistory.get(0);
                DialogCollect mDlg = new DialogCollect(mContext, la, 0, false);
                mDlg.setOnDismissListener(new DialogInterface.OnDismissListener()
                {
                    @Override
                    public void onDismiss(DialogInterface dialog)
                    {
                        // check if we have mark the alert
                        if( (la.getProperty() & la.COLLECTED) > 0)
                        {
                            if(YaV1Logger.addLine(mFileName, mFormat, la))
                            {
                                // Toast
                            }
                            mRecordLayout.setVisibility(View.GONE);
                        }
                    }
                });
                mDlg.show();
                hideCmd = false;
                // attach the dismiss listener
                break;
            default:
                hideCmd = false;
        }

        if(hideCmd)
        {
            mCmdLayout.setVisibility(View.GONE);
        }
    }
}
