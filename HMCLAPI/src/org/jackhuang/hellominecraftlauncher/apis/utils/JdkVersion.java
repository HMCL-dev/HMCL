/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.utils;

/**
 *
 * @author hyh
 */
public final class JdkVersion {
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
	 * Constant identifying the 1.8 JVM (Java 7).
	 */
	public static final int JAVA_18 = 5;
        
	private static final String javaVersion;
	private static final int majorJavaVersion;
	static {
		javaVersion = System.getProperty("java.version");
		// version String should look like "1.4.2_10"
		if (javaVersion.contains("1.8.")) {
			majorJavaVersion = JAVA_18;
		}
		else if (javaVersion.contains("1.7.")) {
			majorJavaVersion = JAVA_17;
		}
		else if (javaVersion.contains("1.6.")) {
			majorJavaVersion = JAVA_16;
		}
		else {
			// else leave 1.5 as default (it's either 1.5 or unknown)
			majorJavaVersion = JAVA_15;
		}
	}
	/**
	 * Return the full Java version string, as returned by
	 * <code>System.getProperty("java.version")</code>.
	 * @return the full Java version string
	 * @see System#getProperty(String)
	 */
	public static String getJavaVersion() {
		return javaVersion;
	}
	/**
	 * Get the major version code. This means we can do things like
	 * <code>if (getMajorJavaVersion() < JAVA_14)</code>.
	 * @return a code comparable to the JAVA_XX codes in this class
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
	 * Convenience method to determine if the current JVM is at least
	 * Java 1.6 (Java 6).
	 * @return <code>true</code> if the current JVM is at least Java 1.6
	 * @deprecated as of Spring 3.0, in favor of reflective checks for
	 * the specific Java 1.6 classes of interest
	 * @see #getMajorJavaVersion()
	 * @see #JAVA_16
	 * @see #JAVA_17
	 */
	@Deprecated
	public static boolean isAtLeastJava16() {
		return (majorJavaVersion >= JAVA_16);
	}
}
