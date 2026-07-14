package com.valentine.esp.bluetooth;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class V1connectionLEFrameTest
{
    @Test
    public void packRoundTripPreservesEscapedBytes()
    {
        byte[] espFrame = new byte[] {
                (byte) 0xAA, 0x01, 0x02, 0x03, 0x02, 0x7F, 0x7D, (byte) 0xAB
        };

        byte[] packed = V1connectionLE.wrapInPackFraming(espFrame);

        assertTrue(containsSequence(packed, new byte[] {0x7D, 0x5F}));
        assertTrue(containsSequence(packed, new byte[] {0x7D, 0x5D}));
        assertArrayEquals(espFrame, V1connectionLE.stripPackFraming(packed));
    }

    @Test
    public void malformedPackFramesAreRejected()
    {
        assertNull(V1connectionLE.stripPackFraming(null));
        assertNull(V1connectionLE.stripPackFraming(new byte[] {0x7F, 0x01, 0x7F}));
        assertNull(V1connectionLE.stripPackFraming(
                new byte[] {0x00, 0x04, (byte) 0xAA, (byte) 0xAB, 0x00, 0x7F}));
        assertNull(V1connectionLE.stripPackFraming(
                new byte[] {0x7F, 0x04, (byte) 0xAA, (byte) 0xAB, 0x00, 0x00}));
    }

    private static boolean containsSequence(byte[] haystack, byte[] needle)
    {
        for(int i = 0; i <= haystack.length - needle.length; i++)
        {
            boolean matches = true;
            for(int j = 0; j < needle.length; j++)
            {
                if(haystack[i + j] != needle[j])
                {
                    matches = false;
                    break;
                }
            }
            if(matches)
                return true;
        }
        return false;
    }
}
