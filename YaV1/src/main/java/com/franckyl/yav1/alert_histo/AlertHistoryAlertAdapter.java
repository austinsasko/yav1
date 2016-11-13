package com.franckyl.yav1.alert_histo;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;

/**
 * Created by franck on 2/3/14.
 */
public class AlertHistoryAlertAdapter extends BaseAdapter
{
    private Context     context;
    private TextView    mCounterView = null;
    private boolean     mCanLockout  = false;

    static class ViewHolder
    {
        public TextView  line1;
        public TextView  line2;
        public CheckBox  isCheck;
        public int       color;
    }

    public AlertHistoryAlertAdapter(Context context)
    {
        this.context = context;

        mCanLockout = false;

        // enable manual lockout from here

        if(YaV1.sExpert && YaV1.sAutoLockout != null)
            mCanLockout = true;
    }

    public void setCounterView(TextView txt)
    {
        mCounterView = txt;
    }

    private void refreshCounter()
    {
        if(mCounterView != null)
        {
            mCounterView.setText(AlertHistoryActivity.mAlertList.getNbSelected() + "/" + AlertHistoryActivity.mAlertList.size());
        }
    }

    @Override
    public int getCount()
    {
        return AlertHistoryActivity.mAlertList.size();
    }

    @Override
    public Object getItem(int position)
    {
        return AlertHistoryActivity.mAlertList.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    public void update()
    {
        notifyDataSetChanged();
    }

    public View getView(int position, View convertView, ViewGroup parent)
    {
        //LayoutInflater layoutInflater = LayoutInflater.from(context);
        ViewHolder viewHolder;

        // set value in TextView
        AlertHistory alert;
        try
        {
            alert = AlertHistoryActivity.mAlertList.get(position);
        }
        catch (IndexOutOfBoundsException e)
        {
            Log.d("Valentine lockout ", "YaV1Adapter position " + position + " exception " + e.toString());
            return convertView;
        }

        if(convertView == null)
        {
            // get layout from alert row
            LayoutInflater layoutInflater = LayoutInflater.from(context);
            convertView = layoutInflater.inflate(R.layout.alert_histo_alert_row, parent, false);

            viewHolder = new ViewHolder();
            viewHolder.line1    = (TextView) convertView.findViewById(R.id.time);
            viewHolder.line2    = (TextView) convertView.findViewById(R.id.first);
            viewHolder.isCheck  = (CheckBox) convertView.findViewById(R.id.check);
            // viewHolder.line2.setTextColor((viewHolder.color = alert.getTextColor()));
            viewHolder.line2.setTextColor((viewHolder.color = alert.mListColor));
            viewHolder.isCheck.setTag(alert);
            viewHolder.isCheck.setOnClickListener(mClickCheck);
            viewHolder.line2.setTag(new Integer(position));
            convertView.setTag(viewHolder);
        }
        else
        {
            viewHolder = (ViewHolder) convertView.getTag();
            viewHolder.isCheck.setTag(alert);
            viewHolder.line2.setTag(new Integer(position));
            //int c = alert.getTextColor();
            int c = alert.mListColor;
            if(c != viewHolder.color)
                viewHolder.line2.setTextColor((viewHolder.color = c));
        }

        // disable selection if no gps
        viewHolder.isCheck.setEnabled(alert.hasGps());
        // fill up the time in top

        viewHolder.line1.setText(alert.mId + ": " + alert.getStartTime());
        viewHolder.line2.setText(alert.getTitle());

        // check the box according to the check mark

        viewHolder.isCheck.setChecked(alert.isOption(AlertHistory.H_CHECK));

        //if(mCanLockout)
        //    convertView.setLongClickable(true);

        // recorded alert ..

        if(alert.isRecorded())
            convertView.setBackgroundColor(Color.GRAY);
        else
            convertView.setBackgroundColor(Color.TRANSPARENT);

        return convertView;
    }

    // check box click handler

    final View.OnClickListener mClickCheck = new View.OnClickListener()
    {
        public void onClick(final View v)
        {
            //Log.d("Valentine", "View clicked");
            AlertHistory lid = (AlertHistory) v.getTag();
            if( ((CheckBox) v).isChecked())
                lid.setOption(AlertHistory.H_CHECK);
            else
                lid.unsetOption(AlertHistory.H_CHECK);
            AlertHistoryActivity.mAlertList.incNbSelected(((CheckBox) v).isChecked() ? 1 : -1);
            refreshCounter();
        }
    };
}
