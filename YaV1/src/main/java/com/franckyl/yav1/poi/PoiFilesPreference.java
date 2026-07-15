package com.franckyl.yav1.poi;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import com.franckyl.yav1.R;

import java.io.File;
import java.util.List;

import ar.com.daidalos.afiledialog.FileChooserDialog;
import ar.com.daidalos.afiledialog.FileChooserLabels;

/**
 * [P2-POI] Preference entry that manages the imported POI databases:
 * shows the imported files with their counts, allows enabling / disabling /
 * removing each file and importing a new CSV through the existing
 * aFileDialog chooser.
 */
public class PoiFilesPreference extends Preference
{
    public PoiFilesPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public PoiFilesPreference(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onAttachedToActivity()
    {
        super.onAttachedToActivity();
        updateSummary();
    }

    @Override
    protected void onClick()
    {
        showManageDialog();
    }

    private PoiAlertManager manager()
    {
        if(PoiAlertManager.getInstance() == null)
            PoiAlertManager.init(getContext().getApplicationContext());
        return PoiAlertManager.getInstance();
    }

    private void updateSummary()
    {
        PoiAlertManager m = manager();
        if(m == null || !m.isLoaded())
        {
            setSummary(R.string.poi_files_summary_loading);
            // trigger a load so the summary is right the next time
            if(m != null)
                m.reload();
            return;
        }

        PoiStore store = m.getStore();
        int files = store.getFiles().size();
        setSummary(String.format(getContext().getString(R.string.poi_files_summary),
                                 files, store.enabledCount()));
    }

    // -------------------------------------------------------------- dialogs

    private void showManageDialog()
    {
        final PoiAlertManager m = manager();
        final PoiStore store    = m.getStore();
        final List<PoiFile> files = store.getFiles();

        String[] items = new String[files.size()];
        for(int i = 0; i < files.size(); i++)
        {
            PoiFile pf = files.get(i);
            items[i] = (pf.enabled ? "[on]  " : "[off] ") + pf.name
                        + "  (" + pf.count() + " POI"
                        + (pf.skipped > 0 ? ", " + pf.skipped + " skipped" : "") + ")";
        }

        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle(R.string.poi_files_title);

        if(files.isEmpty())
            b.setMessage(R.string.poi_files_none);
        else
        {
            b.setItems(items, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    showFileDialog(files.get(which));
                }
            });
        }

        b.setNeutralButton(R.string.poi_import, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                showImportChooser();
            }
        });
        b.setPositiveButton(R.string.ok, null);
        b.show();
    }

    private void showFileDialog(final PoiFile pf)
    {
        final PoiAlertManager m = manager();

        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle(pf.name);
        b.setMessage(String.format(getContext().getString(R.string.poi_file_detail),
                                   pf.count(), pf.skipped, pf.source));

        b.setPositiveButton(pf.enabled ? R.string.poi_disable : R.string.poi_enable_file,
            new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    m.getStore().setEnabled(pf, !pf.enabled);
                    m.rebuildIndexNow();
                    updateSummary();
                    showManageDialog();
                }
            });

        b.setNegativeButton(R.string.poi_remove, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                m.getStore().remove(pf);
                m.rebuildIndexNow();
                updateSummary();
                showManageDialog();
            }
        });

        b.setNeutralButton(R.string.cancel, null);
        b.show();
    }

    private void showImportChooser()
    {
        FileChooserDialog dialog = new FileChooserDialog(getContext());
        FileChooserLabels labels = new FileChooserLabels();

        labels.labelSelectButton = getContext().getString(R.string.select);
        dialog.setLabels(labels);
        dialog.loadFolder(com.franckyl.yav1.YaV1.getStorageRootDir().getAbsolutePath());
        dialog.setFilter("(?i).*\\.csv");
        dialog.setCanCreateFiles(false);
        dialog.setShowConfirmation(true, false);

        dialog.addListener(new FileChooserDialog.OnFileSelectedListener()
        {
            @Override
            public void onFileSelected(Dialog source, File file)
            {
                source.dismiss();
                importFile(file);
            }

            @Override
            public void onFileSelected(Dialog source, File folder, String name)
            {
            }
        });

        dialog.show();
        Toast.makeText(getContext(), R.string.poi_import_hint, Toast.LENGTH_LONG).show();
    }

    private void importFile(final File file)
    {
        final PoiAlertManager m = manager();

        // parse can take a moment for big databases: do it off the UI thread
        new Thread("YaV1PoiImport")
        {
            @Override
            public void run()
            {
                final PoiFile pf     = m.getStore().importCsv(file);
                final String  error  = m.getStore().getLastError();

                if(pf != null)
                    m.rebuildIndexNow();

                post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if(pf != null)
                        {
                            Toast.makeText(getContext(),
                                String.format(getContext().getString(R.string.poi_import_done),
                                              pf.name, pf.count(), pf.skipped),
                                Toast.LENGTH_LONG).show();
                            updateSummary();
                        }
                        else
                        {
                            Toast.makeText(getContext(),
                                String.format(getContext().getString(R.string.poi_import_failed), error),
                                Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }.start();
    }

    private void post(Runnable r)
    {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
}
