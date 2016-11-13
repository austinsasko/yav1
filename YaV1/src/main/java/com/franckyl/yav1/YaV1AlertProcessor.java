package com.franckyl.yav1;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.franckyl.yav1.utils.BandRangeList;
import com.franckyl.yav1.utils.CandidateAlert;
import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1lib.YaV1AlertList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by franck on 7/18/13.
 */
public class YaV1AlertProcessor
{
    // Manual action

    public  static final int    MANUAL_OPTION__NONE            = 0;
    public  static final int    MANUAL_OPTION__WHITE           = 1;
    public  static final int    MANUAL_OPTION_REMOVE_LEARNING  = 2;

    // for checking if an alert contains white | lockout | can be manually added
    public  static final int    CHECK_FOR_MANUAL    = (YaV1Alert.PROP_CHECKABLE | YaV1Alert.PROP_LOCKOUT | YaV1Alert.PROP_WHITE);
    public  static final int    CHECK_FOR_LOCKWHITE = (YaV1Alert.PROP_LOCKOUT | YaV1Alert.PROP_WHITE);

    public  static      boolean     sSearchIfLost   = false;

    // the persistent alert list
    private ArrayList<YaV1PersistentAlert> mPersist               = new ArrayList<YaV1PersistentAlert>();

    // same as above but we use the SparseArray for fast access

    private SparseArray<YaV1PersistentAlert> mFastPersist         = new SparseArray<YaV1PersistentAlert>(50);

    // the lockout to unset busy when the alert are over
    private ArrayList<Integer>             mLockoutOver           = new ArrayList<Integer>();

    // the lockout excluded
    private ArrayList<Pair<Integer, Integer>>[] mExcludedLockout  = null;

    // last number of alerts
    private int                            mLastNumber       = 0;

    // number of new alerts
    private int                            mNbNew            = 0;

    // number of new muted alert
    // private int                            mNbNewMuted       = 0;

    // number of alert that can be manually changed
    private int                            mNbManualOverride = 0;

    // laser flag
    private boolean                        mHadLaser         = false;
    private boolean                        mHasLaser         = false;

    // search lockout enabled ?
    private boolean                        mLockoutAvailable = false;

    // used for manual lockout/white list

    private AtomicInteger            mManualId         = new AtomicInteger(0);
    private AtomicInteger            mManualOption     = new AtomicInteger(0);

    private AtomicBoolean            mManualAvailable  = new AtomicBoolean(true);

    // last time called
    private long                     mLastCall         = 0;

    private SparseArray<List<CandidateAlert>> mCandidateAlert = new SparseArray<List<CandidateAlert>>(16);
    private SparseArray<YaV1PersistentAlert>  mMatchPersist   = new SparseArray<YaV1PersistentAlert>(16);
    private SparseArray<List<CandidateAlert>> mHistoCandidate = new SparseArray<List<CandidateAlert>>(16);

    // constructor

    public YaV1AlertProcessor()
    {
        for(int i = 0; i < 16; i++)
        {
            mCandidateAlert.put(i, null);
            mMatchPersist.put(i, null);
        }
    }

    // when we stop, we clear the persist list

    public void stop()
    {
        mPersist.clear();
    }

    // number of new alert

    public int getNewCount()
    {
        return mNbNew;
    }

    // get the number of alert in this packet that can be sow in dialog

    public int getOverrideAbleAlert()
    {
        return mNbManualOverride;
    }

    // our persistent alert order, most recent first

    private static Comparator<YaV1PersistentAlert> comparePersistent = new Comparator<YaV1PersistentAlert>()
    {
        public int compare(YaV1PersistentAlert a1, YaV1PersistentAlert a2)
        {
            int r = (int) (a2.mLastTime - a1.mLastTime);

            if(r == 0)
                r = a1.mMissed - a2.mMissed;

            /*
            if(r== 0)
                r = a1.mId - a2.mId;
            */
            return r;
        }
    };

    // process the alerts

