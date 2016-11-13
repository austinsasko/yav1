package com.franckyl.yav1;

import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;

/**
 * Created by franck on 9/28/13.
 */
public class CustomSwitch extends SwitchPreference
{
    public CustomSwitch(Context context)
    {
        super(context);
    }

    public CustomSwitch(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public CustomSwitch(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }
}