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

public class YaV1SettingSetAdapter extends BaseAdapter
{
    private Context       context;

    public YaV1SettingSetAdapter(Context context)
    {
        this.context = context;
    }

    @Override
    public int getCount()
    {
        return YaV1.sV1Settings.size();
    }

    @Override
    public Object getItem(int position)
    {
        return YaV1.sV1Settings.get(position);
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
        YaV1Setting setting;

        if(convertView == null)
        {
            // get layout from sweep set row
            convertView = layoutInflater.inflate(R.layout.settingset_row, parent, false);
        }

        // set value in TextView
        try
        {
            setting = YaV1.sV1Settings.get(position);
        }
        catch (IndexOutOfBoundsException e)
        {
            return convertView;
        }

        TextView  tvName = (TextView) convertView.findViewById(R.id.setting_name);
        // get the button
        ImageButton    btnEdit = (ImageButton) convertView.findViewById(R.id.edit);
        ImageButton    btnDel  = (ImageButton) convertView.findViewById(R.id.delete);
        ImageButton    btnPush = (ImageButton) convertView.findViewById(R.id.push);

        try
        {
            // reset if ever reused view

            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setEnabled(true);
            btnDel.setVisibility(View.VISIBLE);
            btnDel.setEnabled(true);
            btnPush.setVisibility(View.VISIBLE);
            btnPush.setEnabled(true);

            tvName.setText(setting.getName());
            boolean isCurrent = false;
            boolean isLast    = false;
            if(YaV1.sV1Settings.hasCurrent() && YaV1.sModeData != null)
            {
                if(YaV1.sV1Settings.getCurrentId() == setting.getId())
                    isCurrent = true;
            }
            else
            {
                if(YaV1.sV1Settings.getLastKnown() == setting.getId())
                    isLast = true;
            }

            // we can set the color if empty / current / lastKnown
            if(isCurrent)
                convertView.setBackgroundColor(Color.parseColor("#3776ff"));
            else if (isLast)
                convertView.setBackgroundColor(Color.parseColor("#40570d"));
            else
                convertView.setBackgroundColor(YaV1SweepSetActivity.DEFAULT_COLOR);

            // we can't edit or delete the factory default neither if current

            if(setting.isFactoryDefault() || isCurrent)
            {
                btnDel.setEnabled(false);
                btnDel.setVisibility(View.GONE);
            }
            else
            {
                btnDel.setEnabled(true);
                btnDel.setVisibility(View.VISIBLE);
            }

            // if V1 not connected, or current we can't push
            if(YaV1.sModeData == null || isCurrent || !YaV1AlertService.isActive())
            {
                btnPush.setEnabled(false);
                btnPush.setVisibility(View.GONE);
            }
            else
            {
                btnPush.setEnabled(true);
                btnPush.setVisibility(View.VISIBLE);
            }
        }
        catch (NullPointerException e)
        {
            Log.d("YaV1_SettingAdapter", "NullPointerException");
        }

        return convertView;
    }
}