    public boolean processAlert(YaV1AlertList al, int tnNumber)
    {
        int         nbAlert   = al.size();
        boolean     rc        = mLastNumber != nbAlert;
        YaV1Alert   a;
        int         diff      = 0;
        boolean     isNew     = false;
        boolean     check     = false;
        mNbNew                = 0;
        int         iniProperty;
        int         lastX;

        YaV1PersistentAlert pa;

        // this will be our last number
        mLastNumber       = nbAlert;
        mHasLaser         = false;

        // we need this here, in order to be able to update the unseen alert
        mLockoutAvailable  = false;

        // No new alert

        mNbManualOverride = 0;
        mNbNew            = 0;

        // have we got a last call > Time to live for alert ?
        boolean hasTimedOut = mLastCall > 0 && (SystemClock.elapsedRealtime() - mLastCall) > YaV1PersistentAlert.PERSISTENT_TTL;

        if(!YaV1.mV1Client.isLibraryInDemoMode())
        {
            if(YaV1CurrentPosition.sLockout && YaV1.sAutoLockout != null)
            {
                if(hasTimedOut && YaV1.sAutoLockout.isNormal())
                    YaV1.sAutoLockout.handleTimeout();
                mLockoutAvailable = YaV1.sAutoLockout.checkCurrentArea();
            }
        }

        // reset the last time called
        mLastCall = SystemClock.elapsedRealtime();

        // get the eventual manual lockout

        int     manualUpd    = 0;
        int     manualOption = 0;

        // check if a manual lockout has happen

        if(mLockoutAvailable)
        {
            // we set a flag to avoid firing too fast
            manualUpd    = mManualId.getAndSet(0);
            manualOption = mManualOption.getAndSet(0);
        }
        else
        {
            if(!YaV1.sInTestingMode)
            {
                // we reset the flag for having the demo
                manualUpd    = 0;
                manualOption = 0;
            }
        }

        // sort by last time seen (might not be necessary)

        if(mPersist.size() > 1)
            Collections.sort(mPersist, comparePersistent);

        // reset the persistent candidate

        for(YaV1PersistentAlert px: mPersist)
        {
            // check for candidate
            px.resetCandidate(mLastCall);

            // manual lockout
            if(manualUpd > 0 && px.mId == manualUpd)
            {
                if(manualOption == MANUAL_OPTION_REMOVE_LEARNING)
                {
                    // we got to remove the manual id
                    if(px.mLockoutId > 0)
                    {
                        YaV1.sAutoLockout.removeLockout(px.mLockoutId);
                        resetManualLockoutProperty(px);
                    }
                }
                else
                {
                    // create the lockout and set the id (we might have an id)
                    if(YaV1.sAutoLockout.setManual(px, manualOption == MANUAL_OPTION__WHITE))
                    {
                        setManualLockoutProperty(px);
                    }
                }

                mManualAvailable.set(true);
                rc = true;
            }
            else if(manualUpd < 0 && px.mLockoutId == -manualUpd)
            {
                if(YaV1.sAutoLockout.removeLockout(px.mLockoutId))
                {
                    resetManualLockoutProperty(px);
                }
                mManualAvailable.set(true);
                rc = true;
            }
        }

        if(nbAlert > 0)
        {
            // affect the alert to the live one
            if(newProcess(al, tnNumber))
                rc = true;
        }


        // increase the missed account

        for(YaV1PersistentAlert px: mPersist)
        {
            if((px.mFlag & px.PERSISTENT_CANDIDATE) > 0)
                px.mMissed++;
        }

        // if we have laser and was not before, we set the laser voice

        if(mHasLaser)
        {
            if(!mHadLaser)
            {
                YaV1SoundManager.mCurrentLaserVoice++;
                mHadLaser = true;
            }
        }
        else
            mHadLaser = false;

        if(YaV1.sInTestingMode && YaV1.mV1Client.isLibraryInDemoMode())
        {
            // reset the manual available
            mManualAvailable.set(true);
        }
        else
        {
            if(manualUpd > 0 && !mManualAvailable.get())
            {
                //Log.d("Valentine lockout", "A manual lockout could not be done, too late");
                mManualAvailable.set(true);
            }
        }

        // check for closure
        checkClose();

        return rc;
    }

