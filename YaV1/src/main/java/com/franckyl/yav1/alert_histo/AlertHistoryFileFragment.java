package com.franckyl.yav1.alert_histo;

import android.app.AlertDialog;
import android.app.ListFragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1Activity;
import com.franckyl.yav1.YaV1AlertService;
import com.franckyl.yav1.YaV1Logger;
import com.franckyl.yav1.utils.HighLightArrayAdapter;
import com.franckyl.yav1.utils.YaV1DbgLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by franck on 2/3/14.
 */
public class AlertHistoryFileFragment extends ListFragment
{
    private View                   mFragmentView;
    private ArrayList<String>      mFileList         = new ArrayList<String>();
    private int                    mCurrentPosition  = -1;
    private HighLightArrayAdapter  mAdapter          = null;
    private AlertDialog            mDlg              = null;

    // Fragment creation

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mCurrentPosition = AlertHistoryActivity.mCurrentSelection;
    }

    // create the view, we have to display the file list

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mFragmentView = inflater.inflate(R.layout.alert_histo_file_fragment, container, false);

        // get the files list and set Adapter
        getFileList();

        // set the adapter
        mAdapter = new HighLightArrayAdapter(getActivity(), android.R.layout.simple_list_item_1, mFileList);

        setListAdapter(mAdapter);

        String t = getTag();
        ( (AlertHistoryActivity) getActivity()).setFragmentTag(0, t);

        return mFragmentView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    @Override
    public void onStop()
    {
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        setUserVisibleHint(true);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser)
    {
        super.setUserVisibleHint(isVisibleToUser);
        if(isVisibleToUser)
        {
            if(mAdapter != null)
                mAdapter.setSelection(mCurrentPosition);
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    @Override

    public void onListItemClick(ListView l, View v, int position, long id)
    {
        // we will prepare the file
        if(position != mCurrentPosition)
        {
            mAdapter.setSelection(position);
            // show the processing dialog
            mDlg = new AlertDialog.Builder(getActivity())
                                            .setTitle(R.string.wait)
                                            .setMessage(R.string.gmap_processing).show();

            l.postDelayed(mPrepare, 200);
        }
    }

    // run the prepare list

    private Runnable mPrepare = new Runnable()
    {
        @Override
        public void run()
        {
            // run the prepare list, having Dlg, that we dismiss when done
            if(prepareAlertList(new File(YaV1.sStorageDir + "/logs/" + mFileList.get(mAdapter.getSelection())), false))
            {
                ((AlertHistoryActivity) getActivity()).setFileSelected(mAdapter.getSelection());
            }
            else
            {
                mCurrentPosition = -1;
                mAdapter.setSelection(-1);
                ((AlertHistoryActivity) getActivity()).setFileSelected(-1);
            }

            mDlg.dismiss();
        }
    };

    // get the file list

    private void getFileList()
    {
        // get all file from logging directory
        mFileList.clear();
        File dir = new File(YaV1.sStorageDir + "/" + "logs" + "/");
        String s;

        if(dir.isDirectory())
        {
            File[] files = dir.listFiles();
            for(int i = 0; i < files.length; ++i)
            {
                s = files[i].getName();
                // try to open, and check the legend

                if(prepareAlertList(new File(YaV1.sStorageDir + "/logs/" + s), true))
                    mFileList.add(s);
            }

            // reverse order
            Collections.sort(mFileList, Collections.reverseOrder());
        }
    }

    // we prepare the list of logged alert using the given file

    private boolean prepareAlertList(File iFile, boolean check)
    {
        try
        {
            //YaV1.DbgLog("Entering prepareAlertList file " + iFile.getName() + " Check is " + check);
            FileInputStream f     = new FileInputStream(iFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(f));

            // Read first line

            String line;
            line = reader.readLine();
            if(line == null || line.length() < 20)
            {
                f.close();
                return false;
            }

            int i = 0;

            for(;i<YaV1Logger.sLegend.length;i++)
            {
                if(line.equals(YaV1Logger.sLegend[i]))
                    break;
            }

            if(i >= YaV1Logger.sLegend.length)
            {
                // error in file
                if(!check)
                    Toast.makeText(getActivity(), "This is not a logging file", Toast.LENGTH_SHORT).show();
                f.close();
                return false;
            }

            // we might show a progress dialog

            // boolean isCsv                      = line.equals(YaV1Logger.sLegend[1]);
            boolean isCsv                      = (i % 2 > 0 ? true : false);
            boolean isPersistent               = (i <= 1 && AlertHistoryActivity.TTL == AlertHistoryActivity.DEFAULT_TTL ? true : false);

            ArrayList<LoggedAlert> currentList = new ArrayList<LoggedAlert>();
            int     lastTn                     = 0;
            AlertHistoryList       hList       = new AlertHistoryList(iFile.getPath(), isCsv);
            hList.setFromPersistent(isPersistent);
            ArrayList<LoggedAlert> hRecorded   = new ArrayList<LoggedAlert>();

            i = 0;

            while((line = reader.readLine()) != null)
            {
                i++;
                // in check mode we return as soon as we get at least one record
                if(check)
                {
                    f.close();
                    return true;
                }

                //YaV1.DbgLog("processing line " + i);
                // we can try to create an object
                LoggedAlert la = (isCsv ? LoggedAlert.createFromCsv(line) : LoggedAlert.createFromRaw(line));

                if(la != null)
                {
                    if(la.isOption(LoggedAlert.LGA_RECORDED))
                    {
                        hRecorded.add(la);
                    }
                    else
                    {
                        if(lastTn == 0)
                            lastTn = la.getTn();

                        if(la.getTn() != lastTn)
                        {
                            hList.process(currentList);
                            lastTn = la.getTn();
                            currentList.clear();
                        }

                        // we add to the currentList
                        currentList.add(la);
                    }
                }
            }

            f.close();
            //YaV1.DbgLog("File has been read lines " + i);

            // if we have en empty file

            if(check)
                return false;

            // process eventual last one

            if(currentList.size() > 0)
                hList.process(currentList);

            hList.complete();

            if(hRecorded.size() > 0)
            {
                //YaV1.DbgLog("Processing recorded alert " + hRecorded.size());
                hList.affectRecorded(hRecorded);
            }

            ((AlertHistoryActivity) getActivity()).updateList(hList);
            return true;
        }
        catch(FileNotFoundException e)
        {
            Log.d("Valentine", "File not found exception reading logging file: "  + iFile.getName() + " Exc: " + e.toString());
        }
        catch(IOException e)
        {
            Log.d("Valentine", "IOException reading logging file: " + iFile.getName() + " Exc: " + e.toString());
        }

        return false;
    }

}
