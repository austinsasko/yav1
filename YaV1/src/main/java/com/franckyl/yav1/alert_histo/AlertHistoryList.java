package com.franckyl.yav1.alert_histo;

import android.util.Log;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by franck on 1/29/14.
 */
public class AlertHistoryList extends ArrayList<AlertHistory>
{
    private       int       mCurrId         = 0;
    private       int       mNbSelected     = 0;
    private       boolean   mHasChanged     = false;
    private       boolean   mFromPersistent = false;
    private       String    mFileName       = "";
    private       boolean   mIsCsv          = false;

    public AlertHistoryList(String file, boolean csv)
    {
        mCurrId     = 0;
        mNbSelected = 0;
        mFileName   = file;
        mIsCsv      = csv;
    }

    // get the file name
    public String getFileName()
    {
        return mFileName;
    }

    // get the format

    public boolean isCsv()
    {
        return mIsCsv;
    }

    // set if from peristent

    public void setFromPersistent(boolean persist)
    {
        mFromPersistent = persist;
    }

    public boolean isFromPersistent()
    {
        return mFromPersistent;
    }

    // get number selected

    public int getNbSelected()
    {
        return mNbSelected;
    }
    public void setNbSelected(int nb)
    {
        mNbSelected = nb;
        mHasChanged = true;
    }

    public void incNbSelected(int nb)
    {
        mNbSelected += nb;
        mHasChanged = true;
    }

    public void resetChanged(boolean c)
    {
        mHasChanged = c;
    }

    public boolean hasChanged()
    {
        return mHasChanged;
    }

    // sorting by order values (asc)

    class alertCompare implements Comparator<LoggedAlert>
    {
        public int compare(LoggedAlert a1, LoggedAlert a2)
        {
            return a1.getOrder() - a2.getOrder();
        }
    }
    // process current alert

    public void process(ArrayList<LoggedAlert> current)
    {
        boolean found = false;
        long  refTime = current.get(0).timeStamp;

        // first we inactivate all the non complete
        for(AlertHistory h: this)
        {
            if(!h.isOption(AlertHistory.H_COMPLETE))
                h.unsetOption(AlertHistory.H_ACTIVE);
        }

        // sor the alert with the order
        Collections.sort(current, new alertCompare());

        // for all current we compare the non completed one

        for(LoggedAlert a: current)
        {
            found = false;
            for(AlertHistory h: this)
            {
                if(!h.isOption(AlertHistory.H_COMPLETE))
                {
                    if(mFromPersistent)
                    {
                        if( (found = (h.mPersistentId == a.mPxId)))
                            h.isSame(a, true);
                    }
                    else
                        // check if the current alert is same
                        found = h.isSame(a, false);

                    if(found)
                        break;
                }
            }

            // not found we add to this object
            if(!found)
                add(new AlertHistory(a, ++mCurrId, mFromPersistent));
        }

        // check for inactive remaining that are too old

        for(AlertHistory h: this)
        {
            if(!h.isOption(AlertHistory.H_COMPLETE))
            {
                if(!h.isOption(AlertHistory.H_ACTIVE) && refTime - h.mLastTime >= AlertHistoryActivity.TTL)
                    h.setComplete();
            }
        }
    }

    // reorder most recent to oldest

    public void complete()
    {
        Collections.reverse(this);
    }

    // Try to affect the recorded alerts to some existing alert

    void affectRecorded(ArrayList<LoggedAlert> al)
    {
        boolean found = false;

        for(LoggedAlert a: al)
        {
            found = false;
            for(AlertHistory h: this)
            {
                if(h.isOwner(a))
                {
                    found = true;
                    break;
                }
            }

            if(!found)
                Log.d("Valentine", "Recorded alert not found");
        }
    }
    // get the zoom of selected

    CameraUpdate getZoom()
    {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        Marker m;
        int i = 0;
        for(AlertHistory h: this)
        {
            for(LoggedAlert a: h)
            {
                if(a.isMarked() && (m = a.getMarker()) != null && m.isVisible())
                {
                    builder.include(m.getPosition());
                    i++;
                }
            }
        }

        if(i > 0)
        {
            LatLngBounds bounds = builder.build();
            return CameraUpdateFactory.newLatLngBounds(bounds, 80);
        }

        return null;
    }

    // select or clear the check

    public int select(int m)
    {
        mNbSelected = 0;
        for(AlertHistory l: this)
        {
            if(m > 5)
            {
                if(l.hasGps())
                {
                    l.setOption(AlertHistory.H_CHECK);
                    mNbSelected++;
                }
                resetChanged(true);
            }
            else if(m < 0)
            {
                l.unsetOption(AlertHistory.H_CHECK);
                resetChanged(true);
            }
            else
            {
                if(l.setCheck(m))
                {
                    mNbSelected++;
                    resetChanged(true);
                }
            }
        }

        return mNbSelected;
    }
}
