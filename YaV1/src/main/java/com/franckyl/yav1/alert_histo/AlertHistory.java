package com.franckyl.yav1.alert_histo;

import android.graphics.Color;
import android.location.Location;
import android.util.Log;

import com.franckyl.yav1.AlertProcessorParam;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1AlertService;
import com.franckyl.yav1.lockout.LockoutParam;
import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.utils.GMapUtils;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by franck on 1/29/14.
 */
public class AlertHistory extends ArrayList<LoggedAlert>
{
    public static final int H_ACTIVE       = 1;
    public static final int H_COMPLETE     = 2;
    public static final int H_HASGPS       = 4;
    public static final int H_HASSTART     = 8;
    public static final int H_ALLBASED     = 16;
    public static final int H_GPSDROP      = 32;
    public static final int H_CHECK        = 64;
    public static final int H_DIFFDIR      = 128;
    public static final int H_ALLREADY     = 256;
    public static final int H_LOCKOUT_ABLE = 512;

    private static int      MIN_ARROW_DISTANCE = 20;
    //private static int      MIN_MARK_SPACE     = 30;

    //public  static int      ALERT_TTL          = 6;

    static  float       mResults[] = {0, 0, 0};

    private int         mOption        = 0;

    private long        mFirstTime;
    private LoggedAlert mCurrent;
    private int         mNbMissed;
    private int         mLastTn;
    private int         mDistance;
    private int         mFirstValid;
    private int         mLastValid;
    public  long        mLastTime;
    public  int         mInitialFrequency;
    public  int         mMaxFrequency;
    public  int         mMinFrequency;
    public  double      mMaxSpeed;
    public  double      mMinSpeed;
    public  int         mId;
    public  int         mRecordOption;
    public  int         mPersistentId;
    public  int         mListColor;
    public  int         mLockoutId;

    public static int   sFrequencyThreshold[] = {0, 6, 5, 2, 6};

    private List<String> mProp        = new ArrayList<String>(10);

    public AlertHistory(LoggedAlert n, int id, boolean usePersistent)
    {
        mCurrent    = n;
        mFirstValid = -1;
        mLastValid  = -1;
        mFirstTime  = n.timeStamp;
        mLastTime   = n.timeStamp;
        mDistance   = 0;
        mNbMissed   = 0;
        mLastTn     = n.getTn();
        mId         = id;
        mOption     = H_ACTIVE;

        mInitialFrequency = n.getFrequency();
        mMaxFrequency = mMinFrequency = mInitialFrequency;
        mMaxSpeed     = mMinSpeed     = n.speed;

        mRecordOption = 0;

        add(mCurrent);
        // we set the marker options (for first)
        if(mCurrent.isValid())
        {
            mCurrent.setBaseMark();
            setOption(H_HASSTART);
            setOption(H_HASGPS);

            mFirstValid = mLastValid = 0;
        }
        else
            setOption(H_GPSDROP);

        if(usePersistent)
        {
            mPersistentId = n.mPxId;
            mLockoutId    = n.getPersistentId();

            if(mPersistentId > 0 || (n.isValid() && AlertProcessorParam.isLockoutAble(n.getBand(), n.getFrequency(), n.getSignal()) > 0))
                setOption(H_LOCKOUT_ABLE);
        }
        else
        {
            mPersistentId = 0;
            mLockoutId = 0;
        }

        // we check for the alert processor parameters
        int i = n.getProperty();
        if((i & YaV1Alert.PROP_LOCKOUT) > 0)
            mListColor = LockoutParam.mLockoutColor;
        else if((i & YaV1Alert.PROP_WHITE) > 0)
            mListColor = LockoutParam.mWhiteLockoutColor;
        else if((i & YaV1Alert.PROP_INBOX) > 0)
        {
            mListColor = AlertProcessorParam.getBoxColor(n.getBand(), n.getFrequency());
        }

        // if color if transparent user the default color for the band
        if(mListColor == Color.TRANSPARENT)
        {
            mListColor = AlertHistoryActivity.mDefaultListColor[n.getBand()];
        }

        AlertHistoryActivity.checkIconColor(mListColor);
    }