    private boolean newProcess(YaV1AlertList al, int tnNumber)
    {
        boolean rc       = false;
        int     nbAlert  = al.size();
        int     iniProperty;
        int     diff;
        int     lastColor;

        YaV1Alert           a;
        YaV1PersistentAlert pa;

        mHistoCandidate.clear();
        List<CandidateAlert> ll;

        int i = 0;
        // set the candidate
        for(; i < 16; i++)
        {
            mCandidateAlert.put(i, null);
            mMatchPersist.put(i, null);
        }

        // loop for the candidate

        for(i = 0; i < nbAlert; i++)
        {
            try
            {
                a = al.get(i);
                a.setTn(tnNumber);

                if(a.isLaser())
                {
                    mHasLaser = true;
                    continue;
                }

                List<CandidateAlert> li = new ArrayList<CandidateAlert>();

                // check among the list if we have a match
                for(YaV1PersistentAlert px: mPersist)
                {
                    if(px.isCandidate(a))
                    {
                        CandidateAlert ca = new CandidateAlert(a, i, px);
                        li.add(ca);
                        ll = mHistoCandidate.get(px.mId);
                        if(ll == null)
                        {
                            ll = new ArrayList<CandidateAlert>();
                            mHistoCandidate.put(px.mId, ll);
                        }
                        ll.add(ca);
                    }
                }

                // sort the candidate alert list
                if(li.size() > 0)
                {
                    if(li.size() > 1)
                        Collections.sort(li, CandidateAlert.mCompareAlert);
                }
                mCandidateAlert.put(i, li);
            }
            catch(ArrayIndexOutOfBoundsException exc)
            {
                Log.d("Valentine Tracking", "Alert -- first loop --  out of bound exception index " + i + " Size " + nbAlert + " Exc " + exc.toString());
                YaV1.DbgLog("Valentine Tracking, Alert -- first loop -- out of bound exception index " + i + " Size " + nbAlert + " Exc " + exc.toString());
            }
        }

        // check the affectation
        while(!solveAffect(al));

        //Log.d("Valentine Tracking", "Alert solved");

        for(i=0; i < nbAlert; i++)
        {
            try
            {
                a = al.get(i);
                pa = mMatchPersist.get(i);

                if(a.isLaser())
                {
                    iniProperty = AlertProcessorParam.applyParam(a, mLockoutAvailable);
                    a.setProperty(iniProperty);

                    // check for logging
                    if((iniProperty & YaV1Alert.PROP_LOG) > 0)
                        YaV1.sLog.log(a);

                    continue;
                }

                if(pa != null)
                {
                    if((diff = pa.isSame(a)) > 0)
                    {
                        if(diff > 1)
                            rc = true;
                    }

                    // reset the priority property

                    if((a.getProperty() & a.PROP_PRIORITY) <= 0)
                        pa.mPersistentProperty = (pa.mPersistentProperty & ~(a.PROP_PRIORITY));

                    // find the next cap (for all alerts for now)

                    if(pa.mPreviousCap >= 0 && pa.mNextCap < 0 && mLockoutAvailable && YaV1.sAutoLockout.mCurrentAreaRevision > pa.mAreaRevision)
                    {
                        if(YaV1.sAutoLockout.hasMajorChange(pa) > 0)
                        {
                            if(pa.mNextCap > pa.mPreviousCap && pa.mLockoutId > 0)
                            {
                                // we would update the lockout param2
                                YaV1.sAutoLockout.updateParam2(pa);
                            }
                        }
                    }
                }
                else
                {
                    // create the persistent alert
                    mNbNew++;
                    YaV1CurrentView.sNewAlert[a.getBand()]++;

                    //iniProperty = getAllProp(a.getBand(), a.getFrequency(), a.getSignal(), a.getProperty());
                    iniProperty = AlertProcessorParam.applyParam(a, mLockoutAvailable);
                    pa = new YaV1PersistentAlert(a, iniProperty);
                    mPersist.add(pa);
                    mFastPersist.put(pa.mId, pa);
                    a.setProperty(iniProperty);
                    // store the box number
                    pa.mBoxNumber = AlertProcessorParam.getLastBox();
                    // store the color
                    lastColor = pa.mColor = AlertProcessorParam.getLastColor();

                    // we search for lockout

                    if((iniProperty & a.PROP_CHECKABLE) > 0)
                    {
                        YaV1.sAutoLockout.search(pa);

                        // the lockout process did change the background color, we set the frequency color to the box color (if any)
                        if(lastColor != Color.TRANSPARENT && lastColor != pa.mColor)
                            pa.mTextColor = lastColor;

                        adjustFlagLockout(pa);
                    }

                    if((pa.mPersistentProperty & a.PROP_INBOX) > 0)
                        YaV1SoundManager.mCurrentBoxAlert[pa.mBand-1][pa.mBoxNumber]++;

                    rc = true;
                }

                // affect the property and check
                // set the property to the alert

                a.setProperty(pa.mPersistentProperty);
                a.setColor(pa.mColor);
                a.setTextColor(pa.mTextColor);

                // if lockout or white, we set the lockout id * -1
                if((pa.mPersistentProperty & CHECK_FOR_LOCKWHITE) > 0)
                {
                    a.setPersistentId(-pa.mLockoutId);
                }
                else
                {
                    if((pa.mPersistentProperty & a.PROP_CHECKABLE) > 0)
                        a.setPersistentId(pa.mId);
                }

                if(!YaV1.mV1Client.isLibraryInDemoMode())
                {
                    if(( (pa.mPersistentProperty & CHECK_FOR_MANUAL) > 0) || (YaV1CurrentPosition.sCollector && pa.mBand != YaV1Alert.BAND_LASER))
                        mNbManualOverride++;

                    // check for logging
                    if((pa.mPersistentProperty & YaV1Alert.PROP_LOG) > 0)
                        YaV1.sLog.log(pa, a);
                }
                else
                {
                    if(YaV1.sInTestingMode)
                    {
                        // to remove after test
                        mNbManualOverride++;
                        int tmp = (int) SystemClock.elapsedRealtimeNanos();
                        a.setPersistentId( (tmp % 2 == 0 ? -tmp : tmp));
                    }
                }
            }
            catch(IndexOutOfBoundsException exc)
            {
                Log.d("Valentine Tracking", "Alert -- second loop --  out of bound exception index " + i + " Size " + nbAlert + " Exc " + exc.toString());
                YaV1.DbgLog("Valentine Tracking, Alert -- second loop -- out of bound exception index " + i + " Size " + nbAlert + " Exc " + exc.toString());
            }
        }

        return rc;
    }

