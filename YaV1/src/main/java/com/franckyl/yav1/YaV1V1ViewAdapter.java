package com.franckyl.yav1;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1lib.YaV1AlertList;

/**
 * Created by franck on 12/9/13.
 */
public class YaV1V1ViewAdapter extends BaseAdapter
{
    private Context         context;
    private YaV1AlertList   mList;
    public final static int MAXALERT     = 4;
    private int             mMaxAlert    = MAXALERT;
    private int             mOverlay     = 0;
    private LayoutInflater  mLayoutInflater;
    public  static int      freqColor    = 0;

    private static int      mRowSize[]   = {R.layout.v1_alert_row, R.layout.overlay_alert_row, R.layout.overlay_2_alert_row};

    static class ViewHolder
    {
        public TextView tvBand;
        public TextView tvFreq;
        public int      color;
        public int      freq_color;
    }

    public YaV1V1ViewAdapter(Context context, int overlay, YaV1AlertList alertList)
    {
        mList           = alertList;
        this.context    = context;
        mOverlay        = overlay;
        mLayoutInflater = LayoutInflater.from(context);
        if(mOverlay > 0)
            mMaxAlert = 3;
    }

    public void setOverlay(int overlay)
    {
        mOverlay = overlay;
    }

    // get the widest row size possible

    public int getWidestRowSize()
    {
        int m = 0;
        View view = null;
        FrameLayout fakeParent = new FrameLayout(context);
        view                   = getWideView(1, view, fakeParent);
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        m = view.getMeasuredWidth();
        return m;
    }

    @Override
    public int getCount()
    {
        return Math.min(mList.size(), mMaxAlert);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public Object getItem(int position)
    {
        return mList.get(position);
    }

    public View getWideView(int position, View convertView, ViewGroup parent)
    {
        convertView = mLayoutInflater.inflate(mRowSize[mOverlay], parent, false);
        TextView tvBand = (TextView) convertView.findViewById(R.id.BAND);
        TextView tvFreq = (TextView) convertView.findViewById(R.id.FREQUENCY);
        tvBand.setText("KA");
        tvFreq.setText("99.9999");
        return convertView;
    }

    public View getView(int position, View convertView, ViewGroup parent)
    {
        //LayoutInflater layoutInflater = LayoutInflater.from(context);
        ViewHolder viewHolder;

        // set value in TextView
        YaV1Alert alert;
        try
        {
            alert = mList.get(position);
        }
        catch (IndexOutOfBoundsException e)
        {
            Log.d("Valentine lockout ", "V1Adapter position " + position + " exception " + e.toString());
            return convertView;
        }

        if(convertView == null)
        {
            // get layout from alert row
            convertView = mLayoutInflater.inflate(mRowSize[mOverlay], parent, false);
            viewHolder = new ViewHolder();
            viewHolder.tvBand = (TextView) convertView.findViewById(R.id.BAND);
            viewHolder.tvFreq = (TextView) convertView.findViewById(R.id.FREQUENCY);
            viewHolder.color        = Color.TRANSPARENT;
            viewHolder.freq_color   = Color.TRANSPARENT;
            convertView.setTag(viewHolder);

            if(freqColor == 0)
                freqColor = viewHolder.tvFreq.getCurrentTextColor();
        }
        else
        {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        try
        {
            if(!alert.isLaser())
            {
                viewHolder.tvBand.setText(alert.getBandStr());
                viewHolder.tvFreq.setText(String.format("%.3f",  (alert.getFrequency() / 1000.0)));
                int color = alert.getColor();

                if(viewHolder.color != color)
                {
                    viewHolder.tvFreq.setBackgroundColor(color);
                    viewHolder.color = color;
                }

                color = alert.getTextColor();

                // check the frequency color
                if(color != Color.TRANSPARENT)
                {
                    viewHolder.tvFreq.setTextColor(color);
                    viewHolder.freq_color = color;
                }
                else if(viewHolder.freq_color != Color.TRANSPARENT)
                {
                    viewHolder.tvFreq.setTextColor(freqColor);
                    viewHolder.freq_color = Color.TRANSPARENT;
                }
            }
            else
            {
                viewHolder.tvBand.setText("");
                viewHolder.tvFreq.setText(alert.getBandStr());

                if(viewHolder.color != Color.TRANSPARENT)
                {
                    viewHolder.tvFreq.setBackgroundColor(Color.TRANSPARENT);
                    viewHolder.color = Color.TRANSPARENT;
                }
            }
        }
        catch (NullPointerException e)
        {
            Log.d("Valentine V1AlertAdapter", "NullPointerException: " + e.toString());
        }

        return convertView;
    }
}
