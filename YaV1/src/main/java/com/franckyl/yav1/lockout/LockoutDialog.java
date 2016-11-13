package com.franckyl.yav1.lockout;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1AlertProcessor;
import com.franckyl.yav1.YaV1AlertService;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.utils.DialogCollect;
import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1lib.YaV1AlertList;

/**
 * Created by franck on 8/19/14.
 */
public class LockoutDialog extends Dialog implements View.OnClickListener
{
    private Context                 mContext;
    private YaV1AlertList           mAlertList;
    private lockoutDialogAdapter    mAdapter;
    private LayoutInflater          mInflater;
    private ListView                mListView;
    private static int              mFreqWidth = 0;
    private DialogCollect           mDlg       = null;
    private boolean                 mInDemo;

    static class V_Holder
    {
        View     frequ;
        Button   lockout;
        Button   white;
        int      color;
    }

    // create our dialog as an activity

    public LockoutDialog(final Context context, YaV1AlertList l, boolean inDemo)
    {
        super(context);

        mContext   = context;
        mAlertList = l;

        mInDemo = inDemo;

        // no title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // get the layout
        setContentView(R.layout.lockout_dialog);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

        mListView = (ListView) findViewById(R.id.listView);
        mInflater = LayoutInflater.from(mContext);
        mAdapter  = new lockoutDialogAdapter(this);
        mListView.setAdapter(mAdapter);

        ( (Button) findViewById(R.id.cancel)).setOnClickListener(this);

        if(mFreqWidth == 0)
        {
            mFreqWidth = YaV1.measureItemWidth(mContext, R.layout.fake_measure_text_band_freq, R.id.frequency, 1.02f);
        }
    }

    // we remove the element at position, close the dialog if list is empty
    private void removeAlert(int position)
    {
        mAlertList.remove(position);

        if(mAlertList.size() == 0)
        {
            dismiss();
            return;
        }
        // we will redraw our list
        mAdapter.notifyDataSetChanged();
    }

    public void onClick(View v)
    {
        int id = v.getId();

        if(id == R.id.cancel)
        {
            dismiss();
            return;
        }

        final int position = mListView.getPositionForView((View) v.getParent());

        YaV1Alert a = mAlertList.get(position);

        // get the tag to know the position

        switch(id)
        {
            case R.id.frequency:
                mDlg = new DialogCollect(mContext, a, position, mInDemo);
                mDlg.setOnDismissListener(mDismissCollect);
                mDlg.show();
                break;
            case R.id.alert_lockout:
                processManual(a, position, YaV1AlertProcessor.MANUAL_OPTION__NONE);
                break;
            case R.id.alert_white:
                processManual(a, position, YaV1AlertProcessor.MANUAL_OPTION__WHITE);
                break;
        }
    }

    // long click to remove a in learning lockout

    View.OnLongClickListener mLongClick = new View.OnLongClickListener()
    {
        @Override
        public boolean onLongClick(View v)
        {
            final int position = mListView.getPositionForView((View) v.getParent());
            int id = v.getId();
            if(id == R.id.frequency)
                id = 1;
            else
                id = 2;

            //Log.d("Valentine Lockout", "LongClick on Position " + position);

            YaV1Alert a = mAlertList.get(position);
            if(a.getPersistentId() > 0)
                processManual(a, position, YaV1AlertProcessor.MANUAL_OPTION_REMOVE_LEARNING);
            return true;
        }
    };

    // set the manual to the processor

    private void processManual(final YaV1Alert a, final int position, int option)
    {
        YaV1AlertService.mProcessor.setManualLockUnlock(a.getPersistentId(), option);
        final ProgressDialog pDlg = new ProgressDialog(mContext);
        pDlg.setIndeterminate(true);
        pDlg.setCancelable(false);
        pDlg.setMessage(mContext.getString(R.string.processing));
        pDlg.setOnDismissListener(new OnDismissListener()
        {
            @Override
            public void onDismiss(DialogInterface dialog)
            {
                // we can set or unset depends of the id > 0 or < 0
                if(!YaV1CurrentPosition.sCollector)
                    removeAlert(position);
                else
                    mAdapter.notifyDataSetChanged();
            }
        });
        pDlg.show();
        a.setPersistentId(0);

        new Thread()
        {
            public void run()
            {
                do
                {
                    try
                    {
                        sleep(200);
                    }
                    catch(InterruptedException e)
                    {
                        break;
                    }
                }while(!YaV1AlertService.mProcessor.isManualAvailable());
                pDlg.dismiss();
            }
        }.start();
    }

