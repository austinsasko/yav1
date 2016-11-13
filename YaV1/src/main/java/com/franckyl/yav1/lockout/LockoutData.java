package com.franckyl.yav1.lockout;

import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.os.SystemClock;
import android.util.Log;

import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.YaV1PersistentAlert;
import com.franckyl.yav1.utils.YaV1GpsPos;
import com.franckyl.yav1lib.YaV1Alert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by franck on 2/27/14.
 */
public class LockoutData
{
    // mode - Disabled - Frozen - Normal
    public static int MODE_NORMAL   = 0;
    public static int MODE_FROZEN   = 1;
    public static int MODE_DISABLED = 2;

    // we keep the id's here

    public static int mLockoutId    = 0;

    // current mode

    public static  int mMode         = MODE_NORMAL;

    // file name
    private LockoutDb mDb           = null;

    // the number of lockout and learning one

    public static int   sLockoutCount     = 0;
    public static int   sLockoutLearning  = 0;

    // the locks

    private ReentrantReadWriteLock mReadWriteLock = null;
    private Lock read                             = null;
    private Lock write                            = null;
    private boolean mUsable                       = false;

    // Revision of the short list
    private int         mRevision                  = 0;

    // we stick a revision number to the current area update

    public  int         mCurrentAreaRevision       = 0;

    // the list in this area
    private LockoutList mAreaList                  = new LockoutList();

    // the current area
    private LockoutArea               mCurrentArea  = new LockoutArea();

    // last checked place
    private double                    mCheckLat = Double.NaN;
    private double                    mCheckLon = Double.NaN;
    private int                       mCheckCap = 0;

    private int                       mLastCap      = 0;

    // the blocking queue for saving
    private BlockingQueue<Lockout>    mChangedQueue = null;

    // the Db Update there
    private updateDbThread            mUpdateThread = null;

    // location computation

    private float mResults[]                        = {0, 0, 0};

    private Context  mContext                       = null;
    private boolean  mInShortList                   = false;
    private ArrayList<Lockout> mPendingArea         = new ArrayList<Lockout>();


    // flag used when Gps lost
    public  static AtomicBoolean      mGpsLost      = new AtomicBoolean(false);

    // for testing

    public  static int        sAngle                  = 45;
    public  static int        sAngleVisible           = 40;
    public  static int        sDistanceMustBeVisible  = 50;
    public  static YaV1GpsPos sLastMajorCapChange     = null;
    public  static int        sLastMajorRevision      = 0;
    public  static int        sLastMajorAngle         = 0;
    public  static float      sDistanceCapChange      = 50f;
    public  static int        sLastMajorCap           = 0;

    // constructor

    public LockoutData(boolean reset)
    {
        mUsable        = false;
        mAreaList.clear();

        mReadWriteLock = new ReentrantReadWriteLock();
        read           = mReadWriteLock.readLock();
        write          = mReadWriteLock.writeLock();

        // revision of the short list is 0
        mRevision      = 0;
        mDb            = new LockoutDb(YaV1.sContext);

        mDb.open();

        if(reset)
        {
            mDb.reset();
        }

        // create the queue for update
        mChangedQueue = new ArrayBlockingQueue<Lockout>(30);

        // start the update thread

        mUpdateThread  = new updateDbThread();
        mUpdateThread.start();

        mPendingArea.clear();
        mCurrentArea.clear();

        // make sure we refresh the current area

        mCheckLat     = Double.NaN;
    }

    // reset the DB

    public void reset()
    {
        mUsable = false;

        // stop the update thread
        if(mUpdateThread != null && mUpdateThread.isAlive())
            mUpdateThread.interrupt();

        // reset the Db
        mDb.reset();

        // force revision to 0
        mRevision = 0;

        mPendingArea.clear();
        mCurrentArea.clear();

        // force the reload of the current area
        mCheckLat  = Double.NaN;

        // re start the update thread
        mUpdateThread  = new updateDbThread();
        mUpdateThread.start();
    }

    // normal mode

    public boolean isNormal()
    {
        return mUsable && mMode == MODE_NORMAL;
    }

    // Disabled ?

    public boolean isDisabled()
    {
        return mUsable && mMode == MODE_DISABLED;
    }

    // get the lockout count

