package com.glasslsoftware.yav1.functional;

import com.glasslsoftware.yav1.YaV1BsmFilter;

import java.lang.reflect.Method;

/**
 * [QA-FUNC] Access to the package-private test seams of the app packages.
 *
 * This suite deliberately lives in its own packages
 * (com.glasslsoftware.yav1.functional / .regression) so it can never collide
 * file-wise with the per-class unit-test suites; reflection stands in for
 * package access where a seam is package-private.
 */
public final class TestSeams
{
    private TestSeams()
    {
    }

    /** YaV1BsmFilter.setEnabled is the package-private preference seam. */
    public static void setBsmFilterEnabled(boolean enabled)
    {
        try
        {
            Method m = YaV1BsmFilter.class.getDeclaredMethod("setEnabled", boolean.class);
            m.setAccessible(true);
            m.invoke(null, enabled);
        }
        catch(Exception e)
        {
            throw new AssertionError("unable to toggle YaV1BsmFilter", e);
        }
    }
}
