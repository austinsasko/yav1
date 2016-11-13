package com.franckyl.yav1;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import ar.com.daidalos.afiledialog.FileChooserDialog;
import ar.com.daidalos.afiledialog.FileChooserLabels;

/**
 * Created by franck on 6/29/13.
 */
public class YaV1PreferenceActivity extends PreferenceActivity
{
    private String mLastError = "";
    private static SharedPreferences mPref;


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mPref = PreferenceManager.getDefaultSharedPreferences(this);

        View footerView =  ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.pref_buttons, null, false);

        setListFooter(footerView);
        // showAll();
    }

    public void onBuildHeaders(List<Header> target)
    {
        loadHeadersFromResource(R.xml.yav1_headers, target);
    }

    @Override
    protected boolean isValidFragment (String fragmentName)
    {
        if(YaV1PrefFragment.class.getName().equals(fragmentName))
            return true;
        return false;

    }

    @Override
    @SuppressWarnings("deprecation")
    public void onResume()
    {
        super.onResume();
        // YaV1.superResume();
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
        super.onPause();
        YaV1.superPause();
        mPref = PreferenceManager.getDefaultSharedPreferences(YaV1.sContext);
    }

    // button has been click

    public void onClick(View v)
    {
        switch(v.getId())
        {
            case R.id.preset:
                preset();
                break;
            case R.id.backup:
                backup();
                break;
            case R.id.restore:
                restore();
                break;
        }
    }

    // to handle our voice picker
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(requestCode == YaV1Activity.GET_VOICEALERT && resultCode == RESULT_OK)
        {
            Uri uri= data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if(uri != null)
            {
                Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
                String sT = (ringtone == null ? "" : ringtone.getTitle(this));
                DialogBandRange.setVoice(uri.toString(), sT);
            }
            else
                DialogBandRange.setVoice("", "");
        }
    }

    // backup the settings

    private void backup()
    {
        // check fist if we can write on phone
        if(!YaV1.checkStorage(this, "backup"))
        {
            // long toast
            return;
        }

        FileChooserDialog dialog = new FileChooserDialog(this);
        FileChooserLabels labels = new FileChooserLabels();
        // we need a file dialog to choose a file with prefefined name
        dialog.loadFolder(Environment.getExternalStorageDirectory() + "/" + YaV1.PACKAGE_NAME + "/" + "backup" + "/");
        dialog.setCanCreateFiles(true);
        dialog.setShowConfirmation(true, false);
        labels.labelConfirmYesButton        = getString(R.string.yes);
        labels.labelConfirmNoButton         = getString(R.string.no);
        labels.messageConfirmSelection      = getString(R.string.override_file);
        labels.createFileDialogMessage      = getString(R.string.new_file);
        labels.createFileDialogTitle        = getString(R.string.pref_menu_create_file);
        labels.createFileDialogAcceptButton = getString(R.string.accept);
        labels.createFileDialogCancelButton = getString(R.string.cancel);
        labels.labelAddButton               = getString(R.string.add);
        labels.labelSelectButton            = getString(R.string.select);;

        dialog.setLabels(labels);
        dialog.addListener(new FileChooserDialog.OnFileSelectedListener()
        {
            @Override
            public void onFileSelected(Dialog source, File file) {
                source.hide();
                save(source, file);
                source.dismiss();
            }
            @Override
            public void onFileSelected(Dialog source, File folder, String name)
            {
                source.hide();
                Log.d("Valentine", "new file " + folder.getAbsolutePath() + " File " + name);
                File file = new File(folder, name);
                save(source, file);
                source.dismiss();
            }

            // save here
            private void save(Dialog source, File file)
            {
                if(saveSharedPreferencesToFile(file))
                {
                    // we issue an error
                    new AlertDialog.Builder(source.getContext())
                            .setTitle(R.string.success)
                            .setMessage(String.format(getString(R.string.pref_menu_backup_success), file.getAbsolutePath()))
                            .setPositiveButton(R.string.ok, null).show();
                }
                else
                {
                    new AlertDialog.Builder(source.getContext())
                            .setTitle(R.string.error)
                            .setMessage(mLastError)
                            .setPositiveButton(R.string.ok, null).show();

                }
            }
        });
        dialog.show();
        Toast.makeText(getBaseContext(), R.string.select_create_file, Toast.LENGTH_SHORT).show();
    }

    // restore settings

    private void restore()
    {
        FileChooserDialog dialog = new FileChooserDialog(this);
        FileChooserLabels labels = new FileChooserLabels();

        // we need a file dialog to choose a file with prefefined name
        dialog.loadFolder(Environment.getExternalStorageDirectory() + "/" + YaV1.PACKAGE_NAME + "/" + "backup" + "/");
        dialog.setCanCreateFiles(false);
        dialog.setShowConfirmation(true, false);
        labels.messageConfirmSelection = getString(R.string.pref_restore_confirm);
        labels.labelSelectButton       = getString(R.string.select);;
        dialog.setLabels(labels);
        dialog.addListener(new FileChooserDialog.OnFileSelectedListener()
        {
            @Override
            public void onFileSelected(Dialog source, File file)
            {
                source.hide();
                if(loadSharedPreferencesFromFile(file))
                {
                    new AlertDialog.Builder(source.getContext())
                            .setTitle(R.string.success)
                            .setMessage(getString(R.string.pref_restore_success))
                            .setPositiveButton(R.string.ok, null).show();
                }
                else
                {
                    new AlertDialog.Builder(source.getContext())
                            .setTitle(R.string.error)
                            .setMessage(mLastError)
                            .setPositiveButton(R.string.ok, null).show();
                }
                source.dismiss();
            }

            @Override
            public void onFileSelected(Dialog source, File folder, String name)
            {
            }
        });
        dialog.show();
        Toast.makeText(getBaseContext(), R.string.select_file, Toast.LENGTH_SHORT).show();
    }

    // preset for preferences

    private void preset()
    {
        final CharSequence[] items = getResources().getStringArray(R.array.preset_settings);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final Context ct = this;
        builder.setTitle(R.string.preset);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, final int item) {
                new AlertDialog.Builder(ct)
                        .setTitle(R.string.preset_title)
                        .setMessage(R.string.preset_message)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if(loadSharedPreferencesFromAssets("preset_settings_"+item))// restore the file
                                    Toast.makeText(getApplicationContext(), R.string.preset_done, Toast.LENGTH_LONG).show();
                                else
                                    Toast.makeText(getApplicationContext(), R.string.preset_error, Toast.LENGTH_LONG).show();
                            }
                        }).setNegativeButton(R.string.no, null).show();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();
    }
    // static method to return the SharedPreference

    public static SharedPreferences getYaV1Preference()
    {
        return PreferenceManager.getDefaultSharedPreferences(YaV1.sContext);
    }

    // save the settings to sdcard

    private boolean saveSharedPreferencesToFile(File dst)
    {
        boolean res = false;
        mLastError  = "";

        ObjectOutputStream output = null;
        try
        {
            output = new ObjectOutputStream(new FileOutputStream(dst));
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            output.writeInt(29140);
            output.reset();
            output.writeObject(pref.getAll());
            res = true;
        }
        catch(FileNotFoundException e)
        {
            mLastError = getString(R.string.pref_menu_error_backup) + ":" + e.toString();
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            mLastError = getString(R.string.pref_menu_error_backup) + ":" + e.toString();
        }
        finally
        {
            try
            {
                if (output != null)
                {
                    output.flush();
                    output.close();
                }
            }
            catch (IOException ex)
            {
                mLastError = getString(R.string.pref_menu_error_backup) + ":" + ex.toString();
                ex.printStackTrace();
            }
        }

        return res;
    }

    // Restore preferences

    private boolean loadSharedPreferencesFromFile(File src)
    {
        mLastError = "";
        boolean res = false;
        ObjectInputStream input = null;
        try
        {
            input = new ObjectInputStream(new FileInputStream(src));
            // check if it comes from YaV1
            int s = input.readInt();
            if(s == 29140)
            {
                SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(this).edit();
                prefEdit.clear();
                Map<String, ?> entries = (Map<String, ?>) input.readObject();

                for(Map.Entry<String, ?> entry : entries.entrySet())
                {
                    Object v = entry.getValue();
                    String key = entry.getKey();

                    if (v instanceof Boolean)
                        prefEdit.putBoolean(key, ((Boolean) v).booleanValue());
                    else if (v instanceof Float)
                        prefEdit.putFloat(key, ((Float) v).floatValue());
                    else if (v instanceof Integer)
                        prefEdit.putInt(key, ((Integer) v).intValue());
                    else if (v instanceof Long)
                        prefEdit.putLong(key, ((Long) v).longValue());
                    else if (v instanceof String)
                        prefEdit.putString(key, ((String) v));
                }
                prefEdit.commit();
                res = true;
            }
            else
            {
                // not from YaV1
            }
        }
        catch(ClassCastException e)
        {
            mLastError = getString(R.string.pref_menu_error_invalid_file);
            Log.d("MyTest", "Not a YaV1 Backup file");
        }
        catch(FileNotFoundException e)
        {
            mLastError = getString(R.string.pref_menu_error_restore) + ":" + e.toString();
            e.printStackTrace();
        }
        catch(IOException e)
        {
            mLastError = getString(R.string.pref_menu_error_restore) + ":" + e.toString();
            e.printStackTrace();
        }
        catch (ClassNotFoundException e)
        {
            mLastError = getString(R.string.pref_menu_error_restore) + ":" + e.toString();
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (input != null)
                {
                    input.close();
                }
            }
            catch (IOException ex)
            {
                mLastError = getString(R.string.pref_menu_error_restore) + ":" + ex.toString();
                ex.printStackTrace();
            }
        }
        return res;
    }

    // load shared preference from assets

    private boolean loadSharedPreferencesFromAssets(String name)
    {
        boolean res = false;

        ObjectInputStream input = null;
        try
        {
            InputStream inputStream  = YaV1.sContext.getResources().getAssets().open(name);
            input = new ObjectInputStream(inputStream);
            // check if it comes from YaV1
            int s = input.readInt();
            if(s == 29140)
            {
                SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(this).edit();

                prefEdit.clear();
                Map<String, ?> entries = (Map<String, ?>) input.readObject();

                for(Map.Entry<String, ?> entry : entries.entrySet())
                {
                    Object v = entry.getValue();
                    String key = entry.getKey();

                    if (v instanceof Boolean)
                        prefEdit.putBoolean(key, ((Boolean) v).booleanValue());
                    else if (v instanceof Float)
                        prefEdit.putFloat(key, ((Float) v).floatValue());
                    else if (v instanceof Integer)
                        prefEdit.putInt(key, ((Integer) v).intValue());
                    else if (v instanceof Long)
                        prefEdit.putLong(key, ((Long) v).longValue());
                    else if (v instanceof String)
                        prefEdit.putString(key, ((String) v));
                }
                prefEdit.apply();
                res = true;
            }
            else
            {
                // not from YaV1
            }
        }
        catch(IOException e)
        {
        }
        catch (ClassNotFoundException e)
        {
            mLastError = getString(R.string.pref_menu_error_restore) + ":" + e.toString();
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (input != null)
                {
                    input.close();
                }
            }
            catch (IOException ex)
            {
                mLastError = getString(R.string.pref_menu_error_restore) + ":" + ex.toString();
                ex.printStackTrace();
            }
        }

        return res;
    }
    // utility to show all preferences

    private void showAll()
    {
        Map<String,?> keys = mPref.getAll();

        for(Map.Entry<String,?> entry : keys.entrySet())
        {
            Log.d("Valentine",entry.getKey() + ": " + entry.getValue().toString());
        }
    }
}
