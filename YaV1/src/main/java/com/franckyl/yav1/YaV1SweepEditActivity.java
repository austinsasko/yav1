package com.franckyl.yav1;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.valentine.esp.data.SweepDefinition;

import java.util.ArrayList;

/**
 * Created by franck on 8/3/13.
 */
public class YaV1SweepEditActivity extends ListActivity
{
    private YaV1Sweep           mSweep;
    private MySweepAdapter      mAdapter;
    private ListView            mListView;
    private int                 mSweepId;
    private ArrayList<String>   mSweepString = new ArrayList<String>();
    private Context             mContext;
    private boolean             mView        = false;
    @Override

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mView = this.getIntent().getBooleanExtra("view", false);

        mContext = this;
        mSweepId = this.getIntent().getIntExtra("sweepId", -1);

        mSweep = YaV1.sSweep.duplicateSet(mSweepId);

        // we show our view
        setContentView(R.layout.yav1_sweepset_edit_activity);

        // get the list view
        mListView = getListView();

        // setup the list
        setList();

        // listener
        ImageButton editName = (ImageButton) findViewById(R.id.editname);
        Button      onSave   = (Button) findViewById(R.id.save);
        Button      onCancel = (Button) findViewById(R.id.cancel);

        if(!mView)
        {
            editName.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    final TextView tvT = (TextView) findViewById(R.id.sweep_name);
                    final EditText input = new EditText(mContext);
                    input.setText(mSweep.getName());
                    new AlertDialog.Builder(mContext)
                            .setTitle(R.string.sweep_edit_name_title)
                            .setMessage(R.string.sweep_edit_name_message)
                            .setView(input)
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                            {
                                public void onClick(DialogInterface dialog, int whichButton)
                                {
                                    // we will duplicate the given one using the id
                                    String value = input.getText().toString();
                                    mSweep.setName(value);
                                    tvT.setText(value);
                                }
                            }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int whichButton)
                        {
                            // Do nothing.
                        }
                    }).show();
                }
            });

            onSave.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    // we have to save the new values
                    YaV1.sSweep.updateSweep(mSweepId, mSweep);
                    // finish the activity with ok
                    Intent response = new Intent();
                    setResult(Activity.RESULT_OK, response);
                    finish();
                    return;
                }
            });
        }
        else
        {
            editName.setVisibility(View.GONE);
            onSave.setVisibility(View.GONE);
        }

        onCancel.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Intent response = new Intent();
                setResult(Activity.RESULT_CANCELED, response);
                finish();
                return;
            }
        });
    }

    // we click on an adpater

    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        if(mView)
            return;

        // retreive the sweep definition
        final SweepDefinition eDef  = mSweep.getSweepDefinition(position);

        new DialogSweepEdge(this, eDef)
        {
            public void handleDismiss(boolean isOk)
            {
                if(isOk)
                {
                    SweepDefinition nDef = getSweepDefinition();
                    eDef.setLowerFrequencyEdge(nDef.getLowerFrequencyEdge());
                    eDef.setUpperFrequencyEdge(nDef.getUpperFrequencyEdge());
                }

                // dismiss the dialog
                dismiss();

                // invalidate the content
                mAdapter.notifyDataSetChanged();
            }
        }.show();
    }

    @Override
    protected void onResume()
    {
        // YaV1.superResume();
        super.onResume();
    }

    @Override
    public void onPostResume()
    {
        super.onPostResume();
        YaV1.superResume();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }


    @Override
    public void onPause()
    {
        YaV1.superPause();
        super.onPause();
    }

    private void setList()
    {
        if(mAdapter == null)
            mAdapter = new MySweepAdapter(this);

        setListAdapter(mAdapter);
        // set the name
        ((TextView) findViewById(R.id.sweep_name)).setText(mSweep.getName());
    }

    private class MySweepAdapter extends BaseAdapter
    {
        private final Context context;

        public MySweepAdapter(Context context)
        {
            this.context = context;
        }

        @Override
        public int getCount()
        {
            return mSweep.getSize();
        }

        @Override
        public Object getItem(int position)
        {
            return mSweep.getSweepDefinition(position);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            LayoutInflater layoutInflater = LayoutInflater.from(context);

            if(convertView == null)
                convertView = layoutInflater.inflate(R.layout.sweep_edit_row, parent, false);

            SweepDefinition swp = mSweep.getSweepDefinition(position);

            if(swp != null)
            {
                TextView  lower   = (TextView) convertView.findViewById(R.id.lower);
                TextView  upper   = (TextView) convertView.findViewById(R.id.upper);
                lower.setText(swp.getLowerFrequencyEdge().toString());
                upper.setText(swp.getUpperFrequencyEdge().toString());
            }

            return convertView;
        }
    }
}