    public boolean isOption(int i)
    {
        return (mOption & i) > 0;
    }

    public void setOption(int i)
    {
        mOption |= i;
    }

    public void unsetOption(int i)
    {
        mOption = (mOption & (~i));
    }

    public boolean isRecorded()
    {
        return mRecordOption > 0;
    }

    // set the check option according to band

    public boolean setCheck(int band)
    {
        if(hasGps() && get(0).getBand() == band)
        {
            setOption(H_CHECK);
            return true;
        }
        unsetOption(H_CHECK);
        return false;
    }

    // check if the given logged Alert is same

    public boolean isSame(LoggedAlert alert, boolean force)
    {
        // we check if band is same and frequency in range according to the band
        if( force || (alert.getBand() == mCurrent.getBand() && alert.timeStamp >= mLastTime && alert.timeStamp - mLastTime < AlertHistoryActivity.TTL && isInRange(alert.getFrequency())))
        {
            // it's same move the current to the list and make new as current
            if(alert.getTn() > mLastTn + 1)
            {
                alert.setMissed(alert.getTn() - mLastTn + 1);
                mNbMissed++;
            }

            mLastTn       = alert.getTn();
            mLastTime     = alert.timeStamp;
            mMaxFrequency = Math.max(mMaxFrequency, alert.getFrequency());
            mMinFrequency = Math.min(mMinFrequency, alert.getFrequency());
            mMaxSpeed     = Math.max(mMaxSpeed, alert.speed);
            mMinSpeed     = Math.min(mMinSpeed, alert.speed);

            setOption(H_ACTIVE);

            // we have different arrow direction

            if(alert.getArrowDir() != mCurrent.getArrowDir())
                setOption(H_DIFFDIR);

            // if we do not have start mark, or previous invalid, we mark this on

            if(alert.isValid())
            {
                if(!isOption(H_HASSTART) || !mCurrent.isValid())
                {
                    alert.setBaseMark();
                    if(!isOption(H_HASSTART))
                    {
                        mFirstValid = size();
                        setOption(H_HASSTART);
                    }

                    if(!mCurrent.isValid())
                    {
                        alert.setOption(LoggedAlert.LGA_GPSGAIN);
                        alert.setBaseMark();
                    }
                }

                // this is the last valid
                mLastValid = size();
            }
            else
            {
                setOption(H_GPSDROP);
                // not valid, but previous was valid we mark it
                if(mCurrent.isValid())
                {
                    mCurrent.setBaseMark();
                    mCurrent.setOption(LoggedAlert.LGA_GPSLOST);
                }
            }

            mCurrent = alert;
            add(mCurrent);
            return true;
        }

        return false;
    }

    // check if the given frequency can be same

    public boolean isInRange(int frequ)
    {
        int d = sFrequencyThreshold[mCurrent.getBand()];
        return (frequ >= (mInitialFrequency-d) && frequ <= (mInitialFrequency+d));
    }

    // check if a recorded alert belongs to this one

    public boolean isOwner(LoggedAlert a)
    {
        if(a.getBand() == mCurrent.getBand())
        {
            for(LoggedAlert n: this)
            {
                boolean s;

                if(mPersistentId > 0)
                    s = mPersistentId == a.mPxId;
                else
                    s = n.getTn() == a.getTn() && n.getOrder() == a.getOrder();

                // if pxId and same got it
                if(s)
                {
                    int f = (a.getProperty() & LoggedAlert.COLLECTED);

                    // we might have to clean the flags
                    if( (f & (YaV1Alert.PROP_TRUE | YaV1Alert.PROP_FALSE)) > 0)
                        mRecordOption = mRecordOption & (~(YaV1Alert.PROP_TRUE | YaV1Alert.PROP_FALSE));

                    if( (f & (YaV1Alert.PROP_MOVING | YaV1Alert.PROP_STATIC)) > 0)
                        mRecordOption = mRecordOption & (~(YaV1Alert.PROP_MOVING | YaV1Alert.PROP_STATIC));

                    mRecordOption |= f;
                    return true;
                }
            }
        }

        return false;
    }

