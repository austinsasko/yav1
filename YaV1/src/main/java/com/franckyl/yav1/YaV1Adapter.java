package com.franckyl.yav1;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.franckyl.yav1.utils.DialogCollect;
import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1lib.YaV1AlertList;

public class YaV1Adapter extends BaseAdapter
{
    private Context         context;
    private YaV1AlertList   mList;
    private static int      prioColor    =  Color.parseColor("#ffa408");
    private static int      oriColor     = 0;
    private static int      freqColor    = 0;
    private static float    sFontSize    = -1f;
    private LayoutInflater  mLayoutInflater;

    // our static class for Holding

    static class ViewHolder
    {
        public TextView  tvBand;
        public TextView  tvFreq;
        public ImageView ivSign;
        public int       color;
        public int       freq_color;
    }

    public YaV1Adapter(Context context, YaV1AlertList alertList)
    {
        this.context    = context;
        mList           = alertList;
        mLayoutInflater = LayoutInflater.from(context);
        sFontSize       = -1;
    }

    @Override
    public int getCount()
    {
        return mList.size();
    }

    @Override
    public Object getItem(int position)
    {
        return mList.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        ViewHolder viewHolder;
        boolean    outBound = false;

        // set value in TextView
        YaV1Alert alert = null;
        try
        {
            alert = mList.get(position);
            if(alert == null)
            {
                YaV1.DbgLog("Alert is null from the view");
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            Log.d("Valentine lockout ", "YaV1Adapter position " + position + " exception " + e.toString());
            outBound = true;
        }

        // retrieve the original color
        // generate convert view if null, otherwise reuse it
        if(convertView == null)
        {
            convertView = mLayoutInflater.inflate(R.layout.alert_row, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.tvBand     = (TextView) convertView.findViewById(R.id.BAND);
            viewHolder.tvFreq     = (TextView) convertView.findViewById(R.id.FREQUENCY);
            viewHolder.ivSign     = (ImageView) convertView.findViewById(R.id.SIGNAL);
            viewHolder.color      = Color.TRANSPARENT;
            viewHolder.freq_color = Color.TRANSPARENT;

            // we keep the original text color
            if(oriColor == 0)
            {
                oriColor  = viewHolder.tvBand.getCurrentTextColor();
                freqColor = viewHolder.tvFreq.getCurrentTextColor();
            }
            // check the font size, if change is needed
            if(sFontSize < 0)
            {
                if(YaV1.sFontFrequencyRatio > 0 )
                {
                    float f = viewHolder.tvFreq.getTextSize();
                    // compute new font size
                    sFontSize = Math.round(f * YaV1.sFontFrequencyRatio);
                }
                else
                    sFontSize = 0;
            }

            // check the font to change
            if(sFontSize > 0)
                viewHolder.tvFreq.setTextSize(TypedValue.COMPLEX_UNIT_PX, sFontSize);
            convertView.setTag(viewHolder);
        }
        else
        {
            viewHolder = (ViewHolder) convertView.getTag();
            viewHolder.tvBand.setVisibility(View.VISIBLE);
            viewHolder.tvBand.setTextColor(oriColor);
        }

        // it happen sometimes
        if(alert == null || outBound)
        {
            return convertView;
        }


        try
        {
            if(!alert.isLaser())
            {
                viewHolder.tvBand.setText(alert.getBandStr());
                final int flag = alert.getProperty();

                if( (flag & alert.PROP_PRIORITY) > 0) //alert.isPriority())
                    viewHolder.tvBand.setTextColor(prioColor);

                viewHolder.tvFreq.setText(String.format("%.3f",  (alert.getFrequency() / 1000.0)));

                int color = alert.getColor();
                // set background
                if(color != viewHolder.color)
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
                // laser do not have band, we remove and set frequency with the laser string (always priority)
                viewHolder.tvBand.setVisibility(View.GONE);
                viewHolder.tvFreq.setText(alert.getBandStr());
                viewHolder.tvFreq.setTextColor(prioColor);
                viewHolder.freq_color = prioColor;
                
                if(viewHolder.color != Color.TRANSPARENT)
                {
                    viewHolder.tvFreq.setBackgroundColor(Color.TRANSPARENT);
                    viewHolder.color = Color.TRANSPARENT;
                }
            }

            viewHolder.ivSign.setImageResource(alert.getDirSignal());
        }
        catch (NullPointerException e)
        {
            Log.d("Valentine AlertAdapter", "NullPointerException: " + e.toString());
        }

        return convertView;
    }
}
