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

import com.franckyl.yav1.events.InfoEvent;
import com.valentine.esp.data.SweepDefinition;

import java.io.File;
import java.util.ArrayList;

import ar.com.daidalos.afiledialog.FileChooserDialog;
import ar.com.daidalos.afiledialog.FileChooserLabels;

/**
 * Created by franck on 8/2/13.
 */
public class YaV1SweepSetActivity extends ListActivity
{
    ListView                mListView;
    YaV1SweepSetAdapter     mListAdapter;
    public static int       DEFAULT_COLOR;
    private final int       GET_SWEEP_EDIT  = 13;
    @Override

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.yav1_sweepset_activity);

        // get the list
        mListView = getListView();
        //mListView = (ListView) findViewById(R.id.mainList);

        // adapter
        mListAdapter  = new YaV1SweepSetAdapter(this);

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
        // YaV1.superResume();
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

    // backup / export

    private void backup()
    {
        FileChooserDialog dialog = new FileChooserDialog(this);
        FileChooserLabels labels = new FileChooserLabels();

        if(!YaV1.checkStorage(this, "backup"))
            return;

        if(YaV1.sSweep.getNumber() < 1)
        {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.sweep_no_sweep)
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
                    Log.d("Valentine", "new file " + folder.getAbsolutePath() + " File " + name);
                    source.hide();
                    File file = new File(folder, name);
                    save(source, file);
                    source.dismiss();
                }

                // save here
                private void save(Dialog source, File file)
                {
                    int nb = YaV1.sSweep.export(file);

                    if(nb > 0)
                    {
                        // we issue an error
                        new AlertDialog.Builder(source.getContext())
                                .setTitle(R.string.success)
                                .setMessage(String.format(getString(R.string.sweep_export_success), nb, file.getAbsolutePath()))
                                .setPositiveButton(R.string.ok, null).show();
                    }
                    else
                    {
                        new AlertDialog.Builder(source.getContext())
                                .setTitle(R.string.error)
                                .setMessage(YaV1.sSweep.getLastError())
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
        // we need a file dialog to choose a file with prefefined name
        dialog.loadFolder(YaV1.sStorageDir + "/" + "backup" + "/");
        dialog.setCanCreateFiles(false);
        dialog.setShowConfirmation(true, false);
        labels.messageConfirmSelection = getString(R.string.sweep_import_confirm);
        labels.labelSelectButton       = getString(R.string.select);;
        dialog.setLabels(labels);
        dialog.addListener(new FileChooserDialog.OnFileSelectedListener()
        {
            @Override
            public void onFileSelected(Dialog source, File file)
            {
                source.hide();
                int nb  = YaV1.sSweep.merge(file);

                if(nb >= 0)
                {
                    new AlertDialog.Builder(source.getContext())
                            .setTitle(R.string.success)
                            .setMessage(String.format(getString(R.string.sweep_import_success), nb, YaV1.sSweep.mMergeDuplicate))
                            .setPositiveButton(R.string.ok, null).show();
                    mListAdapter.notifyDataSetChanged();
                    mListView.invalidateViews();
                }
                else
                {
                    new AlertDialog.Builder(source.getContext())
                            .setTitle(R.string.error)
                            .setMessage(YaV1.sSweep.getLastError())
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
        final YaV1Sweep sweep = YaV1.sSweep.get(position);

        if(sweep != null)
        {
            int Id = sweep.getId();

            Intent intent = new Intent(this, YaV1SweepEditActivity.class);
            intent.putExtra("sweepId", Id);
            if(sweep.isFactoryDefault() || Id == YaV1.sSweep.getCurrentId())
                intent.putExtra("view", true);
            startActivityForResult(intent, GET_SWEEP_EDIT);
        }
    }

    // on edit we want to know the result

    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch(requestCode)
        {
            case GET_SWEEP_EDIT:
            {
                if(resultCode == Activity.RESULT_OK )
                {
                    // we have to refresh the data
                    mListAdapter.notifyDataSetChanged();
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
        final YaV1Sweep sweep = YaV1.sSweep.get(position);

        if(sweep != null)
        {
            final EditText input = new EditText(this);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.sweep_duplicate_title)
                    .setMessage(R.string.sweep_duplicate_title_message)
                    .setView(input)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // we will duplicate the given one using the id
                            String value = input.getText().toString();
                            if (YaV1.sSweep.duplicateSet(value, sweep.getId())) {
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
        final YaV1Sweep sweep = YaV1.sSweep.get(position);

        YaV1.sSweep.internalPush(sweep.getId(), this);
    }

    // after push we stop the activity

    public void afterPush()
    {
        Intent response = new Intent();
        response.putExtra("restart", true);
        setResult(Activity.RESULT_OK, response);
        finish();
    }

    public void onDelete(View v)
    {
        final int position = mListView.getPositionForView((View) v.getParent());
        // find the sweep in the list
        final YaV1Sweep sweep = YaV1.sSweep.get(position);

        if(sweep != null)
        {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.sweep_push_delete_title)
                    .setMessage(R.string.sweep_push_delete_message)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int whichButton)
                        {
                            if( YaV1.sSweep.deleteSet(sweep.getId()))
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
    }
}
