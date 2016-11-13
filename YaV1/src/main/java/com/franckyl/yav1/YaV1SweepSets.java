package com.franckyl.yav1;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.franckyl.yav1.events.InfoEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.squareup.otto.Subscribe;
import com.valentine.esp.data.SweepDefinition;
import com.valentine.esp.data.SweepDefinitionIndex;
import com.valentine.esp.data.SweepSection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by franck on 7/31/13.
 */
public class YaV1SweepSets extends ArrayList<YaV1Sweep>
{
    public  static ArrayList<SweepSection>  sSweepSections    = YaV1.sV1Lookup.getV1DefaultSweepSections();
    public  static final int                MAX_SWEEP_NUMBER  = YaV1.sV1Lookup.getV1DefaultMaxSweepIndex() + 1;
    private static int                      sSweepId          = 0;

    private boolean                         mHasChanges       = false;
    private String                          mFileName         = "sweeps.json";
    private boolean                         mHasCurrent       = false;
    private int                             mCurrentId        = -1;
    private int                             mLastKnown        = -1;
    private String                          mLastError        = "";

    public  int                             mMergeDuplicate   = 0;
    private ProgressDialog                  mPd               = null;
    private int                             mCurrentPush      = 0;
    private String                          mError            = "";
    private Context                         mPushContext      = null;

    public  static int sNbRequestSweepSection                 = 0;

    // return the string for the SweepSection

    public static String getSectionString()
    {
        String str = "";
        int i =0;

        // reverse the list for having freqeuncy in order

        ArrayList<SweepSection> lsec = new ArrayList<SweepSection>();
        lsec.addAll(sSweepSections);
        Collections.reverse(lsec);

        for(SweepSection s: lsec)
        {
            str = str + (i > 0 ? "\n" : "") + s.getLowerFrequencyEdgeInteger() + "-" + s.getUpperFrequencyEdgeInteger();
            i++;
        }

        return str;
    }

    public static boolean checkSweep(int lower, int upper)
    {
        // check in section if we find lower/upper in a section
        for(SweepSection s: sSweepSections)
        {
            int l = s.getLowerFrequencyEdgeInteger();
            int u = s.getUpperFrequencyEdgeInteger();
            if(lower >= l && upper <= u)
                return true;
        }
        return false;
    }

    // set factory when no custom sweeps

    public void setDefaultFactory()
    {
        mCurrentId  = 0;
        mLastKnown  = 0;
        mHasCurrent = true;
    }

    // set no current (case pushing a USA profile after a Euro + CS)

    public void setNoCurrent()
    {
        mHasCurrent = false;
        mCurrentId  = -1;
    }

    // need the current sweeps ?

    public boolean hasCurrent()
    {
        return mHasCurrent;
    }

    // get the current position (return -1 if no current or factory default)

    public int getCurrentPosition()
    {
        int rc = 0;
        if(mCurrentId > 0)
        {
            for(YaV1Sweep s: this)
            {
                if(s.getId() == mCurrentId)
                    break;
                rc++;
            }
        }

        return rc - 1;
    }

    // get the sweeps pushable from quick list

    public List<String> getQuickSweepList()
    {
        List<String> l = new ArrayList<String>();
        for(YaV1Sweep s: this)
        {
            if(s.getId() > 0)
                l.add(s.getName());
        }

        return l;
    }

    // get the id from the quick list position

    public int getIdFromQuickListPosition(int position)
    {
        List<Integer> l = new ArrayList<Integer>();

        for(YaV1Sweep s: this)
        {
            if(s.getId() > 0)
                l.add(s.getId());
        }

        return l.get(position).intValue();
    }

    // get the current sweep

    public int getCurrentId()
    {
        return mCurrentId;
    }

    // get number of custom sweeps

    public int getNumber()
    {
        return Math.max(0, size() - 1);
    }

    // get the current sweep names

    public String getCurrentName()
    {
        if(mHasCurrent)
        {
            YaV1Sweep curr = getSweepFromId(mCurrentId);
            if(curr != null)
                return curr.getName();
        }

        return "";
    }
    // get the last known

    public int getLastKnown()
    {
        return mLastKnown;
    }

    // set the change flag

    public void hasChanged()
    {
        mHasChanges = true;
    }

    // set the current sweep id after push

    public void setCurrentSweepId(int iId)
    {
        mCurrentId = iId;
        mHasCurrent = true;
        mLastKnown  = iId;
        mHasChanges = true;
    }

