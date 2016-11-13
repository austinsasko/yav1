package com.franckyl.yav1.utils;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import java.util.List;

/**
 * Created by franck on 2/3/14.
 */
public class HighLightArrayAdapter extends ArrayAdapter<String>
{
    private Context context;
    private int     mCurrentPosition  = -1;

    public HighLightArrayAdapter(Context context, int textViewResourceId, List<String> objects)
    {
        super(context, textViewResourceId, objects);
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        View v = super.getView(position, convertView, parent);

        if(position == mCurrentPosition)
            v.setBackgroundColor(Color.parseColor("#4d90fe"));
        else
            v.setBackgroundColor(Color.TRANSPARENT);

        return v;
    }

    public void setSelection(int s)
    {
        if(mCurrentPosition != s)
        {
            mCurrentPosition = s;
            notifyDataSetChanged();
        }
    }

    public int getSelection()
    {
        return mCurrentPosition;
    }
}