    // make the match of the incoming alert on existing

    private boolean solveAffect(YaV1AlertList al)
    {
        // loop in the opposite way
        List<CandidateAlert> cl, hl;
        YaV1Alert a;
        int       nbc;
        int       hMatch;
        int       hMatchC;
        int       nbAlert = al.size();

        for(int i = 0; i < nbAlert; i++)
        {
            try
            {
                a = al.get(i);

                if(a.isLaser())
                    continue;

                cl = mCandidateAlert.get(i);
                if(cl == null || cl.size() < 1)
                    continue;

                // check the number of candidate for the histo we did choose
                hMatch  = cl.get(0).mHistoId;
                hl      = mHistoCandidate.get(hMatch);
                hMatchC = hl.size();

                // simple case cl.size =  1 and Histo n  = 1

                if(cl.size() == 1 && hMatchC == 1)
                {
                    // more simple to use direct clear / affect
                    //Log.d("Valentine Tracking", "Alert " + i + " Single match persist " + hMatch);
                    mMatchPersist.put(i, mFastPersist.get(hMatch));
                    cl.clear();
                    hl.clear();
                }
                else
                {
                    if(hMatchC > 1)
                    {
                        // we sort the collection for the best alert to fit in
                        Collections.sort(hl, CandidateAlert.mCompareHisto);
                    }

                    // get the first alert index that matches

                    int idx = hl.get(0).mAlertIndex;
                    //Log.d("Valentine Tracking", "Alert " + i + " Candidate " + cl.size() + " Histo size " + hMatchC);
                    affectHistoAlert(hMatch, idx);

                    // will cause a loop
                    if(i != idx)
                        return false;
                }
            }
            catch(ArrayIndexOutOfBoundsException exc)
            {
                Log.d("Valentine Tracking", "Alert -- solving --  out of bound exception index " + i + " Size " + nbAlert + " Exc " + exc.toString());
                YaV1.DbgLog("Valentine Tracking, Alert -- solving -- out of bound exception index " + i + " Size " + nbAlert + " Exc " + exc.toString());
                break;
            }
        }

        return true;
    }

    // affect an histo for an alert and adjust the counters