    // get the current

    public LoggedAlert getCurrent()
    {
        return mCurrent;
    }

    // close the alert history

    public void setComplete()
    {
        if(!isOption(H_COMPLETE))
        {
            setOption(H_COMPLETE);

            if(!isOption(H_HASGPS))
            {
                Log.d("Valentine", "Alert with no GPS");
                return;
            }

            // compute the distance done

            Location.distanceBetween(get(mFirstValid).lat, get(mFirstValid).lon, get(mLastValid).lat, get(mLastValid).lon, mResults);
            mDistance = (int) mResults[0];

            //set the final marker

            if(mCurrent.isValid())
                mCurrent.setOption(LoggedAlert.LGA_MARK_IT);

            // if less than 5, mark all
            if(size() <= 5)
            {
                if(size() > 2)
                {
                    markAllBased();
                    setOption(H_ALLBASED);
                }
                return;
            }

            int maxD = -1;

            // we mark the direction changes

            if(isOption(H_DIFFDIR))
            {
                maxD = markDirection(maxD);
            }

            // we mark max signal between 2 mark

            maxD = markMaxSignal(maxD);

            // if max Distance is > ThreShold we mark it

            if( (maxD < 0  && mDistance > AlertHistoryActivity.MIN_MARK_SPACE || maxD > AlertHistoryActivity.MIN_MARK_SPACE))
                markSpacing();
        }
    }

    // if the alert is "blind"
    public boolean hasGps()
    {
        return mFirstValid >= 0 && mLastValid >= 0;
    }

    public int getDistance()
    {
        return mDistance;
    }

    public long getDuration()
    {
        return mLastTime - mFirstTime;
    }

    public String getStartTime()
    {
        return GMapUtils.getTimeString(mFirstTime);
    }

    public boolean hasAll()
    {
        return mFirstValid == 0 && mLastValid == size() -1;
    }

    // Get the title for an alert

    public String getTitle()
    {
        LoggedAlert l = get(0);
        String s = l.getBandStr();
        if(l.getBand() != 0)
            s += " " + l.getFrequency();

        s += " " + size() + " hits";

        if(!hasAll())
            s += " *";

        return s;
    }

    // get the dialog title

    public String getDialogTitle()
    {
        LoggedAlert l = get(0);
        String s = mId + ": " + l.getBandStr();
        if(l.getBand() != 0)
            s += " " + String.format("%.03f", (l.getFrequency()/1000.0));
        return s;
    }

    // get the dialog message

    public String getDialogMessage()
    {
        LoggedAlert l = get(0);
        // time, duration, hits
        String s = getStartTime() + " - " + String.format("%d'%02d''", ((int) (getDuration() / 60)), ( (int) (getDuration() % 60))) +
                " - " + size() + " hits\n";

        s += GMapUtils.getDistanceInUnit(getDistance()) + (hasAll() ? "" : "*") + " - " +
             GMapUtils.getSpeedStr( (mMinSpeed < 0 ? 0 : mMinSpeed), mMaxSpeed) + " - " + YaV1CurrentPosition.bearingToString((float) l.bearing) + "\n";

        if(l.getBand() != 0)
            s +=  "-" + (mInitialFrequency - mMinFrequency) + "/+" + (mMaxFrequency-mInitialFrequency) + " Mhz - ";

        s += LoggedAlert.sAlertDir[l.getArrowDir()] + " " + l.getSignal();

        int  p = l.getProperty();
        mProp.clear();

        // lockout or white
        if((p & YaV1Alert.PROP_LOCKOUT ) > 0)
            mProp.add("LOCKOUT");
        else if((p & YaV1Alert.PROP_WHITE) > 0)
            mProp.add("WL");

        // in box
        if((p & YaV1Alert.PROP_INBOX ) > 0)
            mProp.add("ITB");

        if(mRecordOption > 0)
        {
            // dump the recorded
            if( (mRecordOption & YaV1Alert.PROP_TRUE) > 0)
            {
                mProp.add("TRUE");
                if( (mRecordOption & YaV1Alert.PROP_IO) > 0)
                    mProp.add("IO");
            }
            else if( (mRecordOption & YaV1Alert.PROP_FALSE) > 0)
                mProp.add("FALSE");

            if( (mRecordOption & YaV1Alert.PROP_MOVING) > 0)
                mProp.add("MOVING");
            if( (mRecordOption & YaV1Alert.PROP_STATIC) > 0)
                mProp.add("STATIC");
        }

        if(mProp.size() > 0)
            s += "\n" + GMapUtils.implode(" / ", mProp);

        return s;
    }

