package com.franckyl.yav1;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.franckyl.yav1.events.AlertEvent;
import com.squareup.otto.Subscribe;

public class YaV1ScreenFragment extends YaV1ScreenBaseAlertFragment
{
    private YaV1Adapter mAlertAdapter;
    private boolean     mIsRegistered = false;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        if(args != null)
            mIntentName = getArguments().getString("IntentName");
        else
            mIntentName = "V1Alert";

        //mParent = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        //mFragmentView = inflater.inflate( (YaV1CurrentPosition.enabled ? R.layout.yav1_screen_fragment_gps : R.layout.yav1_screen_fragment), container, false);
        mFragmentView = inflater.inflate(R.layout.yav1_screen_fragment, container, false);
        mListAlert    = (ListView) mFragmentView.findViewById(R.id.alertList);

        // set the divider to 0 height and no line

        mListAlert.setDividerHeight(0);
        mListAlert.setDivider(null);

        mAlertAdapter  = new YaV1Adapter(getActivity(), mAlertList);
        mListAlert.setAdapter(mAlertAdapter);
        setGpsParts(true);
        return mFragmentView;
    }

    // long item in case of lockout long item

    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        //setUserVisibleHint(true);
    }

    @Override
    public void onResume()
    {
        // YaV1.superResume();
        super.onResume();
        if(!mIsRegistered)
        {
            YaV1.getEventBus().register(this);
            mIsRegistered = true;
        }
        // LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mAlertReceiver, new IntentFilter(mIntentName));
        //Log.d("Valentine", "Receiver installed " + mIntentName);
        //registerForContextMenu(mListAlert);
    }

    @Override
    public void onPause()
    {
        // YaV1.superPause();
        super.onPause();
        if(mIsRegistered)
        {
            YaV1.getEventBus().unregister(this);
            mIsRegistered = false;
        }
        //LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mAlertReceiver);
        Log.d("Valentine", "Receiver un installed " + mIntentName + " Not visible");
        // mCallBackInstalled.set(false);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser)
    {
        super.setUserVisibleHint(isVisibleToUser);
        Log.d("Valentine Alert", "Visible to user " + isVisibleToUser + " Installed " + mIsRegistered);
        if(isVisibleToUser)
        {
            if(!mIsRegistered)
            {
                YaV1.getEventBus().register(this);
                mIsRegistered = true;
            }
        }
        else
        {
            if(mIsRegistered)
            {
                YaV1.getEventBus().unregister(this);
                mIsRegistered = false;
                clearRemaining();
            }
        }
    }

    @Subscribe
    public void onNewAlert(AlertEvent evt)
    {
        if(evt.getType() == AlertEvent.Type.V1_ALERT)
        {
            getActivity().runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    YaV1.getNewAlert(mAlertList);
                    mAlertAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    // clear the remaining alert
    private void clearRemaining()
    {

        getActivity().runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mAlertList.clear();
                mAlertAdapter.notifyDataSetChanged();
            }
        });
    }
    // receive the alerts
    /*
    private BroadcastReceiver mAlertReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            // this could happen when rotating
            if(getActivity() == null || !intent.getAction().equals(mIntentName))
                return;

            // mAlertList = intent.getParcelableExtra("V1Alert");
            // getActivity().runOnUiThread(mRefreshAlert);
            final YaV1AlertList lAl = intent.getParcelableExtra("V1Alert");
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (lAl != null) {
                        mAlertList.clear();
                        mAlertList.addAll(lAl);
                        mAlertAdapter.notifyDataSetChanged();
                        //Log.d("Valentine", "End notify " + SystemClock.elapsedRealtime());
                    }
                }
            });
        }
    };
    */
}
