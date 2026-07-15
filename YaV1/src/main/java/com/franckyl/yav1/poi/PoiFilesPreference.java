package com.franckyl.yav1.poi;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.preference.Preference;
import android.provider.OpenableColumns;
import android.util.AttributeSet;
import android.widget.Toast;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1PreferenceActivity;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * [P2-POI] Preference entry that manages the imported POI databases:
 * shows the imported files with their counts, allows enabling / disabling /
 * removing each file and importing a new CSV through Android's Storage
 * Access Framework.
 */
public class PoiFilesPreference extends Preference
{
    private static WeakReference<PoiFilesPreference> sImportTarget =
        new WeakReference<PoiFilesPreference>(null);

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
        // If the activity was recreated while the system picker was open, make
        // the newly attached preference the recipient of the result.
        sImportTarget = new WeakReference<PoiFilesPreference>(this);
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
        Context context = getContext();
        if(!(context instanceof Activity))
        {
            Toast.makeText(context,
                String.format(context.getString(R.string.poi_import_failed),
                              "document picker unavailable"),
                Toast.LENGTH_LONG).show();
            return;
        }

        sImportTarget = new WeakReference<PoiFilesPreference>(this);

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
            "text/csv", "text/comma-separated-values", "application/csv",
            "application/vnd.ms-excel", "text/plain"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        ((Activity) context).startActivityForResult(
            intent, YaV1PreferenceActivity.GET_POI_CSV);
    }

    /** Called by YaV1PreferenceActivity for the document picker result. */
    public static boolean handleActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(requestCode != YaV1PreferenceActivity.GET_POI_CSV)
            return false;

        PoiFilesPreference target = sImportTarget.get();
        if(resultCode == Activity.RESULT_OK && data != null && data.getData() != null
                && target != null)
            target.importUri(data.getData(), data.getFlags());

        return true;
    }

    private void importUri(final Uri uri, int resultFlags)
    {
        final PoiAlertManager m = manager();
        final Context context = getContext().getApplicationContext();
        final String displayName = displayName(uri);

        int takeFlags = resultFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try
        {
            context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
        }
        catch(Exception ignored)
        {
            // Some document providers grant a one-shot permission only. The import
            // consumes the stream immediately, so that is sufficient.
        }

        // parse can take a moment for big databases: do it off the UI thread
        new Thread("YaV1PoiImport")
        {
            @Override
            public void run()
            {
                PoiFile imported = null;
                String failure = "";
                InputStream in = null;

                try
                {
                    in = context.getContentResolver().openInputStream(uri);
                    if(in == null)
                        throw new java.io.IOException("document provider returned no data");
                    imported = m.getStore().importCsv(in, displayName, uri.toString());
                }
                catch(Exception exc)
                {
                    failure = exc.toString();
                }
                finally
                {
                    if(in != null)
                    {
                        try { in.close(); } catch(Exception ignored) {}
                    }
                }

                final PoiFile pf = imported;
                final String storeError = m.getStore().getLastError();
                final String error = (pf != null ? "" :
                    (!failure.isEmpty() ? failure :
                        (storeError.isEmpty() ? "unable to read " + displayName : storeError)));

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

    private String displayName(Uri uri)
    {
        Cursor cursor = null;
        try
        {
            cursor = getContext().getContentResolver().query(
                uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
            if(cursor != null && cursor.moveToFirst())
            {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(column >= 0)
                {
                    String name = cursor.getString(column);
                    if(name != null && !name.trim().isEmpty())
                        return name;
                }
            }
        }
        catch(Exception ignored)
        {
        }
        finally
        {
            if(cursor != null)
                cursor.close();
        }

        String name = uri.getLastPathSegment();
        return (name == null || name.trim().isEmpty()) ? "import.csv" : name;
    }

    private void post(Runnable r)
    {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
}
