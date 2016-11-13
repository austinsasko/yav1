package com.franckyl.yav1.utils;

import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.util.Log;

import com.franckyl.yav1.YaV1;

/**
 * Created by franck on 8/18/14.
 */
public class MemoryBoss implements ComponentCallbacks2
{

    @Override
    public void onConfigurationChanged(final Configuration newConfig)
    {
    }

    @Override
    public void onLowMemory()
    {
    }

    @Override
    public void onTrimMemory(final int level)
    {
        if(level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        {
            // We're in the Background
            Log.d("Valentine", "We are in background according to memory boss");
            YaV1.backgroundFromBoss();
        }
    }
}