    // update the markers

    public int updateMarker(GoogleMap map, boolean recreate)
    {
        int nb = 0;
        if(!isOption(H_CHECK))
        {
            for(LoggedAlert a: this)
                a.setMarkerVisible(false);
        }
        else
        {
            int i           = 1;
            LoggedAlert ref = get(mFirstValid);

            // we create the markers

            for(LoggedAlert a: this)
            {
                // this alert needs a marker
                if(a.isMarked())
                {
                    nb++;
                    MarkerOptions m = a.getMarkerOptions();
                    // have we got the options ?
                    if(m == null)
                    {
                        // create the options for this alert
                        // create the marker on the map
                        m = new MarkerOptions().position(new LatLng(a.lat, a.lon));
                        String s = mId + ": " + a.getBandStr();
                        if(a.getBand() > 0)
                        {
                            s += String.format(" %.03f", (a.getFrequency()/1000d));
                            if(i > 1)
                                s += String.format( " (%+d Mhz)", (a.getFrequency() - mInitialFrequency));
                        }
                        //m.icon(B)
                        // default color
                        //m.icon(BitmapDescriptorFactory.defaultMarker(sBaseColor[ref.getColorIndex()]));
                        m.icon(AlertHistoryActivity.sIconColor.get(mListColor));
                        // title
                        m.title(s);
                        // snippet
                        s = a.getDirSignal() + "," + a.getProperty() + "," +
                                GMapUtils.getTimeString(a.timeStamp) + " - " +
                                GMapUtils.getSpeedStr( (a.speed < 0 ? 0 : a.speed)) + " - " + YaV1CurrentPosition.bearingToString((float) a.bearing) + "\n" +
                                i + "/" + this.size();

                        if(i > 1)
                        {
                            // get the distance since start
                            Location.distanceBetween(ref.lat, ref.lon, a.lat, a.lon, mResults);
                            s +=  "  +" + (a.timeStamp - mFirstTime) + " ''" + (mFirstValid > 0 ? "*" : "");
                            s += " " + GMapUtils.getDistanceInUnit(((int) mResults[0])) + (mFirstValid > 0 ? "*" : "");
                        }

                        int  p = a.getProperty();
                        mProp.clear();

                        if((p & YaV1Alert.PROP_LOCKOUT ) > 0)
                            mProp.add("L");
                        else if((p & YaV1Alert.PROP_WHITE) > 0)
                            mProp.add("WL");

                        if((p & YaV1Alert.PROP_INBOX ) > 0)
                            mProp.add("ITB");

                        if(a.gpsLost())
                            mProp.add("GPS-");
                        else if(a.gpsGain())
                            mProp.add("GPS+");

                        if(a.mNbMissed > 0)
                            mProp.add( new String(Integer.toString(a.mNbMissed)));

                        if(mProp.size() > 0)
                            s += " (" + GMapUtils.implode("/", mProp) + ")";

                        m.snippet(s);
                        a.setMarkerOptions(m);
                    }

                    // create the marker

                    if(a.needMarker())
                        a.setMarker(map.addMarker(m));
                    else
                        a.setMarkerVisible(true);
                }
                else
                    a.setMarkerVisible(false);

                i++;
            }
        }

        return nb;
    }