    public boolean getLockoutCount()
    {
        return mDb.getCount();
    }

    // when stopping

    public void endLockout()
    {
        // we stop the update thread
        mUsable = false;

        // stop the update thread

        if(mUpdateThread != null && mUpdateThread.isAlive())
            mUpdateThread.interrupt();

        while(mInShortList)
        {
            // we wait for ending
            try
            {
                Thread.sleep(200);
            }
            catch(InterruptedException e)
            {

            }
        }

        // we will do a final save of the current area

        read.lock();
        try
        {
            mDb.finalSave(mCurrentArea);
        }
        finally
        {
            read.unlock();
        }

        mDb.close();
    }

    // check if we can update the current area

    public boolean checkCurrentArea()
    {
        if(mUsable && mMode != MODE_DISABLED && YaV1CurrentPosition.isValid)
        {
            if(sLastMajorCapChange == null)
                sLastMajorCapChange = YaV1CurrentPosition.getPos();

            // check only if we changed place at least 5 meters

            boolean check = Double.isNaN(mCheckLat);
            if(!check)
            {
                Location.distanceBetween(mCheckLat, mCheckLon, YaV1CurrentPosition.lat, YaV1CurrentPosition.lon, mResults);
                check = mResults[0] >= 5;
            }

            // area did not change, we can search on the current one

            if(!check)
                return true;

            // lock for the loop (in case the shortList is being refresh
            read.lock();

            // update our last value

            mLastCap = mCheckCap;

            // would be our last check value

            mCheckLat = YaV1CurrentPosition.lat;
            mCheckLon = YaV1CurrentPosition.lon;
            mCheckCap = YaV1CurrentPosition.bearing;

            mCurrentAreaRevision++;

            int angleCap;

            // update last major change cap, is it now ?
            if((angleCap = YaV1CurrentPosition.getAngle(mCheckCap, mLastCap)) >= 20)
            {
                // distance since major change
                Location.distanceBetween(mCheckLat, mCheckLon, sLastMajorCapChange.lat, sLastMajorCapChange.lon, mResults);
                sLastMajorCapChange = YaV1CurrentPosition.getPos();
                sLastMajorRevision  = mCurrentAreaRevision;
                sLastMajorAngle     = angleCap;
                sLastMajorCap       = mLastCap;
            }

            try
            {
                updateCurrentArea();
            }
            finally
            {
                read.unlock();
            }

            return true;
        }

        return false;
    }

    // check the last major cap changed

    public int hasMajorChange(YaV1PersistentAlert px)
    {
        if(px.mNbHit == 1)
        {
            Location.distanceBetween(sLastMajorCapChange.lat, sLastMajorCapChange.lon, px.mInitialPosition.lat, px.mInitialPosition.lon, mResults);
            if(mResults[0] <= sDistanceCapChange)
                px.mPreviousCap = sLastMajorAngle;
            else
                px.mPreviousCap = 0;
        }
        else
        {
            Location.distanceBetween(mCheckLat, mCheckLon, px.mInitialPosition.lat, px.mInitialPosition.lon, mResults);

            // if we did not get a cap change and we have done 50 meters, we will not consider changing cap
            if(mResults[0] > sDistanceCapChange)
            {
                px.mNextCap = 0;
            }
            else
            {
                if(sLastMajorRevision > px.mAreaRevision)
                {
                    px.mNextCap = sLastMajorAngle;
                    return 1;
                }
            }
        }

        return 0;
    }

    // get next id for lockout

    static public int getNextLockoutId()
    {
        return ++mLockoutId;
    }

    // handle a V1 stop more than 10 sec

    public void handleTimeout()
    {
        mDb.finalSave(mCurrentArea);
        // clear the current Area
        mCurrentArea.clear();
        mCheckLat = Double.NaN;
    }

    // build the current area (no previous give)

