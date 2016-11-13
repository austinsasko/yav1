package com.franckyl.yav1;

import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;

import com.franckyl.yav1.utils.BandRangeList;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by franck on 6/29/13.
 */

public class YaV1PrefFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        String settings = getArguments().getString("settings");
        if("general".equals(settings)) {
            addPreferencesFromResource(R.xml.pref_general);
        } else if ("box".equals(settings)) {
            addPreferencesFromResource(R.xml.pref_box);
        } else if("display".equals(settings)){
            addPreferencesFromResource(R.xml.pref_display);
        //} else if("mute".equals(settings)){
        //    addPreferencesFromResource(R.xml.pref_mute);
        } else if ("record".equals(settings)) {
            addPreferencesFromResource(R.xml.pref_record);
        } else if ("lockout".equals(settings)) {
            addPreferencesFromResource(R.xml.pref_lockout);
        } else if ("gmap".equals(settings)) {
            addPreferencesFromResource(R.xml.pref_gmap);
        } else if ("sound".equals(settings)) {
            addPreferencesFromResource(R.xml.pref_sound);
        }

        // setHasOptionsMenu(false);
    }

    @Override
    public void onResume()
    {
        // Set up a listener whenever a key changes
        getPreferenceManager().getDefaultSharedPreferences(YaV1.sContext).registerOnSharedPreferenceChangeListener(this);
        // set the ringtone names
        // setRingtoneSummary();
        //setBandSummary();
        initList();
        super.onResume();
        YaV1.superResume();
    }

    @Override
    public void onPause()
    {
        // Set up a listener whenever a key changes
        getPreferenceManager().getDefaultSharedPreferences(YaV1.sContext).unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
        YaV1.superPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        Preference l = findPreference(key);
        if(l instanceof ListPreference)
            l.setSummary(((ListPreference) l).getEntry());
        else if(l instanceof BandRangeList)
            l.setSummary(sharedPreferences.getString(key, ""));
        else if(l instanceof DialogBandRange)
            l.setSummary(getBoxSummary(sharedPreferences.getString(key, "")));
        else if(l instanceof RingtonePreference)
            l.setSummary(getRingToneTitleFromString(sharedPreferences.getString(key, ""), "Silent"));
    }

    @Override
    public void onStart()
    {
        super.onStart();
    }

    @Override
    public void onStop()
    {
        super.onStop();
    }

    // get ringtone title from  string

    public String getRingToneTitleFromString(String str, String notFound)
    {
        if(!str.isEmpty())
        {
            Ringtone ringtone;
            Uri ringtoneUri = Uri.parse(str);
            if(ringtoneUri != null && (ringtone = RingtoneManager.getRingtone(getActivity(), ringtoneUri)) != null)
                return ringtone.getTitle(getActivity());
        }

        return notFound;
    }

    public String getBoxSummary(String s)
    {
        String sBox = "";
        List<String>    items;
        if(!s.isEmpty())
        {
            // split in 3 low,high,color
            items = Arrays.asList(s.split("\\s*,\\s*"));
            if(items.size() >= 3)
            {
                String v = "";
                if(items.size() > 3)
                    v = getRingToneTitleFromString(items.get(3), "");
                 sBox = String.format("%s - %s", items.get(0), items.get(1));
                if(!v.isEmpty())
                    sBox += " - " + v;
            }
        }

        return sBox;
    }

    public void initList()
    {
        Map<String,?> keys = PreferenceManager.getDefaultSharedPreferences(YaV1.sContext).getAll();

        for(Map.Entry<String,?> entry : keys.entrySet())
        {
            Preference l = findPreference(entry.getKey());
            if(l instanceof ListPreference)
                l.setSummary(((ListPreference) l).getEntry());
            else if(l instanceof BandRangeList)
                l.setSummary(entry.getValue().toString());
            else if(l instanceof DialogBandRange)
                l.setSummary(getBoxSummary(entry.getValue().toString()));
            else if(l instanceof RingtonePreference)
                l.setSummary(getRingToneTitleFromString(entry.getValue().toString(), "Silent"));

        }
    }
}