    DialogInterface.OnDismissListener mDismissCollect = new DialogInterface.OnDismissListener()
    {
        @Override
        public void onDismiss(DialogInterface dialog)
        {
            if(mDlg != null)
            {
                removeAlert(mDlg.getPosition());
                mDlg = null;
            }
        }
    };

    // our Adapter

    private class lockoutDialogAdapter extends BaseAdapter
    {
        View.OnClickListener mClick;

        public lockoutDialogAdapter(View.OnClickListener iClick)
        {
            mClick = iClick;
        }

        public int getCount()
        {
            return mAlertList.size();
        }

        public View getView(int position, View convertView, ViewGroup parent)
        {
            // we use the either collector or Lockout/White layout
            V_Holder holder;

            if(convertView == null)
            {
                convertView = mInflater.inflate((YaV1CurrentPosition.sCollector ? R.layout.lockout_dialog_row_collect: R.layout.lockout_dialog_row), null);
                holder = new V_Holder();

                holder.frequ   = convertView.findViewById(R.id.frequency);
                holder.frequ.getLayoutParams().width = mFreqWidth;

                holder.lockout = (Button) convertView.findViewById(R.id.alert_lockout);
                holder.white   = (Button) convertView.findViewById(R.id.alert_white);

                holder.lockout.setOnClickListener(mClick);
                holder.white.setOnClickListener(mClick);

                holder.lockout.setOnLongClickListener(mLongClick);
                holder.white.setOnLongClickListener(mLongClick);
                holder.color = Color.TRANSPARENT;

                //holder.lockout.setClickable(false);

                if(YaV1CurrentPosition.sCollector)
                    ( (Button) holder.frequ).setOnClickListener(mClick);

                convertView.setTag(holder);
            }
            else
                holder = (V_Holder) convertView.getTag();

            YaV1Alert a = (YaV1Alert) getItem(position);

            int lLid = a.getPersistentId();
            int prop = a.getProperty();
            int color = Color.TRANSPARENT;

            if(!YaV1CurrentPosition.sCollector)
                ((TextView) holder.frequ).setText(String.format("%s %d", a.getBandStr(), a.getFrequency()));
            else
                ( (Button) holder.frequ).setText(String.format("%s %d", a.getBandStr(), a.getFrequency()));

            // case 0 (nothing to do, except collection)

            if(lLid == 0)
            {
                holder.white.setVisibility(View.GONE);
                holder.lockout.setVisibility(View.GONE);
                holder.lockout.setOnLongClickListener(null);
            }
            else if(lLid > 0)
            {
                holder.lockout.setVisibility(View.VISIBLE);
                holder.lockout.setText(R.string.lockout);
                holder.white.setVisibility(View.VISIBLE);
                holder.white.setText(R.string.white_short);
                holder.lockout.setOnLongClickListener(mLongClick);
                color = 0X883776ff;
            }
            else
            {
                holder.lockout.setOnLongClickListener(null);
                // remove
                if( (prop & a.PROP_LOCKOUT) > 0)
                {
                    holder.white.setVisibility(View.GONE);
                    holder.lockout.setVisibility(View.VISIBLE);
                    holder.lockout.setText(R.string.unlock);
                }
                else
                {
                    holder.lockout.setVisibility(View.GONE);
                    holder.white.setVisibility(View.VISIBLE);
                    holder.white.setText(R.string.remove);
                }
            }

            if(holder.color != color)
            {
                convertView.setBackgroundColor((holder.color = color));
            }

            return convertView;
        }

        @Override
        public Object getItem(int position)
        {
            try
            {
                return mAlertList.get(position);
            }
            catch(IndexOutOfBoundsException e)
            {
                Log.d("Valentine", "Lockout Dialog Adapter Exception " + e.toString());
                return null;
            }
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }
    }
}
