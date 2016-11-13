package com.franckyl.yav1.alert_histo;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1lib.YaV1Alert;

/**
 * Created by franck on 2/3/14.
 */
public class AlertHistoryAlertFragment extends Fragment
{
    private View                     mFragmentView;
    private AlertHistoryAlertAdapter mAdapter;
    private ListView                 mListView;
    private Spinner                  mModeSpinner;
    private TextView                 mCountView    = null;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    // create the view, we have to display the file list

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mFragmentView = inflater.inflate(R.layout.alert_histo_alert_fragment, container, false);

        mListView     = (ListView) mFragmentView.findViewById(R.id.list);
        mAdapter      = new AlertHistoryAlertAdapter(getActivity());

        mListView.setAdapter(mAdapter);

        mCountView    = (TextView) mFragmentView.findViewById(R.id.nbsel);

        mAdapter.setCounterView(mCountView);

        mModeSpinner  = (Spinner) mFragmentView.findViewById(R.id.mode_spinner);
        mModeSpinner.setSelection(0);

        mModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                if(position != AlertHistoryActivity.getCurrentMode())
                    AlertHistoryActivity.setCurrentMode(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {

            }
        });

        // clear all button

        Button bt = (Button) mFragmentView.findViewById(R.id.clear_all);
        bt.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                AlertHistoryActivity.mAlertList.select(-1);
                mAdapter.notifyDataSetChanged();
                refreshCount();
            }
        });

        String t = getTag();
        ( (AlertHistoryActivity) getActivity()).setFragmentTag(1, t);
        /*
        if(YaV1.sExpert && YaV1.sAutoLockout != null)
            mListView.setOnItemLongClickListener(mLongClick);
        */
        return mFragmentView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.alert_histo_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int i = -1;
        switch(item.getItemId())
        {
            case R.id.select_all:
                Log.d("Valentine", "Select all");
                i = 10;
                break;
            case R.id.select_laser:
                i = YaV1Alert.BAND_LASER;
                break;
            case R.id.select_ka:
                i = YaV1Alert.BAND_KA;
                break;
            case R.id.select_k:
                i = YaV1Alert.BAND_K;
                break;
            case R.id.select_x:
                i = YaV1Alert.BAND_X;
                break;
            case R.id.select_ku:
                i = YaV1Alert.BAND_KU;
                break;
        }

        // got an option for select

        if(i >= 0)
        {
            int nb = AlertHistoryActivity.mAlertList.select(i);
            mAdapter.notifyDataSetChanged();
            // we can hide the clear all according to Nb
            refreshCount();
            return true;
        }

        return false;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        //mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStop()
    {
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        setUserVisibleHint(true);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser)
    {
        super.setUserVisibleHint(isVisibleToUser);
        if(isVisibleToUser)
        {
            refreshCount();
        }
    }

    public void updateList()
    {
        mAdapter.notifyDataSetChanged();
    }

    // refresh the selected count

    public void refreshCount()
    {
        if(mCountView != null)
        {
            mCountView.setText(AlertHistoryActivity.mAlertList.getNbSelected() + "/" + AlertHistoryActivity.mAlertList.size());
        }
    }
}
