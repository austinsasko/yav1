package com.franckyl.yav1;

import android.util.Log;

import com.valentine.esp.data.SweepDefinition;

import java.util.ArrayList;

/**
 * Created by franck on 7/31/13.
 */
public class YaV1Sweep
{
    private ArrayList<SweepDefinition> mSweep = new ArrayList<SweepDefinition>();
    private String                     mName  = "No name";
    private int                        mId;

    // constructor

    public YaV1Sweep(int id)
    {
        // we build up to Sweep Number sweep definition
        mId             = id;
        SweepDefinition swp;

        for(int i = 0; i < YaV1SweepSets.MAX_SWEEP_NUMBER; i++)
        {
            swp = new SweepDefinition();
            swp.setIndex(i);
            swp.setLowerFrequencyEdge(0);
            swp.setUpperFrequencyEdge(0);
            mSweep.add(swp);
        }
    }

    // second constructor for current sweeps
    public YaV1Sweep(int id, ArrayList<SweepDefinition> iList)
    {
        // we build up to Sweep Number sweep definition
        mId             = id;
        mSweep          = iList;
    }

    // copy constructor

    public YaV1Sweep(int id, YaV1Sweep src, String name)
    {
        mId                 = id;
        mName               = name;
        int i               = 0;
        SweepDefinition t;

        ArrayList<SweepDefinition> source = src.getSweep();

        for(SweepDefinition s: source)
        {
            t = new SweepDefinition();
            t.setLowerFrequencyEdge(s.getLowerFrequencyEdge());
            t.setUpperFrequencyEdge(s.getUpperFrequencyEdge());
            t.setIndex(i++);
            mSweep.add(t);
        }

        // reorganize
        reorgSweep();
    }
    // get the Id

    public int getId()
    {
        return mId;
    }

    // get the size

    public int getSize()
    {
        return mSweep.size();
    }

    // get the sweep definition at given position

    public SweepDefinition getSweepDefinition(int position)
    {
        return mSweep.get(position);
    }
    // count non empty sweep

    public int countNonEmpty()
    {
        int nb = 0;
        for(SweepDefinition s: mSweep)
        {
            if(s.getLowerFrequencyEdge() != 0 && s.getUpperFrequencyEdge() != 0)
                nb++;
        }

        return nb;
    }
    // set/get the name of this Set

    public void setName(String name)
    {
        mName = name;
    }

    public String getName()
    {
        return mName;
    }

    // set sweepdefintion

    public void setSweepDefinition(ArrayList<SweepDefinition> iList)
    {
        mSweep = iList;
    }

    // get the sweepDefinition

    public ArrayList<SweepDefinition> getSweep()
    {
        return mSweep;
    }

    // is default factory ?

    public boolean isFactoryDefault()
    {
        return (mId == 0 ? true : false);
    }

    // check if the given definition is same as this one

    public boolean isSame(YaV1Sweep n)
    {
        if(countNonEmpty() != n.countNonEmpty())
            return false;

        ArrayList<SweepDefinition> tgt = n.getSweep();
        int currLow;
        int currUpper;
        int Low;
        int Upper;

        for(int i = 0; i < YaV1SweepSets.MAX_SWEEP_NUMBER; i++)
        {
            Low     = mSweep.get(i).getLowerFrequencyEdge();
            Upper   = mSweep.get(i).getUpperFrequencyEdge();

            currLow   = tgt.get(i).getLowerFrequencyEdge();
            currUpper = tgt.get(i).getUpperFrequencyEdge();

            if(Low != currLow || Upper != currUpper)
                return false;
        }
        return true;
    }

    // reorganize the seeps, to get non empty in beginning

    public boolean reorgSweep()
    {
        int nb = countNonEmpty();

        // if all empty or all full no need to reorg

        if(nb > 0 && nb < YaV1SweepSets.MAX_SWEEP_NUMBER)
        {
            ArrayList<SweepDefinition> notEmpty = new ArrayList<SweepDefinition>();
            SweepDefinition swd;

            int i = 0;
            for(SweepDefinition s: mSweep)
            {
                if(s.getUpperFrequencyEdge() != 0 && s.getLowerFrequencyEdge() != 0)
                {
                    s.setIndex(i);
                    notEmpty.add(s);
                    ++i;
                }
            }

            // complete to MAX_SWEEP_NUMBER
            while(i < YaV1SweepSets.MAX_SWEEP_NUMBER)
            {
                swd = new SweepDefinition();
                swd.setLowerFrequencyEdge(0);
                swd.setUpperFrequencyEdge(0);
                swd.setIndex(i);
                notEmpty.add(swd);
                i++;
            }

            // replace the list
            mSweep = notEmpty;
            return true;
        }

        return false;
    }

    // check sweep validity

    public boolean isValid()
    {
        int low;
        int upper;
        int nb = 0;

        for(int i=0; i < YaV1SweepSets.MAX_SWEEP_NUMBER; i++)
        {
            low     = mSweep.get(i).getLowerFrequencyEdge();
            upper   = mSweep.get(i).getUpperFrequencyEdge();

            if(low == 0 && upper == 0)
                continue;

            // check boundaries
            if(upper < low || !YaV1.sSweep.checkSweep(low, upper))
                return false;
            nb++;
        }

        return (nb > 0);
    }

    public void logSweep()
    {
        Log.d("Valentine", "Sweep id " + mId);
        for(SweepDefinition d: mSweep)
            Log.d("Valentine", "After sweep push " + d.getIndex() + ": " + d.getLowerFrequencyEdge() + " " + d.getUpperFrequencyEdge());
    }
}
