package com.glasslsoftware.yav1.functional;

import android.util.SparseArray;

import java.util.TreeMap;

/**
 * [QA-FUNC] A functioning android.util.SparseArray for the JVM.
 *
 * Unit tests run against the mockable android.jar with returnDefaultValues,
 * whose SparseArray silently does nothing (put is a no-op, size() is 0).
 * GetAlertData keeps its per-cycle alert table in a SparseArray, so without
 * a working implementation no alert table would ever complete on the JVM.
 * Tests inject this subclass into the (package-private) field via
 * reflection; only the methods GetAlertData uses are implemented.
 */
public class WorkingSparseArray<E> extends SparseArray<E>
{
    private final TreeMap<Integer, E> mMap = new TreeMap<Integer, E>();

    @Override
    public void put(int key, E value)
    {
        mMap.put(key, value);
    }

    @Override
    public E get(int key)
    {
        return mMap.get(key);
    }

    @Override
    public E get(int key, E valueIfKeyNotFound)
    {
        E v = mMap.get(key);
        return v != null ? v : valueIfKeyNotFound;
    }

    @Override
    public int size()
    {
        return mMap.size();
    }

    @Override
    public E valueAt(int index)
    {
        int i = 0;
        for(E v : mMap.values())
        {
            if(i == index)
                return v;
            i++;
        }
        return null;
    }

    @Override
    public void clear()
    {
        mMap.clear();
    }

    @Override
    public void remove(int key)
    {
        mMap.remove(key);
    }
}
