package com.glasslsoftware.yav1.functional;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * [QA-FUNC] Locates and reads files shipped with the app (assets, manifest)
 * from a JVM unit test.
 *
 * Gradle runs unit tests with the module directory (YaV1/) as the working
 * directory; some runners use the repository root, so both are tried (the
 * same pattern the existing StateResolverTest uses).
 */
public final class RepoFile
{
    private RepoFile()
    {
    }

    /** Resolve a path given relative to the YaV1 module directory. */
    public static File find(String moduleRelativePath) throws IOException
    {
        String[] candidates = {
            moduleRelativePath,
            "YaV1/" + moduleRelativePath,
            "../YaV1/" + moduleRelativePath,
        };

        for(String c : candidates)
        {
            File f = new File(c);
            if(f.isFile())
                return f;
        }

        throw new IOException(moduleRelativePath + " not found from "
                              + new File(".").getAbsolutePath());
    }

    /** Whole file as UTF-8 text. */
    public static String read(File f) throws IOException
    {
        FileInputStream in = new FileInputStream(f);
        try
        {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            int n;
            while(off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0)
                off += n;
            return new String(buf, 0, off, Charset.forName("UTF-8"));
        }
        finally
        {
            in.close();
        }
    }

    /**
     * The hex packet lines of a demo .dat file (assets/demo), with comment
     * ("//...") and tutorial ("&lt;...&gt;") lines dropped.
     */
    public static List<String> demoHexLines(File demoFile) throws IOException
    {
        List<String> out = new ArrayList<String>();

        BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(demoFile), "UTF-8"));
        try
        {
            String line;
            while((line = br.readLine()) != null)
            {
                line = line.trim();
                if(line.isEmpty() || line.startsWith("//") || line.startsWith("<"))
                    continue;
                out.add(line);
            }
        }
        finally
        {
            br.close();
        }

        return out;
    }
}
