package com.franckyl.yav1.lockout;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1GpsService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import ar.com.daidalos.afiledialog.FileChooserDialog;
import ar.com.daidalos.afiledialog.FileChooserLabels;

/**
 * Created by franck on 6/24/14.
 */
public class LockoutActivity extends Activity
{
    private String mRc      = "";
    private String mRcTitle = "";
    private Context mContext;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.yav1_lockout_activity);

        mContext = this;
    }

    @Override
    public void onResume()
    {
        YaV1.superResume();
        super.onPause();
    }

    @Override
    public void onPostResume()
    {
        super.onPostResume();
        YaV1.superResume();
    }

    @Override
    public void onPause()
    {
        //YaV1.superPause();
        super.onPause();
    }

    @Override
    public void onStop()
    {
        super.onStop();
    }

    // menu for export / import

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.yav1_lockout, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.truncate:
                resetDb();
                return true;
            case R.id.backup:
                backupDb();
                return true;
            case R.id.restore:
                restoreDb();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // handle the click

    public void onClick(View v)
    {
        int id = v.getId();

        switch(id)
        {
            case R.id.backup:
                backupDb();
                break;
            case R.id.truncate:
                resetDb();
                break;
            case R.id.restore:
                restoreDb();
                break;
        }
    }

    // backup the Db
    private void backupDb()
    {
        // The backup file name
        if(!YaV1.checkStorage(this, "backup"))
            return;

        final String dbBackup = YaV1.sStorageDir + "/backup/" +  DateFormat.format("yyyy-MM-dd_HH_mm_", new java.util.Date()) + LockoutDb.DB_NAME;

        // confirmation
        new AlertDialog.Builder(this)
                .setTitle(R.string.lockout_backup)
                .setMessage(String.format(getString(R.string.lockout_backup_file), dbBackup))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int whichButton)
                    {
                        backupLockoutDb(LockoutDb.getDbCompleteName(), dbBackup);
                    }
                }).setNegativeButton(R.string.no, null).show();
    }

    // copy the lockout DB

    private void backupLockoutDb(final String in, final String dbBackup)
    {
        // show a progress dialog
        final ProgressDialog lDlg = new ProgressDialog(this);
        lDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        lDlg.setTitle(R.string.lockout_backup_progress);
        lDlg.setMessage("Wait for the operation to complete ... ");
        lDlg.setCancelable(false);
        lDlg.setCanceledOnTouchOutside(false);
        lDlg.setIndeterminate(true);
        lDlg.show();

        mRc = "";

        Thread myT = new Thread()
        {
            public void run()
            {
                // stop the lockout
                YaV1.sAutoLockout.endLockout();
                YaV1.sAutoLockout = null;

                if(copyFile(in, dbBackup))
                {
                    mRcTitle = getString(R.string.lockout_backup);
                    mRc = getString(R.string.lockout_backup_success);
                }
                else
                {
                    mRcTitle = getString(R.string.lockout_backup_error);
                }

                // recreate the lockout db Handler

                YaV1.sAutoLockout = new LockoutData(false);
                YaV1GpsService.resetFirstListDone();

                // dismiss the dialog
                lDlg.dismiss();
                YaV1.sLooper.getHandler().post(showResult);
            }
        };

        myT.start();
    }

    // restore the Db

    private void restoreDb()
    {
        // first we ask for a file to restore
        FileChooserDialog dialog = new FileChooserDialog(this);
        FileChooserLabels labels = new FileChooserLabels();
        dialog.loadFolder(YaV1.sStorageDir + "/" + "backup" + "/");

        dialog.setCanCreateFiles(false);
        dialog.setShowConfirmation(true, false);
        dialog.setTitle(R.string.lockout_restore_select);
        dialog.setFilter("[^\\s]+\\.db$");
        dialog.setShowOnlySelectable(true);
        labels.messageConfirmSelection = getString(R.string.lockout_restore_confirm);
        labels.labelSelectButton       = getString(R.string.select);;
        dialog.setLabels(labels);
        dialog.addListener(new FileChooserDialog.OnFileSelectedListener()
        {
            @Override
            public void onFileSelected(Dialog source, File file)
            {
                source.hide();
                restoreLockoutDb(file.getAbsolutePath());
            }
            @Override
            public void onFileSelected(Dialog source, File folder, String name)
            {
            }

        });
        dialog.show();
    }

    private void restoreLockoutDb(final String in)
    {
        // show a progress dialog
        final ProgressDialog lDlg = new ProgressDialog(this);
        lDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        lDlg.setTitle(R.string.lockout_restore_progress);
        lDlg.setMessage("Wait for the operation to complete ... ");
        lDlg.setCancelable(false);
        lDlg.setCanceledOnTouchOutside(false);
        lDlg.setIndeterminate(true);
        lDlg.show();

        mRc = "";

        Thread myT = new Thread()
        {
            public void run()
            {
                // stop the lockout
                YaV1.sAutoLockout.endLockout();
                YaV1.sAutoLockout = null;

                // remove the LockoutDb file
                File f = new File(LockoutDb.getDbCompleteName());
                if(f.exists())
                    f.delete();

                if(copyFile(in, LockoutDb.getDbCompleteName()))
                {
                    mRcTitle = getString(R.string.lockout_restore);
                    mRc = getString(R.string.lockout_restore_success);
                }
                else
                {
                    mRcTitle = getString(R.string.lockout_restore_error);
                }

                // recreate the lockout data object
                YaV1.sAutoLockout = new LockoutData(false);

                // make the Gps Service to request first init
                YaV1GpsService.resetFirstListDone();

                // dismiss the dialog
                lDlg.dismiss();
                YaV1.sLooper.getHandler().post(showResult);
            }
        };

        myT.start();
    }

    // try to show the success error message

    private final Runnable showResult = new Runnable()
    {
        @Override
        public void run()
        {
            new AlertDialog.Builder(mContext)
                    .setTitle(mRcTitle)
                    .setMessage(mRc)
                    .setNegativeButton(R.string.ok, null).show();

        }
    };

    // copy source on target

    private boolean copyFile(String source, String target)
    {
        boolean rc = false;

        FileInputStream fis=null;
        FileOutputStream fos=null;

        try
        {
            fis = new FileInputStream(source);
            fos = new FileOutputStream(target);

            while(true)
            {
                int i=fis.read();
                if(i!=-1)
                {fos.write(i);}
                else
                {break;}
            }
            fos.flush();
            rc = true;
        }
        catch(Exception e)
        {
            mRc = e.toString();
            e.printStackTrace();
        }

        finally
        {
            try
            {
                fos.close();
                fis.close();
            }
            catch(IOException ioe)
            {}
        }

        return rc;
    }

    // reset the Db

    private void resetDb()
    {
        new AlertDialog.Builder(this)
                .setTitle(R.string.lockout_reset_title)
                .setMessage(R.string.lockout_reset_confirm)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton)
                    {
                        // reset
                        YaV1.sAutoLockout.endLockout();
                        YaV1.sAutoLockout = new LockoutData(true);
                        // tell Gps to load again the current Area
                        YaV1GpsService.resetFirstListDone();
                        // show toast
                        mRcTitle = getString(R.string.lockout_reset_title);
                        mRc = getString(R.string.lockout_reset_done);
                        YaV1.sLooper.getHandler().post(showResult);
                    }
                }).setNegativeButton(R.string.no, null).show();
    }
}