package com.franckyl.yav1.utils;

import android.util.Log;

import com.franckyl.yav1.YaV1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by franck on 12/19/14.
 */
public class MiscUtils
{
    // function to check for a font increase file
    public static int checkFontIncrease()
    {
        int    rc = 0;
        File file = new File(YaV1.sStorageDir + "/font_ratio");

        if(file.exists())
        {
            // read the first line and try to find a numeric value
            try
            {
                FileInputStream f = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(f));

                // read the ratio for the font
                String line = reader.readLine();

                if(line != null)
                {
                    try
                    {
                        rc = Integer.valueOf(line);
                    }
                    catch(NumberFormatException e)
                    {
                    }
                }

                f.close();
            }
            catch(FileNotFoundException ex)
            {

            }
            catch(IOException ex)
            {

            }
        }

        return rc;
    }
}
