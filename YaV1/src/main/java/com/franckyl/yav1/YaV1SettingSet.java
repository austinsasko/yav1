package com.franckyl.yav1;

import android.os.Environment;
import android.util.Log;

import com.franckyl.yav1.events.InfoEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.valentine.esp.data.UserSettings;

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
import java.util.List;

/**
 * Created by franck on 8/5/13.
 */
public class YaV1SettingSet extends ArrayList<YaV1Setting>
{
    public static final int     SETTING_BYTES  = 6;

    private static int                      sSettingId        = 0;
    private boolean                         mHasChanges       = false;
    private String                          mFileName         = "v1_settings.json";
    private boolean                         mHasCurrent       = false;
    private int                             mCurrentId        = -1;
    private int                             mLastKnown        = -1;
    private YaV1Setting                     mWaitingCurrent   = null;
    private String                          mLastError        = null;
    public  int                             mMergeDuplicate   = 0;

    // need the current sweeps ?
    public boolean hasCurrent()
    {
        return mHasCurrent;
    }

    // get the current sweep

    public int getCurrentId()
    {
        return mCurrentId;
    }

    // get the last known

    public int getLastKnown()
    {
        return mLastKnown;
    }

    // get the number of settings

    public int getNumber()
    {
        //size minus factory default
        return Math.max(0, size() - 1);
    }

    public String getLastError()
    {
        return mLastError;
    }

    // set the current sweep id after push

    public void setCurrentSettingId(int iId)
    {
        mCurrentId = iId;
        mHasCurrent = true;
        mLastKnown  = iId;
        // this to save the last current setting
        mHasChanges = true;
    }

    // reset the current settings (when user modify current and do not push)

    public void resetCurrent()
    {
        mCurrentId = -1;
        mHasCurrent = false;
    }

    // set the current user setting from V1

    public void setCurrentUserSettingCallback(UserSettings current)
    {
        // that can happen in Demo mode
        if(current == null)
            return;

        mWaitingCurrent = new YaV1Setting(++sSettingId, current);
        searchCurrentSetting(mWaitingCurrent);

        // we request the sweep info (if enabled)
        // wait a second
        try
        {
            Thread.sleep(1000);
        }
        catch(InterruptedException e)
        {

        }

        YaV1AlertService.requestSweep();

        // we post the refresh of the setting name
        YaV1.postEvent(new InfoEvent(InfoEvent.Type.V1_INFO));
        save(false);
    }

    // search for a current setting that match
    public void searchCurrentSetting(YaV1Setting current)
    {
        // we check if we already have it
        for(YaV1Setting s: this)
        {
            if(s.isSame(current))
            {
                mHasCurrent = true;
                mCurrentId  = s.getId();
                break;
            }
        }

        if(!mHasCurrent)
        {
            // we create new one
            // set the name to be today's date
            mHasChanges = true;
            current.setName("V1 Setting the " + android.text.format.DateFormat.format("yyyy-MM-dd_hh-mm-ss", new java.util.Date()));
            add(current);
            mHasCurrent = true;
            mCurrentId  = current.getId();
        }

        // if custom sweeps, we request them

        mLastKnown  = mCurrentId;
    }

    // get the settings list

    public List<String> getSettingList()
    {
        List<String> sp = new ArrayList<String>();
        YaV1Setting obj;

        for(int i=0; i < size(); i++)
        {
            obj = get(i);
            sp.add(obj.getName());
        }

        return sp;
    }

    // get the current setting position

    public int getCurrentSettingPosition()
    {
        return getPositionFromId(mCurrentId);
    }

    // Push setting from position

    public boolean pushSettingFromPosition(int position)
    {
        int id = getIdFromPosition(position);

        if(id >= 0)
            return pushSetting(id);

        return false;
    }

    // Push setting