    // update the current sweepSection

    public void setSweepSectionCallback(ArrayList<SweepSection> sections)
    {
        // update the sweep sections
        sSweepSections = sections;
        mHasChanges = true;
        sNbRequestSweepSection++;
        // request the sweep again
        YaV1.sInstance.getSweeps();
    }

    // set the current sweeps

    public void setCurrentSweepCallback(ArrayList<SweepDefinition> currSweep)
    {
        // in Demo mode we can have this
        if(currSweep == null)
            return;

        // for now we store this into a YaV1Sweep object with name "current"
        YaV1Sweep lSweep = new YaV1Sweep(++sSweepId, currSweep);

        // check if the set is valid
        if(!lSweep.isValid())
        {
            Log.d("Valentine", "Sweep invalid request again");
            // we request new sweep
            YaV1.sInstance.getSweeps();
            return;
        }

        mHasCurrent = false;

        // search this sweep in our list
        for(YaV1Sweep s: this)
        {
            if(s.isSame(lSweep))
            {
                mCurrentId  = s.getId();
                mHasCurrent = true;
                if(mLastKnown != mCurrentId)
                    mHasChanges = true;
                break;
            }
        }

        if(!mHasCurrent)
        {
            mHasChanges = true;
            lSweep.setName("Current sweep the " + android.text.format.DateFormat.format("yyyy-MM-dd_hh-mm-ss", new java.util.Date()));
            add(lSweep);
            mHasCurrent = true;
            mCurrentId  = lSweep.getId();
        }

        // will be the last known id

        mLastKnown  = mCurrentId;

        // broadcast the refresh of the info
        YaV1.postEvent(new InfoEvent(InfoEvent.Type.V1_INFO));
        save(false);
    }

    // push from the quick settings position

    public void pushFromQuickSweeps(int position, Context ctx)
    {
        // get the sweep from the "display" position (Factory not available)
        int id = getIdFromQuickListPosition(position);
        internalPush(id, ctx);
    }

    // push the sweep

