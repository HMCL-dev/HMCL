/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author hyh
 */
public final class JdkVersion {

    public String ver;
    /**
     * -1 - unkown 0 - 32Bit 1 - 64Bit
     */
    public int is64Bit;

    public JdkVersion(String ver, int is64Bit) {
        this.ver = ver;
        this.is64Bit = is64Bit;
    }

    /**
     * Constant identifying the 1.5 JVM (Java 5).
     */
    public static final int UNKOWN = 2;
    /**
     * Constant identifying the 1.5 JVM (Java 5).
     */
    public static final int JAVA_15 = 2;
    /**
     * Constant identifying the 1.6 JVM (Java 6).
     */
    public static final int JAVA_16 = 3;
    /**
     * Constant identifying the 1.7 JVM (Java 7).
     */
    public static final int JAVA_17 = 4;
    /**
     * Constant identifying the 1.8 JVM (Java 8).
     */
    public static final int JAVA_18 = 5;
    /**
     * Constant identifying the 1.9 JVM (Java 9).
     */
    public static final int JAVA_19 = 6;

    private static final String javaVersion;
    private static final int majorJavaVersion;

    static {
        javaVersion = System.getProperty("java.version");
        // version String should look like "1.4.2_10"
        if (javaVersion.contains("1.9.")) {
            majorJavaVersion = JAVA_18;
        } else if (javaVersion.contains("1.8.")) {
            majorJavaVersion = JAVA_18;
        } else if (javaVersion.contains("1.7.")) {
            majorJavaVersion = JAVA_17;
        } else if (javaVersion.contains("1.6.")) {
            majorJavaVersion = JAVA_16;
        } else {
            // else leave 1.5 as default (it's either 1.5 or unknown)
            majorJavaVersion = JAVA_15;
        }
    }

    /**
     * Return the full Java version string, as returned by
     * <code>System.getProperty("java.version")</code>.
     *
     * @return the full Java version string
     * @see System#getProperty(String)
     */
    public static String getJavaVersion() {
        return javaVersion;
    }

    /**
     * Get the major version code. This means we can do things like
     * <code>if (getMajorJavaVersion() < JAVA_14)</code>. @retu
     *
     *
     * rn a code comparable to the JAVA_XX codes in this class
     * @return 
     * @see #JAVA_13
     * @see #JAVA_14
     * @see #JAVA_15
     * @see #JAVA_16
     * @see #JAVA_17
     */
    public static int getMajorJavaVersion() {
        return majorJavaVersion;
    }

    /**
     * Convenience method to determine if the current JVM is at least Java 1.6
     * (Java 6).
     *
     * @return <code>true</code> if the current JVM is at least Java 1.6
     * @deprecated as of Spring 3.0, in favor of reflective checks for the
     * specific Java 1.6 classes of interest
     * @see #getMajorJavaVersion()
     * @see #JAVA_16
     * @see #JAVA_17
     */
    @Deprecated
    public static boolean isAtLeastJava16() {
        return (majorJavaVersion >= JAVA_16);
    }

    public static boolean isJava64Bit() {
        String jdkBit = System.getProperty("sun.arch.data.model");
        return jdkBit.contains("64");
    }

    static Pattern p = Pattern.compile("java version \"[1-9]*\\.[1-9]*\\.[0-9]*(.*?)\"");

    public static JdkVersion getJavaVersionFromExecutable(String file) throws IOException {
        String[] str = new String[]{file, "-version"};
        ProcessBuilder pb = new ProcessBuilder(str);
        JavaProcess jp = new JavaProcess(str, pb.start(), null);
        InputStream is = jp.getRawProcess().getErrorStream();
        BufferedReader br = null;
        int lineNumber = 0;
        String ver = null;
        int is64Bit = -1;
        try {
            br = new BufferedReader(new InputStreamReader(is));
            String line;
            jp.getRawProcess().waitFor();
            while ((line = br.readLine()) != null) {
                lineNumber++;
                switch (lineNumber) {
                    case 1:
                        Matcher m = p.matcher(line);
                        if (m.find()) {
                            ver = m.group();
                            ver = ver.substring("java version \"".length(), ver.length() - 1);
                        }
                        break;
                    case 3:
                        if (line.contains("64-Bit")) {
                            is64Bit = 1;
                        } else {
                            is64Bit = 0;
                        }
                        break;
                }
            }
        } catch (InterruptedException | IOException e) {
            HMCLog.warn("Failed to get java version", e);
        } finally {
            if (br != null) {
                br.close();
            }
        }
        return new JdkVersion(ver, is64Bit);
    }

    public void write(File f) throws IOException {
        if (ver != null && is64Bit != -1) {
            FileUtils.write(f, ver + "\n" + is64Bit);
        }
    }
    
    public boolean isEarlyAccess() {
        return ver != null && ver.endsWith("-ea");
    }
}