    public boolean pushSetting(int id)
    {
        YaV1Setting set = getSettingFromId(id);

        if(set != null)
        {
            // we build the user setting structure
            UserSettings us = set.getV1Definition();
            if(set.isFactoryDefault())
                YaV1.mV1Client.doFactoryDefault(YaV1.mV1Client.m_valentineType);
            else
                // we push the settings
                YaV1.mV1Client.writeUserSettings(us);
            setCurrentSettingId(set.getId());
            // request the settings again
            (YaV1.sInstance).getV1Setting();
            return true;
        }

        return false;
    }


    // duplicate a setting

    public boolean duplicateSetting(String name, int nId)
    {
        YaV1Setting dup = getSettingFromId(nId);

        if(dup != null)
        {
            YaV1Setting ns = new YaV1Setting(++sSettingId, dup);
            ns.setName(name);
            add(ns);
            mHasChanges = true;
        }

        return false;
    }

    // duplicate a setting

    public YaV1Setting duplicateSetting(int nId)
    {
        YaV1Setting dup = getSettingFromId(nId);

        if(dup != null)
        {
            YaV1Setting ns = new YaV1Setting(++sSettingId, dup);
            ns.setName(dup.getName());
            return ns;
        }

        return null;
    }

    // update a setting

    public void updateSetting(int id, YaV1Setting iSet)
    {
        YaV1Setting s = getSettingFromId(id);

        // replace the name
        s.setName(iSet.getName());
        // replace the bytes
        s.setBytes(iSet.getBytes());

        // be sure to save
        mHasChanges = true;
    }

    // return position from id

    public int getPositionFromId(int id)
    {
        int i = 0;
        for(YaV1Setting s: this)
        {
            if(s.getId() == id)
                return i;
            i++;
        }

        return -1;
    }

    // get the id from from position

    public int getIdFromPosition(int position)
    {
        for(int i=0; i < size(); i++)
        {
            if(i == position)
                return get(i).getId();
        }

        return -1;
    }

    // delete a set

    public boolean deleteSetting(int id)
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

    // get the current setting name

    public String getCurrentName()
    {
        if(mHasCurrent)
        {
            YaV1Setting curr = getSettingFromId(mCurrentId);
            if(curr != null)
                return curr.getName();
        }

        return "";
    }

    // get a setting from Id

    public YaV1Setting getSettingFromId(int id)
    {
        for(YaV1Setting s: this)
        {
            if(s.getId() == id)
                return s;
        }

        return null;
    }
    // save the settings