    public boolean internalPush(int sweepId, final Context ctx)
    {
        final     YaV1Sweep s   = getSweepFromId(sweepId);
        boolean   rc            = false;

        if(s != null)
        {
            if(s.isFactoryDefault())
            {
                YaV1.mV1Client.setSweepsToDefault();
                setCurrentSweepId(s.getId());
                return true;
            }

            mPushContext = ctx;
            mError       = "";
            mCurrentPush = s.getId();

            mPd = new ProgressDialog(ctx);
            mPd.setIndeterminate(true);
            mPd.setCancelable(false);
            mPd.setTitle(R.string.sweep_push_title);
            mPd.setMessage(ctx.getText(R.string.sweep_push_message));
            mPd.show();

            // register this for finishing
            YaV1.getEventBus().register(this);
            final Object o = this;
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    YaV1.mV1Client.setCustomSweeps(s.getSweep(), o, "successSweepCallback", o, "failureSweepCallback");

                    // wait for the callback
                    while(mCurrentPush > 0)
                    {
                        try
                        {
                            Thread.sleep(500);
                        }
                        catch (InterruptedException e)
                        {

                        }
                    }

                    YaV1.postEvent(new InfoEvent(InfoEvent.Type.SWEEP_PUSHED));
                }
            }).start();
            rc = true;
        }

        return rc;
    }

    // success pushing sweep

    public void successSweepCallback(ArrayList<SweepDefinition> param)
    {
        // the pushed sweep becomes current
        setCurrentSweepId(mCurrentPush);

        // we might reload the sweeps since they can be change (adjusted) by V1
        YaV1Sweep sweep = getSweepFromId(mCurrentPush);

        // we create new Sweep fromm the callback (frequency might have been calibrated)

        YaV1Sweep nSweep = new YaV1Sweep(-1, param);
        if(!nSweep.isSame(sweep))
        {
            // adjust
            sweep.setSweepDefinition(param);
            hasChanged();
        }

        mError = "";
        mCurrentPush = -1;
    }

    // failure

    public void failureSweepCallback(String param)
    {
        mError = param;
    }

    @Subscribe
    public void pushFinished(InfoEvent event)
    {
        if(event.getType() == InfoEvent.Type.SWEEP_PUSHED)
        {
            ((Activity) mPushContext).runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    YaV1.getEventBus().unregister(this);
                    // we check about success or failure
                    if(mPd != null)
                        mPd.dismiss();

                    mPd = null;

                    if(!mError.isEmpty())
                    {
                        new AlertDialog.Builder(mPushContext)
                                .setTitle(R.string.sweep_push_error_title)
                                .setMessage(R.string.error + ": "  + mError)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int whichButton)
                                    {
                                    }
                                }).show();
                    }
                    else
                    {
                        Toast.makeText(mPushContext, R.string.sweep_push_success, Toast.LENGTH_SHORT).show();
                        if(mPushContext.getClass() == YaV1SweepSetActivity.class)
                        {
                            // we restart the AlertScreen
                            ( (YaV1SweepSetActivity) mPushContext).afterPush();
                        }
                        else
                        {
                            // send an info event
                            YaV1.postEvent(new InfoEvent(InfoEvent.Type.V1_INFO));
                        }
                    }
                }
            });
        }
    }

    // find a sweep position from it's id

    public int getPositionFromId(int id)
    {
        int i = 0;
        while(i < size())
        {
            if(get(i).getId() == id)
                return i;
            i++;
        }

        return -1;
    }

    // get the sweep from id

    public YaV1Sweep getSweepFromId(int id)
    {
        for(YaV1Sweep s: this)
        {
            if(s.getId() == id)
                return s;
        }

        return null;
    }

    // duplicate in edit mode

    public YaV1Sweep duplicateSet(int id)
    {
        int pos = getPositionFromId(id);

        if(pos >= 0)
        {
            YaV1Sweep n = new YaV1Sweep(++sSweepId, get(pos), get(pos).getName());
            return n;
        }

        return null;
    }

    // duplicate a sweep

    public boolean duplicateSet(String name, int id)
    {
        int pos = getPositionFromId(id);

        if(pos >= 0)
        {
            YaV1Sweep n = new YaV1Sweep(++sSweepId, get(pos), name);
            this.add(pos+1, n);
            mHasChanges = true;
            return true;
        }

        return false;
    }

    // update a sweep

    public void updateSweep(int id, YaV1Sweep nSweep)
    {
        // reorg the given sweep
        nSweep.reorgSweep();
        YaV1Sweep s = getSweepFromId(id);
        s.setName(nSweep.getName());
        s.setSweepDefinition(nSweep.getSweep());
        mHasChanges = true;
    }
    // remove a sweep set

    public boolean deleteSet(int id)
    {
        int pos = getPositionFromId(id);

        if(pos >= 0)
        {
            // if was lastKnown, remove the last Known flas
            if(id == mLastKnown)
                mLastKnown = -1;

            this.remove(pos);
            mHasChanges = true;
            return true;
        }

        return false;
    }

    // save

    public void save(boolean force)
    {
        if(!mHasChanges && !force)
            return;
        // Log.d("Valentine", "Saving sweeps");

        // create the directory if needed
        File sDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + YaV1.PACKAGE_NAME);

        if(sDir.isDirectory() || sDir.mkdirs())
        {
            // we will open file and use Gson
            File file = new File(sDir, mFileName);
            try
            {
                FileOutputStream f = new FileOutputStream(file);
                String s;
                // write last known id
                s = Integer.toString(mLastKnown) + "\n";
                f.write(s.getBytes());
                // write the sections
                Type listOfTestObject   = new TypeToken<List<SweepSection>>(){}.getType();
                Gson gson = new Gson();
                s = gson.toJson(sSweepSections, listOfTestObject) + "\n";
                f.write(s.getBytes());
                // loop over the sweeps
                int     nbs = 0;
                YaV1Sweep obj;
                listOfTestObject   = new TypeToken<YaV1Sweep>(){}.getType();

                while(nbs < this.size())
                {
                    obj = this.get(nbs);

                    if(obj != null && obj.isValid())
                    {
                        s = gson.toJson(obj, listOfTestObject) + "\n";
                        f.write(s.getBytes());
                        f.flush();
                        nbs++;
                    }
                }

                //s = gson.toJson(this, listOfTestObject);
                //f.write(s.getBytes());
                f.close();
                mHasChanges = false;
                Log.d("Valentine", "Saving Sweeps done, nb sweep set: " + this.size());
            }
            catch(IOException e)
            {
                 Log.d("Valentine", "IO exception writing sweept: " + e.toString());
            }
        }
    }

    public String getLastError()
    {
        return mLastError;
    }

    // clear all sweep (except factory default)

    public void clearAll()
    {
        // get the Factory setting
        YaV1Sweep s = getSweepFromId(0);
        clear();
        // add the factory setting
        this.add(s);
        // reset next sweep id
        sSweepId = 1;
        mHasChanges = true;
        if(mCurrentId > 0)
        {
            mCurrentId  = -1;
            mHasCurrent = false;
        }

        if(mLastKnown > 0)
            mLastKnown  = -1;
    }

    // build a sparse array of string of the sweeps

    public SparseArray<String> getSweepList()
    {
        SparseArray<String> sp = new SparseArray<String>();
        YaV1Sweep obj;
        for(int i =0; i < size(); i++)
        {
            obj = get(i);
            if(obj.isValid())
                sp.put(i, obj.getName());
        }

        return sp;
    }

    // export / backup

    public int export(File file)
    {
        mLastError = "";
        int nbs = 0;
        int nb  = 0;
        try
        {
            FileOutputStream f = new FileOutputStream(file);
            String s;
            // write last known id
            s = Integer.toString(29150) + "\n";
            f.write(s.getBytes());
            // write the sections
            Gson gson = new Gson();
            // loop over the sweeps
            YaV1Sweep obj;
            Type listOfTestObject   = new TypeToken<YaV1Sweep>(){}.getType();

            while(nbs < this.size())
            {
                obj = this.get(nbs);

                // we export only valid and not factory default

                if(!obj.isFactoryDefault() && obj.isValid())
                {
                    s = gson.toJson(obj, listOfTestObject) + "\n";
                    f.write(s.getBytes());
                    f.flush();
                    nb++;
                }
                nbs++;
            }

            f.close();
        }
        catch(IOException e)
        {
            mLastError = e.toString();
            Log.d("Valentine", "IO exception writing sweept: " + e.toString());
            return -1;
        }

        return nb;
    }

    // read

    public void read()
    {
        File sDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + YaV1.PACKAGE_NAME);
        File file = new File(sDir, mFileName);
        int  nb   = 0;

        try
        {
            FileInputStream f     = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(f));

            int nbInvalid         = 0;

            // loop to read all lines
            String line;
            this.clear();

            // read the last known sweep
            line = reader.readLine();

            if(line != null)
            {
                mLastKnown = Integer.valueOf(line);

                // read the SweepSection
                Type listOfTestObject = new TypeToken<List<SweepSection>>(){}.getType();
                Gson gson = new Gson();

                line = reader.readLine();
                sSweepSections = gson.fromJson(line, listOfTestObject);
                YaV1Sweep defaultSweep = null;

                if(!checkSweepSections(sSweepSections))
                {
                    sSweepSections = YaV1.sV1Lookup.getV1DefaultSweepSections();
                    // we add up the default sweep definition as Id = 0;
                    defaultSweep = new YaV1Sweep(0, YaV1.sV1Lookup.getV1DefaultCustomSweepsAsList());
                    defaultSweep.setName("Factory Default");
                    this.add(defaultSweep);
                    mHasChanges = true;
                }

                // we have to do something for new sweep section

                listOfTestObject = new TypeToken<YaV1Sweep>(){}.getType();
                while((line = reader.readLine()) != null)
                {
                    YaV1Sweep swp = gson.fromJson(line, listOfTestObject);

                    // the default sweep has been added already, ignore it
                    if(defaultSweep != null && swp.getId() == 0)
                        continue;

                    if(!swp.isValid())
                    {
                        nbInvalid++;
                        continue;
                    }

                    // adjust the id
                    sSweepId = Math.max(sSweepId, swp.getId());
                    this.add(swp);
                    nb++;
                }

                f.close();
                // Log.d("Valentine", "Reading Sweep " + nb + " SweepSet");

                if(mHasChanges || nbInvalid > 0)
                    save(true);

                mHasChanges = false;
                mHasCurrent = false;
            }
            else
            {
                initFromAssets();
                mHasChanges = true;
            }
            //YaV1.sLog.log("Reading short: " + mIsShort + " lockouts - " + size() + " lockouts");
        }
        catch(FileNotFoundException e)
        {
            // we initialize from the assets
            initFromAssets();
            mHasChanges = true;
            Log.d("Valentine", "File not found exception reading sweep, use assets");
        }
        catch(IOException e)
        {
            Log.d("Valentine", "File not found exception reading sweep Exc: " + e.toString());
        }

    }

    // check the readen sweep sections

    public boolean checkSweepSections(ArrayList<SweepSection> l)
    {
        for(SweepSection sc: l)
        {
            Log.d("valentine", "Check Sweep Low " + sc.getLowerFrequencyEdgeInteger() + " High " + sc.getUpperFrequencyEdgeInteger());
            if(sc.getLowerFrequencyEdgeInteger() == 0 || sc.getUpperFrequencyEdgeInteger() == 0)
                return false;
            /*
            SweepDefinitionIndex idx = sc.getSweepDefinitionIndex();
            if(idx != null)
                Log.d("Valentine", "SweepDefinition index " + idx.getNumberOfSectionsAvailable() + " Nb " + idx.getCurrentSweepIndex());
            */
        }

        return true;
    }
    // import / merge

    public int merge(File file)
    {
        mLastError      = "";
        mMergeDuplicate = 0;
        int         nb    = 0;
        boolean     found = false;
        try
        {
            FileInputStream f     = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(f));

            // loop to read all lines
            String line;

            // read the last known sweep
            line  = reader.readLine();
            int i = 0;
            try
            {
                i = Integer.valueOf(line);
            }
            catch(NumberFormatException e)
            {
                Log.d("Valentine", "Merge sweeps, invalid file");
                i = 0;
            }

            if(i != 29150)
            {
                mLastError = YaV1.sContext.getString(R.string.sweep_invalid_file);
                return -1;
            }

            Gson gson = new Gson();

            Type listOfTestObject = new TypeToken<YaV1Sweep>(){}.getType();
            while((line = reader.readLine()) != null)
            {
                YaV1Sweep swp = gson.fromJson(line, listOfTestObject);
                if(!swp.isValid())
                    continue;

                // check if not duplicate
                found = false;
                for(YaV1Sweep s: this)
                {
                    if(s.isSame(swp))
                    {
                        found = true;
                        break;
                    }
                }

                // adjust the id
                if(!found)
                {
                    // create a sweep with new id
                    YaV1Sweep sNew = new YaV1Sweep(++sSweepId, swp, "imp_" + swp.getName());
                    this.add(sNew);
                    nb++;
                }
                else
                    mMergeDuplicate++;
            }

            f.close();
            // Log.d("Valentine", "Import Sweep " + nb + " SweepSet");
            if(nb > 0)
                mHasChanges = true;
            //YaV1.sLog.log("Reading short: " + mIsShort + " lockouts - " + size() + " lockouts");
        }
        catch(java.lang.NullPointerException e)
        {
            // probably not a sweep file
            mLastError = YaV1.sContext.getString(R.string.sweep_invalid_file);
            Log.d("Valentine", "Nullexception reading sweep file" + e.toString());
            nb = -1;

        }
        catch(FileNotFoundException e)
        {
            mLastError = String.format(YaV1.sContext.getString(R.string.sweep_import_exception), e.toString());
            Log.d("Valentine", "File not found exception reading sweep, use assets");
            nb = -1;
        }
        catch(IOException e)
        {
            mLastError = String.format(YaV1.sContext.getString(R.string.sweep_import_exception), e.toString());
            Log.d("Valentine", "File not found exception reading sweep Exc: " + e.toString());
            nb = -1;
        }

        return nb;
    }

    // we read the asset file

    private void initFromAssets()
    {
        // open the assets and get the sweepSections as well as factory default
        try
        {
            // open the give file and play it
            InputStream inputStream  = YaV1.sContext.getResources().getAssets().open("factory_sweeps.json");

            BufferedReader reader    = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb         = new StringBuilder();
            String line              = null;

            // read the sweep section
            Type listOfTestObject = new TypeToken<List<SweepSection>>(){}.getType();
            Gson gson = new Gson();

            // line = reader.readLine();
            // sSweepSections = gson.fromJson(line, listOfTestObject);

            // we add the Factory sweep
            YaV1Sweep defaultSweep = new YaV1Sweep(0, YaV1.sV1Lookup.getV1DefaultCustomSweepsAsList());
            defaultSweep.setName("Factory Default");
            this.add(defaultSweep);

            listOfTestObject = new TypeToken<YaV1Sweep>(){}.getType();

            while((line = reader.readLine()) != null)
            {
                YaV1Sweep swp = gson.fromJson(line, listOfTestObject);
                this.add(swp);
            }
            // set the has changes for saving
            mHasChanges = true;
            // close the stream
            inputStream.close();
        }
        catch(IOException e)
        {
        }
    }
 }
