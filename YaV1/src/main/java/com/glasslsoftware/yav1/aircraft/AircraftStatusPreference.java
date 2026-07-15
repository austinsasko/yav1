package com.glasslsoftware.yav1.aircraft;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import com.glasslsoftware.yav1.R;

import java.util.List;

/**
 * [P2-ADSB] Simple debug/status surface: shows the aircraft seen in the last
 * ADS-B poll with watchlist / heuristic annotations.
 */
public class AircraftStatusPreference extends Preference
{
    public AircraftStatusPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public AircraftStatusPreference(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onClick()
    {
        AircraftMonitor m = AircraftMonitor.getInstance();

        StringBuilder sb = new StringBuilder();

        if(m == null)
            sb.append(getContext().getString(R.string.adsb_status_disabled));
        else
        {
            long age = m.getLastResultAge();

            if(age < 0)
                sb.append(getContext().getString(R.string.adsb_status_no_poll));
            else
            {
                sb.append(String.format(getContext().getString(R.string.adsb_status_age),
                                        age / 1000));
                sb.append("\n\n");

                List<String> lines = m.getStatusLines();
                if(lines.isEmpty())
                    sb.append(getContext().getString(R.string.adsb_status_empty));
                else
                {
                    for(String l: lines)
                        sb.append(l).append('\n');
                }
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.pref_adsb_status)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