    // mark all alert as based marked

    private void markAllBased()
    {
        LoggedAlert a;
        for(int i=mFirstValid; i<=mLastValid; i++)
        {
            a = get(i);
            if(a.isValid() && !a.isBasedMarked())
                a.setBaseMark();
        }
    }

    // set marker on arrow direction changes

    private int markDirection(int maxD)
    {
        LoggedAlert a, lastMarked = get(mFirstValid);
        int d    = lastMarked.getArrowDir();

        for(int i=mFirstValid+1; i < size(); i++)
        {
            a = get(i);
            if(a.isValid() && d != a.getArrowDir())
            {
                // get the distance between last marked and this one
                Location.distanceBetween(lastMarked.lat, lastMarked.lon, a.lat, a.lon, mResults);

                if( (int) mResults[0] > MIN_ARROW_DISTANCE)
                {
                    a.setBaseMark();
                    d = a.getArrowDir();
                    maxD = Math.max(maxD, (int) mResults[0]);
                    lastMarked = a;
                }
            }
        }

        return maxD;
    }

    // set marker on arrow direction changes

    private int markMaxSignal(int maxD)
    {
        LoggedAlert a, lastMarked;
        int c = mFirstValid, d, am;

        while(c < size() - 1)
        {
            //Log.d("Valentine", mId + " Starting loop c " + c + " size " + size());
            lastMarked = get(c);
            d  = lastMarked.getSignal();
            am = -1;

            for(int z=c+1; z < size(); z++)
            {
                a = get(z);
                if(a.isBasedMarked())
                {
                    //Log.d("Valentine", mId + " Break loop c " + c + " Becomes " + z);
                    c = z;
                    break;
                }
                else
                {
                    if(a.isValid())
                    {
                        if(a.getSignal() > d)
                        {
                            //Log.d("Valentine", mId + " New Max signal " + d + " Becomes " +  a.getSignal());
                            am = z;
                            d = a.getSignal();
                        }
                    }
                    else
                        c = z;
                }
            }

            if(am > 0)
            {
                // distance computation
                get(am).setBaseMark();
                Location.distanceBetween(lastMarked.lat, lastMarked.lon, get(am).lat, get(am).lon, mResults);

                maxD = Math.max(maxD, ((int) mResults[0]));
            }
        }

        return maxD;
    }

    // mark the spacing

    private void markSpacing()
    {
        LoggedAlert a, lastMarked;
        int c = mFirstValid, d, nba;

        while(c < size() - 1)
        {
            lastMarked = get(c);
            nba        = 0;
            for(int z=c+1; z < size(); z++)
            {
                a = get(z);

                if(a.isBasedMarked())
                {
                    // compute distance
                    Location.distanceBetween(lastMarked.lat, lastMarked.lon, a.lat, a.lon, mResults);

                    if( (int) mResults[0] >= AlertHistoryActivity.MIN_MARK_SPACE && nba > 0)
                        markSpaced(c, z, AlertHistoryActivity.MIN_MARK_SPACE);
                    c = z;
                    break;
                }
                else
                {
                    if(a.isValid())
                        ++nba;
                    else
                        c = z;
                }
            }
        }
    }

    // set a base mark every X meters between 2 marked alerts

    private void markSpaced(int start, int end, int space)
    {
        LoggedAlert lastMarked = get(start), a;

        for(int i=start+1; i< end; i++)
        {
            a = get(i);
            // compute the distance
            Location.distanceBetween(lastMarked.lat, lastMarked.lon, a.lat, a.lon, mResults);
            if( (int) mResults[0] >= MIN_ARROW_DISTANCE)
            {
                a.setBaseMark();
                lastMarked = a;
            }
        }
    }

}
