package com.franckyl.yav1.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;

import java.util.HashMap;
import java.util.List;

/**
 * Created by franck on 8/14/14.
 */
public class BandRangeListAdapter extends BaseExpandableListAdapter
{
    private Context mContext;
    private List<Integer> mDataHeader;
    private HashMap<Integer, List<Pair<Integer, Integer>>> mRangeList;
    private LayoutInflater mInflater;

    static class headerHolder
    {
        int    band;
    }

    static class childHolder
    {
        int    band;
        int    position;
    }

    // constructor

    public BandRangeListAdapter(Context context, List<Integer> header, HashMap<Integer, List<Pair<Integer, Integer>>> dataList)
    {
        mDataHeader = header;
        mRangeList  = dataList;
        mContext    = context;

        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    }

    @Override
    public Object getChild(int groupPosition, int childPosition)
    {
        return mRangeList.get(mDataHeader.get(groupPosition)).get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition)
    {
        return childPosition;
    }

    public View getChildView(int groupPosition, final int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent)
    {

        final Pair<Integer, Integer> child = (Pair<Integer, Integer>) getChild(groupPosition, childPosition);

        childHolder chHolder;
        ImageButton img;

        if(convertView == null)
        {
            convertView = mInflater.inflate(R.layout.bande_range_list_item, null);
            chHolder = new childHolder();
            img = (ImageButton) convertView.findViewById(R.id.removeButton);
            img.setTag(chHolder);
            img.setFocusable(false);
            img.setClickable(false);
            img.setOnClickListener(removeRange);
        }
        else
        {
            img = (ImageButton) convertView.findViewById(R.id.removeButton);
            chHolder = (childHolder) img.getTag();
        }

        chHolder.band = mDataHeader.get(groupPosition);
        chHolder.position = childPosition;

        // we write the text view, and we store the button for the Add band
        TextView txtListChild = (TextView) convertView.findViewById(R.id.rangeText);

        txtListChild.setText(child.first.toString() + " - " + child.second.toString());
        return convertView;
    }

    @Override
    public int getChildrenCount(int groupPosition)
    {
        return mRangeList.get(mDataHeader.get(groupPosition)).size();
    }

    @Override
    public Object getGroup(int groupPosition)
    {
        return mDataHeader.get(groupPosition);
    }

    @Override
    public int getGroupCount()
    {
        return mDataHeader.size();
    }

    @Override
    public long getGroupId(int groupPosition)
    {
        return groupPosition;
    }

    // the view for the group

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent)
    {
        String headerTitle = YaV1.BandBoundaries.getBandStrFromInt((Integer) getGroup(groupPosition));
        headerTitle.toUpperCase();
        headerTitle += " Band";
        headerHolder holder;

        ImageButton img;
        if(convertView == null)
        {
            convertView = mInflater.inflate(R.layout.bande_range_group_item, null);
            holder = new headerHolder();
            img = (ImageButton) convertView.findViewById(R.id.addButton);
            img.setClickable(true);
            img.setFocusable(false);
            img.setTag(holder);
            img.setOnClickListener(addInGroup);
        }
        else
        {
            img = (ImageButton) convertView.findViewById(R.id.addButton);
            holder = (headerHolder) img.getTag();
        }

        holder.band = mDataHeader.get(groupPosition);

        img.setVisibility(!isExpanded ? View.INVISIBLE : View.VISIBLE);

        TextView lblListHeader = (TextView) convertView.findViewById(R.id.rangeHeader);
        lblListHeader.setTypeface(null, Typeface.BOLD);
        lblListHeader.setText(headerTitle);

        return convertView;
    }

    @Override
    public boolean hasStableIds()
    {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition)
    {
        return true;
    }

    // we add a range

    final View.OnClickListener addInGroup = new View.OnClickListener()
    {
        public void onClick(final View v)
        {
            final headerHolder h = (headerHolder) v.getTag();
            editCreateRange(null, h.band, -1);
        }
    };

    // we remove a range

    final View.OnClickListener removeRange = new View.OnClickListener()
    {
        public void onClick(final View v)
        {
            final childHolder h = (childHolder) v.getTag();
            new AlertDialog.Builder(mContext)
                    .setTitle(R.string.band_range_remove_title)
                    .setMessage(R.string.band_range_remove_text)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int whichButton)
                            {
                                mRangeList.get(h.band).remove(h.position);
                                notifyDataSetChanged();
                            }})
                    .setNegativeButton(R.string.no, null)
                    .show();
        }
    };

    public void editCreateRange(Pair<Integer, Integer> p, final int band, final int position)
    {
        new BandRangeCapture(mContext, p, band, position)
        {
            public void handleDismiss(boolean valid)
            {
                if(valid)
                {
                    if(mRange == null)
                        mRangeList.get(band).remove(position);
                    else
                    {
                        // we can add the captured Pair
                        if(mId < 0)
                            mRangeList.get(band).add(mRange);
                        else
                            mRangeList.get(band).set(position, mRange);
                    }
                    notifyDataSetChanged();
                }
                super.handleDismiss(valid);
            }
        }.show();
    }
}