    private boolean affectHistoAlert(int histoID, int alertIndex)
    {
        //Log.d("Valentine Tracking", "Affecting alert " + alertIndex + " To Histo " + histoID);
        List<CandidateAlert> cl, hl, wcl;

        cl = mCandidateAlert.get(alertIndex);

        hl = mHistoCandidate.get(histoID);

        for(CandidateAlert ca: cl)
        {
            wcl = mHistoCandidate.get(ca.mHistoId);
            for(int i=0; i < wcl.size(); i++)
            {
                if(wcl.get(i).mAlertIndex == alertIndex)
                {
                    wcl.remove(i);
                    //Log.d("Valentine Tracking", "Remove alert " + alertIndex + " From Histo " + ca.mHistoId);
                    break;
                }
            }
        }

        // we can clear the list (it should be empty)

        cl.clear();

        // we remove this histo index from the eventual candidate

        for(CandidateAlert ca: hl)
        {
            wcl = mCandidateAlert.get(ca.mAlertIndex);
            for(int i=0; i < wcl.size(); i++)
            {
                if(wcl.get(i).mHistoId == histoID)
                {
                    wcl.remove(i);
                    break;
                }
            }
        }

        hl.clear();

        // we affect the histo to the alert
        mMatchPersist.put(alertIndex, mFastPersist.get(histoID));
        return true;
    }

    // set the manual lockout - white

    public boolean setManualLockUnlock(int lid, int option)
    {
        if(mManualAvailable.getAndSet(true))
        {
            mManualId.set(lid);
            mManualOption.set(option);
            return true;
        }

        return false;
    }

    public boolean isManualAvailable()
    {
        return mManualAvailable.get();
    }

    // set/reset property according to lockout / white

    private boolean setManualLockoutProperty(YaV1PersistentAlert p)
    {
        boolean rc = true;

        if((p.mPersistentProperty & YaV1Alert.PROP_LOCKOUT) > 0)
        {
            p.mPersistentProperty |= YaV1Alert.PROP_MUTE;

            if(AlertProcessorParam.getBandParam(p.mBand).mExcludeLogLockout)
                p.mPersistentProperty = (p.mPersistentProperty & (~YaV1Alert.PROP_LOG));

            p.mPersistentProperty = (p.mPersistentProperty & (~(YaV1Alert.PROP_INBOX | YaV1Alert.PROP_CHECKABLE)));
        }
        else if( (p.mPersistentProperty & YaV1Alert.PROP_WHITE) > 0)
        {
            p.mPersistentProperty = (p.mPersistentProperty & (~(YaV1Alert.PROP_INBOX | YaV1Alert.PROP_CHECKABLE)));
        }
        else
            rc = false;

        return rc;
    }

    // reset the manual property of lockout - white

    private boolean resetManualLockoutProperty(YaV1PersistentAlert p)
    {
        p.mPersistentProperty = (p.mPersistentProperty & (~(YaV1Alert.PROP_WHITE | YaV1Alert.PROP_LOCKOUT | YaV1Alert.PROP_MUTE)));
        // reset the color
        p.mColor     = Color.TRANSPARENT;
        // lockout is gone
        p.mLockoutId = 0;
        return true;
    }

    // check the alerts that should be closed

    private void checkClose()
    {
        YaV1PersistentAlert pa;
        long l = SystemClock.elapsedRealtime();

        mLockoutOver.clear();

        for(int i=mPersist.size()-1; i>=0; i--)
        {
            pa = mPersist.get(i);
            // check if not completed
            if((pa.mFlag & pa.PERSISTENT_CLOSED) > 0)
            {
                mFastPersist.remove(pa.mId);
                // remove it
                mPersist.remove(i);
                // check if we should clear the busy flag in lockout
                if(pa.mLockoutId > 0)
                    mLockoutOver.add(new Integer(pa.mLockoutId));
            }
        }

        if(mLockoutOver.size() > 0)
            YaV1.sAutoLockout.resetBusy(mLockoutOver);
    }

    // adjust the flag when lockout

    public void adjustFlagLockout(YaV1PersistentAlert pa)
    {
        if((pa.mPersistentProperty & CHECK_FOR_LOCKWHITE) > 0)
        {
            if((pa.mPersistentProperty & YaV1Alert.PROP_LOCKOUT) > 0)
            {
                pa.mPersistentProperty |= YaV1Alert.PROP_MUTE;

                if(AlertProcessorParam.getBandParam(pa.mBand).mExcludeLogLockout)
                    pa.mPersistentProperty = (pa.mPersistentProperty & (~YaV1Alert.PROP_LOG));
            }

            pa.mPersistentProperty = (pa.mPersistentProperty & (~(YaV1Alert.PROP_INBOX | YaV1Alert.PROP_CHECKABLE)));
        }
    }
}