    private void updateCurrentArea()
    {
        int     id;
        int     angle;
        Lockout inL;
        int     sB;
        int     dist;

        boolean KaOnly = (YaV1.sModeData != null && YaV1.sModeData.isEuroLogic());

        // loop over the short list

        for(Lockout l: mAreaList.values())
        {
            id = l.getId();
            Location.distanceBetween(mCheckLat, mCheckLon, l.mLat, l.mLon, mResults);
            dist = (int) mResults[0];

            if(dist <= LockoutParam.mRadius)
            {
                angle     = YaV1CurrentPosition.getAngle(l.mBearing, mCheckCap);
                sB        = (angle <= sAngleVisible && dist <= sDistanceMustBeVisible ? 1 : 0);

                if(KaOnly && YaV1.BandBoundaries.getIntBandFromFrequency(l.mFrequency) == YaV1Alert.BAND_K)
                    sB = 0;

                if(mCurrentArea.indexOfKey(id) >= 0)
                {
                    inL = mCurrentArea.get(id);
                    inL.mNbCheck++;
                    inL.mAngle    = angle;
                    inL.mDistance = (int) mResults[0];
                    inL.mShouldBe += sB;
                    // we have it in the current area, mark it
                    inL.mFlag     |= l.LOCKOUT_TMP_KEEP;
                }
                else
                {
                    l.resetOnEnterCurrentArea(sB, angle, (int) mResults[0]);
                    l.mFlag |= l.LOCKOUT_TMP_KEEP;
                    mCurrentArea.put(id, l);
                }
            }
        }

        // check the current area Lockout

        int k;
        Lockout l;

        for(int i = mCurrentArea.size() - 1; i >=0; i--)
        {
            l = mCurrentArea.valueAt(i);
            k = mCurrentArea.keyAt(i);

            // if new we check if it's in the new area, we keep it
            if( (l.mFlag & l.LOCKOUT_TMP_KEEP) > 0)
            {
                l.mFlag = l.mFlag & (~(l.LOCKOUT_TMP_KEEP));
            }
            else
            {
                if(l.checkForUpdate())
                {
                    // we would Save in DB
                    mChangedQueue.add(l);

                    // remove from short list
                    if( (l.mFlag & l.LOCKOUT_REMOVE) > 0)
                    {
                        mAreaList.remove(k);
                    }
                }

                // remove from current area
                mCurrentArea.remove(k);
            }
        }
    }

    // get the revision

    public int getRevision()
    {
        return mRevision;
    }

    // we update the paran2 (when an alert had a major turn within the 50 meters after it started

    public boolean updateParam2(YaV1PersistentAlert a)
    {
        if(a.mLockoutId > 0)
        {
            read.lock();

            try
            {
                Lockout l = mCurrentArea.get(a.mLockoutId);

                if(l != null)
                {
                    l.mParam2 = Math.max(a.mPreviousCap, a.mNextCap);
                    l.mFlag |= l.LOCKOUT_UPDATE;
                }
                else
                {
                    // we search in the area list
                    l = mAreaList.get(a.mLockoutId);

                    if(l != null)
                    {
                        l.mParam2 = Math.max(a.mPreviousCap, a.mNextCap);
                        l.mFlag |= l.LOCKOUT_UPDATE;
                        mChangedQueue.add(l);
                    }
                }
            }
            finally
            {
                read.unlock();
            }
        }

        return false;
    }

    // set an alert as "Manual"

    public boolean setManual(int lockoutId, boolean asWhite, boolean force)
    {
        if(!force && (!mUsable || mMode == MODE_DISABLED))
            return false;

        boolean rc = false;
        read.lock();

        try
        {
            Lockout l = mCurrentArea.get(lockoutId);

            if(l != null)
            {
                // force the lockout, then it will update on DB when we exit the area
                if(asWhite)
                    l.forceWhite();
                else
                    l.forceLockout();
                rc = true;
            }
            else
            {
                // find in short list
                l = mAreaList.get(lockoutId);

                if(l != null)
                {
                    if(asWhite)
                        l.forceWhite();
                    else
                        l.forceLockout();
                    // replace in short list
                    mAreaList.put(l.getId(), l);
                    // queue for update
                    mChangedQueue.add(l);
                    rc = true;
                }
            }
        }
        finally
        {
            read.unlock();
        }

        return rc;
    }

    // set a manual lockout from the Persistent aler