    public void save(boolean force)
    {
        if(!mHasChanges && !force)
            return;

        Log.d("Valentine", "Saving settings");

        // create the directory if needed
        File sDir = new File(YaV1.sStorageDir);

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

                // loop over the settings
                int         nbs = 0;
                YaV1Setting obj;
                Gson gson = new Gson();

                Type listOfTestObject   = new TypeToken<YaV1Setting>(){}.getType();

                while(nbs < this.size())
                {
                    obj = this.get(nbs);
                    s = gson.toJson(obj, listOfTestObject) + "\n";
                    f.write(s.getBytes());
                    f.flush();
                    nbs++;
                }

                f.close();
                mHasChanges = false;
                Log.d("Valentine", "Saving Settings done, nb setting: " + this.size());
            }
            catch(IOException e)
            {
                Log.d("Valentine", "IO exception writing settings: " + e.toString());
            }
        }
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
            s = Integer.toString(29160) + "\n";
            f.write(s.getBytes());
            // write the sections
            Gson gson = new Gson();
            // loop over the sweeps
            YaV1Setting obj;
            Type listOfTestObject   = new TypeToken<YaV1Setting>(){}.getType();

            while(nbs < this.size())
            {
                obj = this.get(nbs);
                if(!obj.isFactoryDefault())
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
            Log.d("Valentine", "IO exception writing v1 setting: " + e.toString());
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

            // loop to read all lines
            String line;
            this.clear();

            // read the last known sweep
            line = reader.readLine();

            if(line != null)
            {
                Log.d("Valentine", "Last known " + line);
                mLastKnown = Integer.valueOf(line);

                Gson gson = new Gson();

                Type listOfTestObject = new TypeToken<YaV1Setting>(){}.getType();
                while((line = reader.readLine()) != null)
                {
                    YaV1Setting ust = (YaV1Setting) gson.fromJson(line, listOfTestObject);
                    // adjust the id
                    sSettingId = Math.max(sSettingId, ust.getId());
                    this.add(ust);
                    nb++;
                }

                f.close();
                Log.d("Valentine", "Reading V1 Setting " + nb + " Settings");
                mHasChanges = false;
                mHasCurrent = false;
                mWaitingCurrent = null;
            }
            else
            {
                f.close();
                initFromAssets();
                mHasChanges = true;
            }
        }
        catch(FileNotFoundException e)
        {
            // we initialize the list for factory settings
            initFromAssets();
            mHasChanges = true;
            Log.d("Valentine", "File not found exception reading v1 setting, write factory default");
        }
        catch(IOException e)
        {
            Log.d("Valentine", "File not found exception reading v1 setting Exc: " + e.toString());
        }
    }

    // import / merge

    public int merge(File file)
    {
        mLastError        = "";
        mMergeDuplicate   = 0;
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
                Log.d("Valentine", "Merge v1 settings, invalid file");
                i = 0;
            }

            if(i != 29160)
            {
                mLastError = YaV1.sContext.getString(R.string.v1_setting_invalid_file);
                return -1;
            }

            Gson gson = new Gson();

            Type listOfTestObject = new TypeToken<YaV1Setting>(){}.getType();
            while((line = reader.readLine()) != null)
            {
                YaV1Setting swp = gson.fromJson(line, listOfTestObject);

                // check if not duplicate
                found = false;
                for(YaV1Setting s: this)
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
                    swp.setName("imp_" + swp.getName());
                    YaV1Setting sNew = new YaV1Setting(++sSettingId, swp);
                    this.add(sNew);
                    nb++;
                }
                else
                    mMergeDuplicate++;
            }

            f.close();
            Log.d("Valentine", "Import v1 setting " + nb + " settings");
            if(nb > 0)
                mHasChanges = true;
            //YaV1.sLog.log("Reading short: " + mIsShort + " lockouts - " + size() + " lockouts");
        }
        catch(java.lang.NullPointerException e)
        {
            // probably not a sweep file
            mLastError = YaV1.sContext.getString(R.string.v1_setting_invalid_file);
            Log.d("Valentine", "Null exception reading setting file" + e.toString());
            nb = -1;

        }
        catch(FileNotFoundException e)
        {
            mLastError = String.format(YaV1.sContext.getString(R.string.v1_setting_import_exception), e.toString());
            Log.d("Valentine", "File not found exception reading v1 setting");
            nb = -1;
        }
        catch(IOException e)
        {
            mLastError = String.format(YaV1.sContext.getString(R.string.v1_setting_import_exception), e.toString());
            Log.d("Valentine", "File not found exception reading settings Exc: " + e.toString());
            nb = -1;
        }

        return nb;
    }

    // we read the asset file

    private void initFromAssets()
    {
        // create the factory default

        YaV1Setting ns = new YaV1Setting(0, UserSettings.GenerateFactoryDefaults());
        ns.setName("Factory default");
        add(ns);

        // open the assets and get the sweepSections as well as factory default
        try
        {
            // open the give file and play it
            InputStream inputStream  = YaV1.sContext.getResources().getAssets().open("v1_settings.json");

            BufferedReader reader    = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb         = new StringBuilder();
            String line              = null;

            Gson gson = new Gson();

            Type listOfTestObject = new TypeToken<YaV1Setting>(){}.getType();

            while((line = reader.readLine()) != null)
            {
                YaV1Setting s = gson.fromJson(line, listOfTestObject);
                this.add(s);
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
