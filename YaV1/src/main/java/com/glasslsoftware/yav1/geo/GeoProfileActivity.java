package com.glasslsoftware.yav1.geo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import com.glasslsoftware.yav1.R;
import com.glasslsoftware.yav1.YaV1;
import com.glasslsoftware.yav1.YaV1Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * [P3-GEO] Configuration screen for location based profile switching.
 *
 * Lists every US state from the offline dataset; tapping a state opens a
 * picker with the existing V1 setting profiles (plus "no profile" to clear).
 * Mappings persist immediately in SharedPreferences as JSON under
 * {@link GeoProfileManager#PREF_RULES}. States without a mapping are simply
 * ignored by the engine.
 */
public class GeoProfileActivity extends Activity
{
    private final List<GeoState>      mStates = new ArrayList<GeoState>();
    private       Map<String, String> mRules;
    private       StateAdapter        mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTitle(R.string.geo_activity_title);

        mRules = GeoRuleStore.fromJson(YaV1.sPrefs.getString(GeoProfileManager.PREF_RULES, ""));

        StateResolver resolver = GeoProfileManager.getResolverBlocking(this);

        if(resolver == null)
        {
            Toast.makeText(this, R.string.geo_dataset_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        collectStates(resolver);

        ListView list = new ListView(this);
        mAdapter = new StateAdapter();
        list.setAdapter(mAdapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                pickProfile(mStates.get(position));
            }
        });

        setContentView(list);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        YaV1.superResume();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        YaV1.superPause();
    }

    private void collectStates(StateResolver resolver)
    {
        mStates.clear();

        for(GeoState s: resolver.getStates())
            mStates.add(s);

        Collections.sort(mStates, new Comparator<GeoState>()
        {
            @Override
            public int compare(GeoState a, GeoState b)
            {
                return a.getName().compareTo(b.getName());
            }
        });
    }

    /** Names of the existing V1 setting profiles. */
    private List<String> profileNames()
    {
        List<String> names = new ArrayList<String>();

        if(YaV1.sV1Settings != null)
        {
            for(YaV1Setting s: YaV1.sV1Settings)
            {
                if(s.getName() != null && s.getName().length() > 0)
                    names.add(s.getName());
            }
        }

        return names;
    }

    private void pickProfile(final GeoState state)
    {
        final List<String> profiles = profileNames();

        if(profiles.isEmpty())
        {
            Toast.makeText(this, R.string.geo_no_profiles_available, Toast.LENGTH_LONG).show();
            return;
        }

        final String[] items = new String[profiles.size() + 1];

        items[0] = getString(R.string.geo_no_profile);

        for(int i = 0; i < profiles.size(); i++)
            items[i + 1] = profiles.get(i);

        String current = mRules.get(state.getCode());
        int    checked = 0;

        for(int i = 1; i < items.length; i++)
        {
            if(items[i].equals(current))
            {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(String.format(getString(R.string.geo_pick_profile_title), state.getName()))
                .setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        if(which == 0)
                            mRules.remove(state.getCode());
                        else
                            mRules.put(state.getCode(), items[which]);

                        saveRules();
                        mAdapter.notifyDataSetChanged();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveRules()
    {
        YaV1.sPrefs.edit()
                .putString(GeoProfileManager.PREF_RULES, GeoRuleStore.toJson(mRules))
                .apply();
    }

    // ------------------------------------------------------------- adapter

    private class StateAdapter extends BaseAdapter
    {
        @Override
        public int getCount()
        {
            return mStates.size();
        }

        @Override
        public Object getItem(int position)
        {
            return mStates.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View view = convertView;

            if(view == null)
                view = LayoutInflater.from(GeoProfileActivity.this)
                        .inflate(android.R.layout.simple_list_item_2, parent, false);

            GeoState s       = mStates.get(position);
            String   profile = mRules.get(s.getCode());

            TextView line1 = (TextView) view.findViewById(android.R.id.text1);
            TextView line2 = (TextView) view.findViewById(android.R.id.text2);

            line1.setText(s.getName() + " (" + s.getCode() + ")");
            line2.setText(profile != null ? profile : getString(R.string.geo_no_profile));

            return view;
        }
    }
}
