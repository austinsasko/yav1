package com.franckyl.yav1.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ExpandableListView;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1lib.YaV1Alert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by franck on 8/14/14.
 */
public class BandRangeList extends DialogPreference
{
    Context mContext;

    private String                        mPrefName = "";
    private String                        mPrefType = "";
    private HashMap<Integer, List<Pair<Integer, Integer>>>  mRangeList = new HashMap<Integer, List<Pair<Integer, Integer>>>();
    private List<Integer>                 mHeader = new ArrayList<Integer>();
    private ExpandableListView            mListView;
    private BandRangeListAdapter          mAdapter;

    // constructor

    public BandRangeList(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BandRangeList);
        mPrefName = a.getString(R.styleable.BandRangeList_prefname);

        mPrefType = a.getString(R.styleable.BandRangeList_preftype);
        a.recycle();
        // get the preference and parse it
        setDialogLayoutResource(R.layout.band_range_list);
        // set the layout for the dialog
        mContext = context;
    }

    // bind the dialog

    protected void onBindDialogView(View view)
    {
        super.onBindDialogView(view);
    }

    protected void showDialog(Bundle bundle)
    {
        super.showDialog(bundle);

        initRangeList();
        mAdapter = new BandRangeListAdapter(mContext, mHeader, mRangeList);

        // get our list view
        mListView = (ExpandableListView) getDialog().findViewById(R.id.expandableListView);

        // set the adapter
        mListView.setAdapter(mAdapter);

        // click child listener
        mListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener()
        {

            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id)
            {
                Pair<Integer, Integer> p = mRangeList.get(mHeader.get(groupPosition)).get(childPosition);
                mAdapter.editCreateRange(p, mHeader.get(groupPosition), childPosition);
                // Log.d("Valentine", "Child click at " + mHeader.get(groupPosition) + " range " + p.first.toString() + " - " + p.second.toString());
                return true;
            }
        });
    }

    // handle the lick on the buttons
    public void onClick(DialogInterface dialog, int which)
    {
        switch(which)
        {
            case DialogInterface.BUTTON_POSITIVE:
                // we reorganize the string and dismiss
                String s = "";

                for(Map.Entry<Integer, List<Pair<Integer, Integer>>> entry: mRangeList.entrySet())
                {
                    List<Pair<Integer, Integer>> lp = entry.getValue();
                    for(Pair<Integer, Integer> pair: lp)
                    {
                        if(s.length() == 0)
                            s = String.format("%d-%d", pair.first, pair.second);
                        else
                            s += String.format(",%d-%d", pair.first, pair.second);
                    }
                }

                // store the preference
                SharedPreferences.Editor editor = getEditor();
                editor.putString(mPrefName, s);
                editor.commit();
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                getDialog().dismiss();
                break;
        }
    }

    protected void onDialogClosed(boolean positiveResult)
    {
        super.onDialogClosed(positiveResult);
    }

    // initialise the rand list

    private void initRangeList()
    {
        boolean hasKa = true;

        if(mPrefType.equals("lockout"))
        {
            if(!getSharedPreferences().getBoolean("ka_lockout", false) || getSharedPreferences().getBoolean("ka_manual_only", true))
                hasKa = false;
            else
                hasKa = true;

            Log.d("Valentine", "Band Range has Ka " + hasKa);
        }


        mRangeList.clear();
        mHeader.clear();

        if(hasKa)
        {
            mHeader.add(YaV1Alert.BAND_KA);
            mRangeList.put(YaV1Alert.BAND_KA, new ArrayList<Pair<Integer, Integer>>());
        }

        mHeader.add(YaV1Alert.BAND_K);
        mHeader.add(YaV1Alert.BAND_X);

        mRangeList.put(YaV1Alert.BAND_K, new ArrayList<Pair<Integer, Integer>>());
        mRangeList.put(YaV1Alert.BAND_X, new ArrayList<Pair<Integer, Integer>>());

        // get the preference
        String str = getSharedPreferences().getString(mPrefName, "");

        // for testing

        // String str = "24120-24130 ,24200- 24250,35700-3600";

        if(!str.isEmpty())
        {
            // we split the string on ,
            List<String> items = Arrays.asList(str.split("\\s*,\\s*"));
            List<String> ranges;
            int band;

            // populate or list
            for(String s: items)
            {
                ranges = Arrays.asList(s.split("\\-"));
                if(ranges.size() >= 2)
                {
                    band = YaV1.BandBoundaries.getIntBandFromFrequency(Integer.valueOf(ranges.get(0).trim()));
                    // we might had Ka before and not anymore
                    if(band >= 0 && mRangeList.containsKey(band))
                    {
                        mRangeList.get(band).add(new Pair(Integer.valueOf(ranges.get(0).trim()), Integer.valueOf(ranges.get(1).trim())));
                    }
                }
            }
        }
    }

    // explode a range list that can be used in AlertProcessor

    public static ArrayList<Pair<Integer, Integer>>[] parseString(String str)
    {
        ArrayList<Pair<Integer, Integer>>[] mRc  = (ArrayList<Pair<Integer, Integer>>[]) new ArrayList[5];
        for(int i = 0; i < 5; i++)
            mRc[i] = null;

        if(!str.isEmpty())
        {
            // we split the string on ,
            List<String> items = Arrays.asList(str.split("\\s*,\\s*"));
            List<String> ranges;
            int band;

            for(String s: items)
            {
                ranges = Arrays.asList(s.split("\\-"));
                if(ranges.size() >= 2)
                {
                    band = YaV1.BandBoundaries.getIntBandFromFrequency(Integer.valueOf(ranges.get(0).trim()));
                    if(band >= 0)
                    {
                        if(mRc[band] == null)
                            mRc[band] = new ArrayList<Pair<Integer, Integer>>();

                        mRc[band].add(new Pair(Integer.valueOf(ranges.get(0).trim()), Integer.valueOf(ranges.get(1).trim())));
                        Log.d("Valentine", "Range " + ranges.get(0) + " - " + ranges.get(1) + " Added to band " + band);
                    }
                }
            }
        }

        return mRc;
    }
}
