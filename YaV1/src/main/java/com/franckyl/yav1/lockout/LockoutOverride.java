package com.franckyl.yav1.lockout;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1AlertService;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.YaV1ScreenActivity;
import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1lib.YaV1AlertList;

import java.util.concurrent.ExecutionException;

/**
 * Created by franck on 8/19/14.
 */
public class LockoutOverride
{
    private YaV1AlertList mAlertList            = new YaV1AlertList();
    private LockoutDialog mDlg                  = null;

    // receives the call for showing the dialog on the UI Thread

    private BroadcastReceiver mOverrideReceiver = null;

    // Context (the main app)

    private Activity mActivity;
    private Activity mOriginalActivity;
    private boolean  mFromAlertView;
    private boolean  mFromDemo;
    // constructor

    public LockoutOverride()
    {
        mActivity = null;
        mOverrideReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(final Context context, Intent intent)
            {
                mFromDemo = YaV1AlertService.mDemo;

                if(mActivity != null && mAlertList.size() > 0)
                {
                    mFromAlertView = mActivity.getLocalClassName().toString().equals("YaV1ScreenActivity");

                    if(mFromAlertView)
                    {
                        mOriginalActivity = null;
                        mActivity.runOnUiThread(new Runnable()
                        {
                            public void run()
                            {
                                mDlg = new LockoutDialog(mActivity, mAlertList, mFromDemo);
                                mDlg.setOnDismissListener(mDismiss);
                                mDlg.show();
                            }
                        });
                    }
                    else
                    {
                        mOriginalActivity = mActivity;
                        mActivity         = null;
                        Intent nIntent = new Intent(context, YaV1ScreenActivity.class);
                        nIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                        // IMPORTANT remove this
                        if(mFromDemo)
                            nIntent.putExtra("demoMode", true);

                        nIntent.putExtra("override", true);
                        context.startActivity(nIntent);
                    }
                }
            }
        };

        // register the receiver
        LocalBroadcastManager.getInstance(YaV1.sContext).registerReceiver(mOverrideReceiver, new IntentFilter("YaV1AlertOverride"));
    }

    // the dismiss listener

    DialogInterface.OnDismissListener mDismiss = new DialogInterface.OnDismissListener()
    {
        @Override
        public void onDismiss(DialogInterface dialog)
        {
            if(!mFromAlertView)
                mActivity.moveTaskToBack(true);
            // we reset the in override flag
            YaV1AlertService.mInOverride = false;
            mDlg              = null;
            mActivity         = null;
            mOriginalActivity = null;
        }
    };

    // in case we timeout on V1, we clean everything

    public void resetOnTimeOut()
    {
        if(mDlg != null)
        {
            mDlg.setOnDismissListener(null);
            mDlg.dismiss();
        }

        mActivity         = null;
        mOriginalActivity = null;
        mDlg              = null;
    }

    // re assign the activity owner and show the dialog

    public void resetActivityOwner(Activity st)
    {
        if(mOriginalActivity != null && mActivity == null)
        {
            mActivity = st;
            mActivity.runOnUiThread(new Runnable()
            {
                public void run()
                {
                    mDlg = new LockoutDialog(mActivity, mAlertList, mFromDemo);
                    mDlg.setOnDismissListener(mDismiss);
                    mDlg.show();
                }
            });
        }
    }

    // use to set the activity

    public void setActivity(Activity act)
    {
        mActivity = act;
    }

    // remove our receiver

    public void cleanup(boolean finish)
    {
        if(finish && mOverrideReceiver != null)
            LocalBroadcastManager.getInstance(YaV1.sContext).unregisterReceiver(mOverrideReceiver);
        resetOnTimeOut();
    }

    // copy the incoming alert list to work on

    public boolean initList(YaV1AlertList iList)
    {
        mAlertList.clear();
        // copy the alerts that can be override

        for(YaV1Alert a: iList)
        {
            //if(a.getPersistentId() > 0)
            if(a.getPersistentId() != 0 || (YaV1CurrentPosition.sCollector && !a.isLaser()))
                mAlertList.add(a);
        }

        return mAlertList.size() > 0;
    }

    // get the lisview

    public YaV1AlertList getAlertList()
    {
        return mAlertList;
    }
}
