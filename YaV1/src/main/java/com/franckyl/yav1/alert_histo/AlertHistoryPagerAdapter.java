package com.franckyl.yav1.alert_histo;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

/**
 * Created by franck on 2/3/14.
 */
public class AlertHistoryPagerAdapter extends FragmentPagerAdapter
{
    int PAGE_COUNT = 3;

    public AlertHistoryPagerAdapter(FragmentManager fm)
    {
        super(fm);
    }

    @Override
    public Fragment getItem(int arg0)
    {
        switch (arg0)
        {
            case 0:
                return new AlertHistoryFileFragment();
            case 1:
                return new AlertHistoryAlertFragment();
            case 2:
                return new AlertHistoryMapFragment();
        }

        return null;
    }

    @Override
    public int getCount()
    {
        // TODO Auto-generated method stub
        return PAGE_COUNT;
    }
}
