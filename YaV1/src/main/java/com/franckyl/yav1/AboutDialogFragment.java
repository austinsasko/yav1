package com.franckyl.yav1;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by franck on 9/23/14.
 */
public class AboutDialogFragment extends DialogFragment implements AdapterView.OnItemClickListener
{
    private ListView        mListView;
    private List<AboutLine> mLines;
    private AboutAdapter    mAdapter;
    private LayoutInflater  mInflater;
    private int             mTitleWidth;
    private int             mExpertClick;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.about_summary);
        builder.setPositiveButton(R.string.ok, null);

        mInflater   = getActivity().getLayoutInflater();
        View v      = mInflater.inflate(R.layout.about_box, null);
        mListView   = (ListView) v.findViewById(R.id.list);

        // build the lines
        buildLines();

        // create the adapter
        mAdapter = new AboutAdapter();

        // set the view
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);

        builder.setView(v);
        return builder.create();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id)
    {
        // retrieve the item
        final AboutLine al = mLines.get(position);

        if(al.isLink > 1)
        {
            if(al.isLink > 2)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.download);
                builder.setMessage(R.string.download_manual);
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        Uri uri = Uri.parse("http://" + al.value);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    }
                });
                builder.setNegativeButton(R.string.no, null);
                builder.show();
                return;
            }

            Uri uri = Uri.parse("http://" + al.value);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            dismiss();
            startActivity(intent);
        }
        else
        {
            // 7 times for becoming an expert
            if(position == 0 && !YaV1.sExpert)
            {
                ++mExpertClick;
                if(mExpertClick >= 7)
                {
                    Toast.makeText(getActivity(), R.string.expert, Toast.LENGTH_SHORT).show();
                    YaV1.sExpert = true;
                }
            }
        }
    }

    // build the lines

    private void buildLines()
    {
        mLines = new ArrayList<AboutLine>();

        mLines.add(new AboutLine(R.string.about_version, YaV1.VERSION_NAME));
        mLines.add(new AboutLine(R.string.about_v1_version, YaV1.sV1Version));
        mLines.add(new AboutLine(R.string.about_v1_serial, YaV1.sV1Serial));
        if(YaV1.sBatteryVoltage > 0.1)
            mLines.add(new AboutLine(R.string.about_v1_voltage, String.format("%.1f V", YaV1.sBatteryVoltage.floatValue())));
        mLines.add(new AboutLine(R.string.about_v1_savvy, YaV1.sSavvyVersion));
        mLines.add(new AboutLine(R.string.about_v1_settings, String.format("%d settings", YaV1.sV1Settings.getNumber())));
        mLines.add(new AboutLine(R.string.about_custom_sweep, String.format("%d sweep set", YaV1.sSweep.getNumber())));
        String sLockoutCount = "N/A";
        if(YaV1.sPrefs.getBoolean("enable_lockout", false) && YaV1.sAutoLockout.getLockoutCount())
            sLockoutCount = String.format("%d - %d", YaV1.sAutoLockout.sLockoutCount, YaV1.sAutoLockout.sLockoutLearning);
        mLines.add(new AboutLine(R.string.about_lockout, sLockoutCount));
        mLines.add(new AboutLine(R.string.about_storage, YaV1.sStorageDir, 1));
        mLines.add(new AboutLine(R.string.about_v1_support, "www.rdforum.org/forumdisplay.php?f=159", 2));
        mLines.add(new AboutLine(R.string.about_documentation, "yav1.rdforum.org/latest_yav1_manual.pdf", 3));
        // measure the size of the title
        FrameLayout fakeParent = new FrameLayout(getActivity());
        View v;
        v = mInflater.inflate(R.layout.about_row, null, false);
        TextView tv = (TextView) v.findViewById(R.id.titleText);
        mTitleWidth = 0;
        for(AboutLine al: mLines)
        {
            tv.setText(getString(al.resId));
            tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            mTitleWidth = Math.max(mTitleWidth, tv.getMeasuredWidth());
        }

        mExpertClick = 0;
    }

    // custom adapter for our list

    private class AboutAdapter extends BaseAdapter
    {
        public int getCount()
        {
            return mLines.size();
        }

        public View getView(int position, View convertView, ViewGroup parent)
        {
            convertView = mInflater.inflate(R.layout.about_row, null);

            AboutLine al = mLines.get(position);
            TextView t = (TextView) convertView.findViewById(R.id.titleText);
            if(al.isLink > 0)
            {
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                lp.alignWithParent = true;
                t.setGravity(Gravity.CENTER_HORIZONTAL);
                t.setText(getString(al.resId) + "\n" + al.value);
                t.setLayoutParams(lp);
                t = (TextView) convertView.findViewById(R.id.textValue);
                t.setVisibility(View.GONE);
            }
            else
            {
                t.setText(getString(al.resId));
                t.setWidth(mTitleWidth);
                t = (TextView) convertView.findViewById(R.id.textValue);
                t.setText(al.value);
            }

            return convertView;
        }

        public long getItemId(int position)
        {
            return position;
        }

        public Object getItem(int position)
        {
            return mLines.get(position);
        }
    }

    // our line class

    private class AboutLine
    {
        int     resId;
        String  value;
        int     isLink;

        AboutLine(int i, String v)
        {
            resId = i;
            value = v;
            isLink = 0;
        }

        AboutLine(int i, String v, int l)
        {
            resId  = i;
            value  = v;
            isLink = l;
        }
    }
}
