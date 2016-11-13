package com.franckyl.yav1;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Parcelable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ImageView;

public class YaV1SweepSetAdapter extends BaseAdapter
{
    private Context       context;

    public YaV1SweepSetAdapter(Context context)
    {
        this.context = context;
    }

    @Override
    public int getCount()
    {
        return YaV1.sSweep.size();
    }

    @Override
    public Object getItem(int position)
    {
        return YaV1.sSweep.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        YaV1Sweep sweep;

        if(convertView == null)
        {
            // get layout from sweep set row
            convertView = layoutInflater.inflate(R.layout.sweepset_row, parent, false);
        }

        // set value in TextView
        try
        {
            sweep = YaV1.sSweep.get(position);
        }
        catch (IndexOutOfBoundsException e)
        {
            return convertView;
        }

        TextView  tvName = (TextView) convertView.findViewById(R.id.sweep_name);
        // get the button
        ImageButton    btnEdit = (ImageButton) convertView.findViewById(R.id.edit);
        ImageButton    btnDel  = (ImageButton) convertView.findViewById(R.id.delete);
        ImageButton    btnPush = (ImageButton) convertView.findViewById(R.id.push);

        try
        {
            // we enabled all button (can have been reused view)
            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setEnabled(true);
            btnDel.setVisibility(View.VISIBLE);
            btnDel.setEnabled(true);
            btnPush.setVisibility(View.VISIBLE);
            btnPush.setEnabled(true);
            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setEnabled(true);

            // we can set the property
            boolean isDefault = sweep.isFactoryDefault();
            boolean isEmpty   = (isDefault ? false : (sweep.countNonEmpty() > 0 ? false : true));
            boolean isCurrent = false;
            boolean isLast    = false;

            if(isDefault)
                tvName.setText(R.string.sweep_factory);
            else
                tvName.setText(sweep.getName());

            if(YaV1.sSweep.hasCurrent() && YaV1.customPossible())
            {
                if(YaV1.sSweep.getCurrentId() == sweep.getId())
                    isCurrent = true;
            }
            else
            {
                if(YaV1.sSweep.getLastKnown() == sweep.getId())
                    isLast = true;
            }

            // we can set the color if empty / current / lastKnown
            if(isCurrent)
                convertView.setBackgroundColor(Color.parseColor("#3776ff"));
            else if (isLast)
                convertView.setBackgroundColor(Color.parseColor("#40570d"));
            else if(isEmpty)
                convertView.setBackgroundColor(Color.parseColor("#ff9b32"));
            else
                convertView.setBackgroundColor(YaV1SweepSetActivity.DEFAULT_COLOR);

            Log.d("Valentine", "Sweep Id:" + sweep.getId() + " Empty " + isEmpty + " Last " + isLast + " Current" + isCurrent + " Default: " + isDefault);

            // we get the button to adjust
            if(sweep.isFactoryDefault() || isCurrent)
            {
                btnDel.setEnabled(false);
                btnDel.setVisibility(View.GONE);
            }
            else
            {
                btnDel.setEnabled(true);
                btnDel.setVisibility(View.VISIBLE);
            }

            // if empty, or not Euro mode, or V1 not connected, or current we can't push
            if(isEmpty || !YaV1.customPossible() || isCurrent || !YaV1AlertService.isActive())
            {
                btnPush.setEnabled(false);
                btnPush.setVisibility(View.GONE);
                //Log.d("Valentine", "Not pushable");
            }
            else
            {
                //Log.d("Valentine", "Pushable");
                btnPush.setEnabled(true);
                btnPush.setVisibility(View.VISIBLE);
            }
        }
        catch (NullPointerException e)
        {
            Log.d("YaV1_SeepSetAdapter", "NullPointerException");
        }

        return convertView;
    }
}
