package com.franckyl.yav1;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.franckyl.yav1lib.YaV1AlertList;

/**
 * Created by franck on 2/27/14.
 */
public class YaV1ScreenBaseAlertFragment extends Fragment
{
    protected View          mFragmentView;
    protected ListView      mListAlert;
    protected YaV1AlertList mAlertList       = new YaV1AlertList();
    protected String        mIntentName      = "";


    // create called from based

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
    }

    // set the gpd view parts

    protected boolean setGpsParts(boolean alertView)
    {
        if(!YaV1CurrentPosition.enabled)
            return false;

        // set long click to the screen activity
        if(YaV1CurrentPosition.sLockout || YaV1CurrentPosition.sCollector)
        {
            View mContainer;
            if(!alertView)
                mContainer = mFragmentView.findViewById(R.id.alert_container);
            else
            {
                mContainer = mFragmentView.findViewById(R.id.click_handle);
                mContainer.setVisibility(View.VISIBLE);
            }
            mContainer.setOnLongClickListener(new View.OnLongClickListener()
            {
                @Override
                public boolean onLongClick(View v)
                {
                    Log.d("Valentine Alert", "long click fragment received");
                    ( (YaV1ScreenActivity) getActivity()).onLongClick();
                    return true;
                }
            });
            return true;
        }
        return false;
    }
}