    public boolean setManual(YaV1PersistentAlert a, boolean asWhite)
    {
        if(a.mLockoutId > 0)
        {
            if(setManual(a.mLockoutId, asWhite, false))
            {
                a.mPersistentProperty |= (asWhite ? YaV1Alert.PROP_WHITE : YaV1Alert.PROP_LOCKOUT);
                a.mColor = (asWhite ? LockoutParam.mWhiteLockoutColor : LockoutParam.mManualLockoutColor);
                return true;
            }

            return false;
        }

        read.lock();

        try
        {
            int id = getNextLockoutId();
            // we create a new lockout, with the current position
            Lockout n = new Lockout(id, a);

            if(asWhite)
                n.forceWhite();
            else
                n.forceLockout();

            // we add it to the short list
            mAreaList.put(id, n);

            // add to the current Area
            mCurrentArea.put(id, n);

            // color and property

            a.mPersistentProperty |= (asWhite ? YaV1Alert.PROP_WHITE : YaV1Alert.PROP_LOCKOUT);
            a.mColor = (asWhite ? LockoutParam.mWhiteLockoutColor : LockoutParam.mManualLockoutColor);

            // always set it
            a.mLockoutId = id;
        }
        finally
        {
            read.unlock();
        }

        return true;
    }

    // manually remove a lockout

    public boolean removeLockout(int lockoutId)
    {
        if(!mUsable || mMode == MODE_DISABLED)
            return false;

        boolean rc = false;
        read.lock();

        try
        {
            Lockout l = mCurrentArea.get(lockoutId, null);

            if(l != null)
            {
                // remove from current area
                mCurrentArea.remove(lockoutId);
            }
            else
            {
                l = mAreaList.get(lockoutId);
            }

            if(l != null)
            {
                // remove from shortlist
                mAreaList.remove(lockoutId);
                // force the flag for removal in Db
                l.mFlag = l.LOCKOUT_REMOVE;
                // update the DB
                mChangedQueue.add(l);
                rc = true;
            }
        }
        finally
        {
            read.unlock();
        }

        return rc;
    }

    // get a lockout for the management

    public Lockout getLockoutForManagement(int lockoutId)
    {
        Lockout l = null;

        read.lock();

        try
        {
            //search in the current area (should not happen)
            l = mCurrentArea.get(lockoutId, null);

            // search in short list (that should not happen)

            if(l == null)
            {
                l = mAreaList.get(lockoutId);
            }

            // read from DB

            if(l == null)
            {
                l = mDb.readLockout(lockoutId);
            }
        }
        finally
        {
            read.unlock();
        }

        return l;
    }

    // remove the lockout from the logged alert

    public boolean removeLockout(Lockout l)
    {
        boolean rc = removeLockout(l.getId());

        // we would remove from DB directly
        if(!rc)
        {
            l.mFlag = l.LOCKOUT_REMOVE;
            // update the DB
            mChangedQueue.add(l);
            rc = true;
        }

        return rc;
    }

    // manual set from logged alert

    public boolean setManual(Lockout l, boolean asWhite)
    {
        boolean rc = setManual(l.getId(), asWhite, true);

        if(!rc)
        {
            if(asWhite)
                l.forceWhite();
            else
                l.forceLockout();
            // queue for update
            mChangedQueue.add(l);
        }

        return rc;
    }
    // called when an alert is over, to clear the busy Flag (TODO: check on stopping)

    public void resetBusy(List<Integer> mIds)
    {
        // if the alert is still in current are, we do not clear the flag
        if(!mUsable || mMode == MODE_DISABLED)
            return;

        read.lock();

        try
        {
            Lockout l;
            int alertId;

            for(int i=0; i<mIds.size(); i++)
            {
                alertId = mIds.get(i);
                l = mCurrentArea.get(alertId);

                if(l == null)
                {
                    l = mAreaList.get(alertId);

                    if(l != null)
                    {
                        l.resetBusyNotInArea();
                    }
                }
                else
                {
                    // this would be clear when get out of the current Area
                    if(LockoutParam.mAllowRemoveInCurrent)
                    {
                        l.resetBusyInArea();
                    }
                    else
                    {
                        l.setResetOnOut();
                    }
                }
            }
        }
        finally
        {
            read.unlock();
        }
    }

    // search an alert in the current area

