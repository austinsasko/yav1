package com.franckyl.yav1.utils;

import android.text.format.DateFormat;
import android.util.Log;

import com.franckyl.yav1.YaV1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by franck on 2/11/14.
 */
public class YaV1DbgLogger extends Thread
{
    private boolean               isRunning = false;
    private BlockingQueue<DbgRec> mItems    =  new ArrayBlockingQueue<DbgRec>(200);
    private PrintStream           mLogFile  = null;
    private File                  mFileDisk = null;
    // constructor

    public YaV1DbgLogger()
    {

    }

    public boolean startLog()
    {
        // check first if enabled
        boolean enabled = YaV1.sPrefs.getBoolean("dbg_log", false);

        if(!enabled || isRunning)
            return false;

        // we can create the file and run the thread
        File sDir = new File(YaV1.sStorageDir + "/" + "dbg" + "/");

        if(sDir.isDirectory() || sDir.mkdirs())
        {
            // we will open file and use Gson
            try
            {
                String fName  = (String) DateFormat.format("yyyy-MM-dd", new java.util.Date()) + ".log";
                // check if file exists
                mFileDisk = new File(sDir, fName);
                FileOutputStream fo = new FileOutputStream(mFileDisk, true);
                mLogFile      = new PrintStream(fo);

                if(!this.isAlive())
                    this.start();
            }
            catch(IOException ex)
            {
                Log.d("Valentine", "Exception creating the DBG log file " + ex.toString());
                mLogFile = null;
            }
        }

        return false;
    }

    public void stopLogger()
    {
        if(isRunning)
        {
            DbgRec mrec = new DbgRec();
            mrec.timestamp = 0;
            mrec.mStr = "";
            dbgLog(mrec);
        }
    }

    // public logging

    public void dbgLog(String s)
    {
        DbgRec mRec = new DbgRec();
        mRec.timestamp = System.currentTimeMillis();
        mRec.mStr = s;
        dbgLog(mRec);
    }

    private void dbgLog(DbgRec rec)
    {
        if(!isRunning)
            return;
        try
        {
            mItems.put(rec);
        }
        catch(InterruptedException iex)
        {
            Thread.currentThread().interrupt();
            Log.d("Valentine", "Interrupt exception in DBG log, thrown as RuntimeException " + iex.toString());
            throw new RuntimeException("Unexpected interruption");
        }
    }

    public void run()
    {
        isRunning = true;
        DbgRec         rec;
        //CharSequence   dt;
        java.util.Date jv          = new java.util.Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS ");

        try
        {
            while(true)
            {
                rec = mItems.take();

                if(rec.timestamp == 0)
                    break;

                // we write to the file
                jv.setTime(rec.timestamp);
                //dt = formatter.format(jv);

                mLogFile.append(formatter.format(jv) + rec.mStr + "\n");
                mLogFile.flush();
            }
        }
        catch(InterruptedException exc)
        {
            Log.d("Valentine", "DBG Logger interrupted");
        }
        finally
        {
            isRunning = false;
        }

        mLogFile.close();
        mLogFile = null;
        isRunning = false;

        YaV1.forceMedia(mFileDisk);
        Log.d("Valentine", "DBG Logger stopped");
    }

    private class DbgRec
    {
        long    timestamp;
        String  mStr;
    }
}