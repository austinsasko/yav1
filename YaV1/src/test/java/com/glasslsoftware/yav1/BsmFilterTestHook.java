package com.glasslsoftware.yav1;

/**
 * [QA] Test-only bridge to YaV1BsmFilter's package-private test switch so
 * integration tests outside this package can toggle the filter.
 */
public final class BsmFilterTestHook
{
    private BsmFilterTestHook()
    {
    }

    public static void setEnabled(boolean enabled)
    {
        YaV1BsmFilter.setEnabled(enabled);
    }
}