    public void search(YaV1PersistentAlert a)
    {
        if(!mUsable || mMode == MODE_DISABLED)
            return;

        // we need a lock
        read.lock();
        try
        {
            // revision of the search
            a.mAreaRevision = mCurrentAreaRevision;

            // check the major change distance
            // hasMajorChange(a);

            boolean manual = (a.mPersistentProperty & YaV1Alert.PROP_LOCKOUT_M) > 0;
            boolean rc     = mCurrentArea.searchNoDirection(a, manual);

            if(!rc && mMode != MODE_FROZEN)
            {
                // if not found update the previous cap
                hasMajorChange(a);

                if(!manual)
                {
                    int id = getNextLockoutId();
                    // we create a new lockout
                    Lockout n = new Lockout(id, a);
                    // we add it to the short list
                    mAreaList.put(id, n);
                    // add to the current Area
                    mCurrentArea.put(id, n);
                    // always set it
                    a.mLockoutId = id;
                }
                else
                    a.mLockoutId = 0;
            }
        }
        finally
        {
            read.unlock();
        }
    }

    // read and make the current List, at current location

    public int makeShortList()
    {
        long t = SystemClock.elapsedRealtime();

        // we are in short list
        mInShortList = true;

        // we make a query
        LockoutList nl = mDb.readShortList();

        // check the updated in current short list that might not yet have been updated
        // we need a lock
        write.lock();
        try
        {
            mAreaList = nl;
            mInShortList = false;

            // this will force the update of the current area

            mCheckLat = Double.NaN;

            // report the Pending area
            for(Lockout a: mPendingArea)
            {
                // mAreaList.put(a.getId(), a);
                YaV1.DbgLog("Valentine Lockout, after shortlist restore id " + a.getId() + " Flag " + a.mFlag);

                // check if we have it in the new list
                Lockout l = mAreaList.get(a.getId());

                if(l != null)
                {
                    if(a.mSeen != l.mSeen || a.mMissed != l.mMissed)
                    {
                        if( (l.mFlag & l.LOCKOUT_NEW) > 0)
                            YaV1.DbgLog("MakeShortList Lockout id " + l.getId() + " in pending has flag New");
                        mAreaList.put(a.getId(), a);
                    }
                }
            }

            Lockout l;

            //
            // we reset the current area lockout in the area list
            // except the lockout to remove (should not happen)
            // This is for the busy flag
            //

            for(int i=0; i < mCurrentArea.size(); i++)
            {
                l = mCurrentArea.valueAt(i);
                if( (l.mFlag & l.LOCKOUT_REMOVE) < 0)
                {
                    if( (l.mFlag & l.LOCKOUT_NEW) > 0)
                        YaV1.DbgLog("MakeShortList Lockout id " + l.getId() + " in current area has flag New");
                    mAreaList.put(l.getId(), l);
                }
            }

            mPendingArea.clear();
        }
        finally
        {
             write.unlock();
        }

        // increase revision number
        ++mRevision;

        YaV1.DbgLog("Lockout short list size " + mAreaList.size());
        // first short list, now usable
        if(!mUsable)
            mUsable = true;

        return mRevision;
    }

    // Db change thread

    private class updateDbThread extends Thread
    {
        private boolean mRecordActive = false;
        Lockout mUpdateLockout;

        private void stopUpdate()
        {
            if(mRecordActive)
            {
                // to stop we push an id = -1
                mChangedQueue.add(new Lockout(-1));
            }
        }

        public void run()
        {
            mRecordActive = true;
            //Log.d("Valentine Lockout", "db Update thread started");
            while(true)
            {
                try
                {
                    mUpdateLockout = mChangedQueue.take();
                    if(!mUsable || mUpdateLockout.getId() <= 0)
                    {
                        break;
                    }

                    // we are building the short list, we "reserve" the area to update in the new short List
                    if(mInShortList)
                    {
                        // we add up the to the pending lockout to report list
                        mPendingArea.add(mUpdateLockout);
                        YaV1.DbgLog("Valentine lockout, update thread store lockout id " + mUpdateLockout.getId());
                    }

                    //
                    // Update the lockout
                    //

                    if((mUpdateLockout.mFlag & Lockout.LOCKOUT_REMOVE) > 0)
                        // maybe we do not delete when in short list, as it could come up again ?
                        mDb.deleteLockout(mUpdateLockout.getId());
                    else
                        mDb.updateLockout(mUpdateLockout, false);
                }
                catch(InterruptedException iex)
                {
                    break;
                }
            }

            mRecordActive = false;
            mChangedQueue.clear();
        }
    };
}
