package com.franckyl.yav1;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;

import ar.com.daidalos.afiledialog.FileChooserDialog;
import ar.com.daidalos.afiledialog.FileChooserLabels;

/**
 * Created by franck on 8/2/13.
 */
public class YaV1SettingSetActivity extends ListActivity
{
    ListView                mListView;
    YaV1SettingSetAdapter   mListAdapter;
    public static int       DEFAULT_COLOR;
    private final int       GET_SETTING_EDIT  = 14;
    private ProgressDialog  mPD = null;
    private int             mCurrentEdit = 0;

    @Override

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.yav1_settingset_activity);

        // get the list
        mListView = getListView();

        // adapter
        mListAdapter  = new YaV1SettingSetAdapter(this);

        setListAdapter(mListAdapter);

        TypedValue bk = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, bk, true);
        if(bk.type >= TypedValue.TYPE_FIRST_COLOR_INT && bk.type <= TypedValue.TYPE_LAST_COLOR_INT)
        {
            // windowBackground is a color
            DEFAULT_COLOR = bk.data;
        }
    }

    @Override
    public void onResume()
    {
        //YaV1.superResume();
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
        YaV1.superPause();
        super.onPause();
    }

    @Override
    public void onStop()
    {
        super.onStop();
    }

    public void onClick(View v)
    {
        switch(v.getId())
        {
            case R.id.backup:
                backup();
                break;
            case R.id.restore:
                restore();
                break;
        }
    }

    // backup

    private void backup()
    {
        FileChooserDialog dialog = new FileChooserDialog(this);
        FileChooserLabels labels = new FileChooserLabels();
        if(!YaV1.checkStorage(this, "backup"))
            return;

        if(YaV1.sV1Settings.getNumber() < 1)
        {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.v1_setting_no_setting)
                    .setPositiveButton(R.string.ok, null).show();
        }
        else
        {
            // we need a file dialog to choose a file with prefefined name
            dialog.loadFolder(YaV1.sStorageDir + "/" + "backup" + "/");
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
                    Log.d("Valentine", "Backup settings new file " + folder.getAbsolutePath() + " File " + name);
                    source.hide();
                    File file = new File(folder, name);
                    save(source, file);
                    source.dismiss();
                }

                // save here
                private void save(Dialog source, File file)
                {
                    int nb = YaV1.sV1Settings.export(file);

                    if(nb > 0)
                    {
                        // we issue an error
                        new AlertDialog.Builder(source.getContext())
                                .setTitle(R.string.success)
                                .setMessage(String.format(getString(R.string.v1_setting_export_success), nb, file.getAbsolutePath()))
                                .setPositiveButton(R.string.ok, null).show();
                    }
                    else
                    {
                        new AlertDialog.Builder(source.getContext())
                                .setTitle(R.string.error)
                                .setMessage(YaV1.sV1Settings.getLastError())
                                .setPositiveButton(R.string.ok, null).show();

                    }
                }
            });
            dialog.show();
            Toast.makeText(getBaseContext(), R.string.select_create_file, Toast.LENGTH_SHORT).show();
        }
    }

    // import / merge

    private void restore()
    {
        FileChooserDialog dialog = new FileChooserDialog(this);
        FileChooserLabels labels = new FileChooserLabels();

        dialog.loadFolder(YaV1.sStorageDir + "/" + "backup" + "/");
        dialog.setCanCreateFiles(false);
        dialog.setShowConfirmation(true, false);
        labels.messageConfirmSelection = getString(R.string.v1_setting_import_confirm);
        labels.labelSelectButton       = getString(R.string.select);
        dialog.setLabels(labels);
        dialog.addListener(new FileChooserDialog.OnFileSelectedListener()
        {
            @Override
            public void onFileSelected(Dialog source, File file)
            {
                source.hide();
                int nb  = YaV1.sV1Settings.merge(file);

                if(nb >= 0)
                {
                    new AlertDialog.Builder(source.getContext())
                            .setTitle(R.string.success)
                            .setMessage(String.format(getString(R.string.v1_setting_import_success), nb, YaV1.sV1Settings.mMergeDuplicate))
                            .setPositiveButton(R.string.ok, null).show();
                    mListAdapter.notifyDataSetChanged();
                    mListView.invalidateViews();
                }
                else
                {
                    new AlertDialog.Builder(source.getContext())
                            .setTitle(R.string.error)
                            .setMessage(YaV1.sV1Settings.getLastError())
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

    // edit

    public void onEdit(View v)
    {
        final int position = mListView.getPositionForView((View) v.getParent());
        final YaV1Setting set = YaV1.sV1Settings.get(position);

        if(set != null)
        {
            mCurrentEdit = set.getId();

            Intent intent = new Intent(this, YaV1SettingEditActivity.class);
            intent.putExtra("settingId", mCurrentEdit);

            if(set.isFactoryDefault())
                intent.putExtra("view", true);

            startActivityForResult(intent, GET_SETTING_EDIT);
        }
        else
            mCurrentEdit = 0;
    }

    // on edit we want to know the result

    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch(requestCode)
        {
            case GET_SETTING_EDIT:
            {
                if(resultCode == Activity.RESULT_OK )
                {
                    boolean modified = data.getBooleanExtra("modified", false);
                    boolean warnpop  = data.getBooleanExtra("warnpop", false);
                    Log.d("Valentine", "Setting id " + mCurrentEdit + " Modified " + modified);
                    // we have to refresh the data
                    mListAdapter.notifyDataSetChanged();
                    // check if the current edit is current active
                    if(mCurrentEdit == YaV1.sV1Settings.getCurrentId() && modified)
                    {
                        // ask for Push ?
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.v1_setting_update_current)
                                .setMessage(R.string.v1_setting_update_current_message)
                                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int whichButton)
                                    {
                                        internalPush(mCurrentEdit);
                                    }
                                }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int whichButton)
                                    {
                                        // Reset current to be able to push later
                                        YaV1.sV1Settings.resetCurrent();
                                        // reset current view
                                        mListAdapter.notifyDataSetChanged();
                                    }
                                }).show();

                    }
                    break;
                }
            }
        }
    }

    // duplicate

    public void onDuplicate(View v)
    {
        final int position = mListView.getPositionForView((View) v.getParent());

        // find the sweep in the list
        final YaV1Setting set = YaV1.sV1Settings.get(position);

        if(set != null)
        {
            final EditText input = new EditText(this);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.v1_setting_duplicate_title)
                    .setMessage(R.string.v1_setting_duplicate_message)
                    .setView(input)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // we will duplicate the given one using the id
                            String value = input.getText().toString();
                            if (YaV1.sV1Settings.duplicateSetting(value, set.getId())) {
                                // we would notify our adapeter about changes
                                mListAdapter.notifyDataSetChanged();
                                mListView.invalidateViews();
                            }
                        }
                    }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Do nothing.
                }
            }).show();
        }
    }

    // push received
    public void onPush(View v)
    {
        final int position = mListView.getPositionForView((View) v.getParent());

        YaV1Setting set = YaV1.sV1Settings.get(position);

        internalPush(set.getId());
    }

    // internal V1 setting push

    public boolean internalPush(int id)
    {
        if(YaV1.sV1Settings.pushSetting(id))
        {
            final ProgressDialog lDlg = new ProgressDialog(this);
            lDlg.setCancelable(false);
            lDlg.setIndeterminate(true);
            lDlg.setMessage(getText(R.string.wait));
            lDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            lDlg.show();
            // we redraw
            mListAdapter.notifyDataSetChanged();
            new Thread(new Runnable() {
                @Override
                public void run()
                {
                    try
                    {
                        Thread.sleep(2000);
                    }
                    catch (InterruptedException e)
                    {

                    }
                }
            }).start();

            lDlg.dismiss();
            Toast.makeText(this, R.string.v1_setting_push_success, Toast.LENGTH_LONG).show();
            // that will cause restart of the AlertService
            Intent response = new Intent();
            response.putExtra("restart", true);
            setResult(Activity.RESULT_OK, response);
            finish();
            return true;
        }

        return false;
    }

    // Delete received

    public void onDelete(View v)
    {
        final int position = mListView.getPositionForView((View) v.getParent());
        // find the sweep in the list
        final YaV1Setting set = YaV1.sV1Settings.get(position);

        if(set != null)
        {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.v1_setting_delete_title)
                    .setMessage(R.string.v1_setting_delete_message)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int whichButton)
                        {
                            if( YaV1.sV1Settings.deleteSetting(set.getId()))
                                // we would notify our adapeter about changes
                                mListAdapter.notifyDataSetChanged();
                        }
                    }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog, int whichButton)
                {
                    // Do nothing.
                }
            }).show();
        }
        Log.d("Valentine", "On Delete setting " + position);
    }
}
