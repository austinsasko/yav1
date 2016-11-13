package com.franckyl.yav1;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.v4.app.FragmentPagerAdapter;

/**
 * Created by franck on 9/8/13.
 */
public class YaV1ScreenAdapter extends FragmentPagerAdapter
{
    // Declare the number of ViewPager pages
    int     PAGE_COUNT = 3;

    public YaV1ScreenAdapter(FragmentManager fm)
    {
        super(fm);
    }

    @Override
    public Fragment getItem(int arg0)
    {
        switch(arg0)
        {
            // Open FragmentTab1.java
            case 0:
            {
                YaV1ScreenV1Fragment fragment = new YaV1ScreenV1Fragment();
                return fragment;
            }
            case 1:
            {
                YaV1ScreenFragment fragment = new YaV1ScreenFragment();
                Bundle args = new Bundle();
                args.putString("IntentName", "V1Alert");
                fragment.setArguments(args);
                return fragment;
            }
            case 2:
            {
                YaV1ScreenToolFragment fragmentT = new YaV1ScreenToolFragment();
                return fragmentT;
            }
        }

        return null;
    }

    @Override
    public int getCount()
    {
        // TODO Auto-generated method stub
        return PAGE_COUNT;
    }

    // return true if the position is the tools

    public boolean isTool(int position)
    {
        return position == 2;
    }

    // return the tools tab position

    public int getToolsTabIndex()
    {
        return 2;
    }
}
