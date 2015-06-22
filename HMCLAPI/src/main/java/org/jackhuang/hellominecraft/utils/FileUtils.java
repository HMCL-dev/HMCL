/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author hyh
 */
public class FileUtils {

    public static void deleteDirectory(File directory)
	    throws IOException {
	if (!directory.exists()) {
	    return;
	}

	if (!isSymlink(directory)) {
	    cleanDirectory(directory);
	}

	if (!directory.delete()) {
	    String message = "Unable to delete directory " + directory + ".";

	    throw new IOException(message);
	}
    }
    
    public static boolean deleteDirectoryQuietly(File directory) {
        try {
            deleteDirectory(directory);
            return true;
        } catch(Exception e) {
            HMCLog.err("Failed to delete directory " + directory, e);
            return false;
        }
    }
    
    public static boolean cleanDirectoryQuietly(File directory) {
        try {
            cleanDirectory(directory);
            return true;
        } catch(Exception e) {
            HMCLog.err("Failed to clean directory " + directory, e);
            return false;
        }
    }

    public static void cleanDirectory(File directory)
	    throws IOException {
	if (!directory.exists()) {
	    //String message = directory + " does not exist";
	    //throw new IllegalArgumentException(message);
            directory.mkdirs();
            return;
	}

	if (!directory.isDirectory()) {
	    String message = directory + " is not a directory";
	    throw new IllegalArgumentException(message);
	}

	File[] files = directory.listFiles();
	if (files == null) {
	    throw new IOException("Failed to list contents of " + directory);
	}

	IOException exception = null;
	for (File file : files) {
	    try {
		forceDelete(file);
	    } catch (IOException ioe) {
		exception = ioe;
	    }
	}

	if (null != exception) {
	    throw exception;
	}
    }

    public static void forceDelete(File file)
	    throws IOException {
	if (file.isDirectory()) {
	    deleteDirectory(file);
	} else {
	    boolean filePresent = file.exists();
	    if (!file.delete()) {
		if (!filePresent) {
		    throw new FileNotFoundException("File does not exist: " + file);
		}
		String message = "Unable to delete file: " + file;

		throw new IOException(message);
	    }
	}
    }

    public static boolean isSymlink(File file)
	    throws IOException {
	if (file == null) {
	    throw new NullPointerException("File must not be null");
	}
	if (File.separatorChar == '\\') {
	    return false;
	}
	File fileInCanonicalDir;
	if (file.getParent() == null) {
	    fileInCanonicalDir = file;
	} else {
	    File canonicalDir = file.getParentFile().getCanonicalFile();
	    fileInCanonicalDir = new File(canonicalDir, file.getName());
	}

	return !fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile());
    }

    public static void copyDirectory(File srcDir, File destDir)
	    throws IOException {
	copyDirectory(srcDir, destDir, true);
    }

    public static void copyDirectory(File srcDir, File destDir, boolean preserveFileDate)
	    throws IOException {
	copyDirectory(srcDir, destDir, null, preserveFileDate);
    }

    public static void copyDirectory(File srcDir, File destDir, FileFilter filter)
	    throws IOException {
	copyDirectory(srcDir, destDir, filter, true);
    }

    public static void copyDirectory(File srcDir, File destDir, FileFilter filter, boolean preserveFileDate)
	    throws IOException {
	if (srcDir == null) {
	    throw new NullPointerException("Source must not be null");
	}
	if (destDir == null) {
	    throw new NullPointerException("Destination must not be null");
	}
	if (!srcDir.exists()) {
	    throw new FileNotFoundException("Source '" + srcDir + "' does not exist");
	}
	if (!srcDir.isDirectory()) {
	    throw new IOException("Source '" + srcDir + "' exists but is not a directory");
	}
	if (srcDir.getCanonicalPath().equals(destDir.getCanonicalPath())) {
	    throw new IOException("Source '" + srcDir + "' and destination '" + destDir + "' are the same");
	}

	List exclusionList = null;
	if (destDir.getCanonicalPath().startsWith(srcDir.getCanonicalPath())) {
	    File[] srcFiles = filter == null ? srcDir.listFiles() : srcDir.listFiles(filter);
	    if ((srcFiles != null) && (srcFiles.length > 0)) {
		exclusionList = new ArrayList(srcFiles.length);
		for (File srcFile : srcFiles) {
		    File copiedFile = new File(destDir, srcFile.getName());
		    exclusionList.add(copiedFile.getCanonicalPath());
		}
	    }
	}
	doCopyDirectory(srcDir, destDir, filter, preserveFileDate, exclusionList);
    }

    private static void doCopyDirectory(File srcDir, File destDir, FileFilter filter, boolean preserveFileDate, List<String> exclusionList)
	    throws IOException {
	File[] srcFiles = filter == null ? srcDir.listFiles() : srcDir.listFiles(filter);
	if (srcFiles == null) {
	    throw new IOException("Failed to list contents of " + srcDir);
	}
	if (destDir.exists()) {
	    if (!destDir.isDirectory()) {
		throw new IOException("Destination '" + destDir + "' exists but is not a directory");
	    }
	} else if ((!destDir.mkdirs()) && (!destDir.isDirectory())) {
	    throw new IOException("Destination '" + destDir + "' directory cannot be created");
	}

	if (!destDir.canWrite()) {
	    throw new IOException("Destination '" + destDir + "' cannot be written to");
	}
	for (File srcFile : srcFiles) {
	    File dstFile = new File(destDir, srcFile.getName());
	    if ((exclusionList == null) || (!exclusionList.contains(srcFile.getCanonicalPath()))) {
		if (srcFile.isDirectory()) {
		    doCopyDirectory(srcFile, dstFile, filter, preserveFileDate, exclusionList);
		} else {
		    doCopyFile(srcFile, dstFile, preserveFileDate);
		}
	    }

	}

	if (preserveFileDate) {
	    destDir.setLastModified(srcDir.lastModified());
	}
    }

    public static String readFileToString(File file)
	    throws IOException {
	return NetUtils.getStreamContent(IOUtils.openInputStream(file));
    }
    
    public static String readFileToStringQuietly(File file) {
        try {
            return NetUtils.getStreamContent(IOUtils.openInputStream(file));
        } catch (IOException ex) {
            HMCLog.err("Failed to read file: " + file, ex);
            return null;
        }
    }

    public static String readFileToString(File file, String charset)
	    throws IOException {
	return NetUtils.getStreamContent(IOUtils.openInputStream(file), charset);
    }
    
    public static String readFileToStringIgnoreFileNotFound(File file) throws IOException {
	try {
	    return NetUtils.getStreamContent(IOUtils.openInputStream(file));
	} catch (FileNotFoundException ex) {
	    return "";
	}
    }

    public static void copyFile(File srcFile, File destFile)
	    throws IOException {
	copyFile(srcFile, destFile, true);
    }

    public static void copyFile(File srcFile, File destFile, boolean preserveFileDate)
	    throws IOException {
	if (srcFile == null) {
	    throw new NullPointerException("Source must not be null");
	}
	if (destFile == null) {
	    throw new NullPointerException("Destination must not be null");
	}
	if (!srcFile.exists()) {
	    throw new FileNotFoundException("Source '" + srcFile + "' does not exist");
	}
	if (srcFile.isDirectory()) {
	    throw new IOException("Source '" + srcFile + "' exists but is a directory");
	}
	if (srcFile.getCanonicalPath().equals(destFile.getCanonicalPath())) {
	    throw new IOException("Source '" + srcFile + "' and destination '" + destFile + "' are the same");
	}
	File parentFile = destFile.getParentFile();
	if ((parentFile != null)
		&& (!parentFile.mkdirs()) && (!parentFile.isDirectory())) {
	    throw new IOException("Destination '" + parentFile + "' directory cannot be created");
	}

	if ((destFile.exists()) && (!destFile.canWrite())) {
	    throw new IOException("Destination '" + destFile + "' exists but is read-only");
	}
	doCopyFile(srcFile, destFile, preserveFileDate);
    }

    private static void doCopyFile(File srcFile, File destFile, boolean preserveFileDate)
	    throws IOException {
	if ((destFile.exists()) && (destFile.isDirectory())) {
	    throw new IOException("Destination '" + destFile + "' exists but is a directory");
	}

	FileInputStream fis = null;
	FileOutputStream fos = null;
	FileChannel input = null;
	FileChannel output = null;
	try {
	    fis = new FileInputStream(srcFile);
	    fos = new FileOutputStream(destFile);
	    input = fis.getChannel();
	    output = fos.getChannel();
	    long size = input.size();
	    long pos = 0L;
	    long count;
	    while (pos < size) {
		count = size - pos > 31457280L ? 31457280L : size - pos;
		pos += output.transferFrom(input, pos, count);
	    }
	} finally {
	    IOUtils.closeQuietly(output);
	    IOUtils.closeQuietly(fos);
	    IOUtils.closeQuietly(input);
	    IOUtils.closeQuietly(fis);
	}

	if (srcFile.length() != destFile.length()) {
	    throw new IOException("Failed to copy full contents from '" + srcFile + "' to '" + destFile + "'");
	}

	if (preserveFileDate) {
	    destFile.setLastModified(srcFile.lastModified());
	}
    }

    public static int indexOfLastSeparator(String filename) {
	if (filename == null) {
	    return -1;
	}
	int lastUnixPos = filename.lastIndexOf(47);
	int lastWindowsPos = filename.lastIndexOf(92);
	return Math.max(lastUnixPos, lastWindowsPos);
    }

    public static int indexOfExtension(String filename) {
	if (filename == null) {
	    return -1;
	}
	int extensionPos = filename.lastIndexOf(46);
	int lastSeparator = indexOfLastSeparator(filename);
	return lastSeparator > extensionPos ? -1 : extensionPos;
    }

    public static String getName(String filename) {
	if (filename == null) {
	    return null;
	}
	int index = indexOfLastSeparator(filename);
	return filename.substring(index + 1);
    }

    public static String getBaseName(String filename) {
	return removeExtension(getName(filename));
    }

    public static String getExtension(String filename) {
	if (filename == null) {
	    return null;
	}
	int index = indexOfExtension(filename);
	if (index == -1) {
	    return "";
	}
	return filename.substring(index + 1);
    }

    public static String removeExtension(String filename) {
	if (filename == null) {
	    return null;
	}
	int index = indexOfExtension(filename);
	if (index == -1) {
	    return filename;
	}
	return filename.substring(0, index);
    }
    
    public static void writeQuietly(File file, CharSequence data) {
        try {
            write(file, data);
        } catch(IOException e) {
            HMCLog.warn("Failed to write data to file: " + file, e);
        }
    }

    public static void write(File file, CharSequence data)
	    throws IOException {
	write(file, data, "UTF-8", false);
    }

    public static void write(File file, CharSequence data, boolean append)
	    throws IOException {
	write(file, data, "UTF-8", append);
    }

    public static void write(File file, CharSequence data, String encoding)
	    throws IOException {
	write(file, data, encoding, false);
    }

    public static void write(File file, CharSequence data, String encoding, boolean append)
	    throws IOException {
	String str = data == null ? null : data.toString();
	writeStringToFile(file, str, encoding, append);
    }

    public static void writeStringToFile(File file, String data)
	    throws IOException {
	writeStringToFile(file, data, "UTF-8", false);
    }

    public static void writeStringToFile(File file, String data, String encoding)
	    throws IOException {
	writeStringToFile(file, data, encoding, false);
    }

    public static void writeStringToFile(File file, String data, String encoding, boolean append)
	    throws IOException {
	OutputStream out = null;
	try {
	    out = openOutputStream(file, append);
	    IOUtils.write(data, out, encoding);
	    out.close();
	} finally {
	    IOUtils.closeQuietly(out);
	}
    }

    public static FileInputStream openInputStream(File file)
	    throws IOException {
	if (file.exists()) {
	    if (file.isDirectory()) {
		throw new IOException("File '" + file + "' exists but is a directory");
	    }
	    if (!file.canRead()) {
		throw new IOException("File '" + file + "' cannot be read");
	    }
	} else {
	    throw new FileNotFoundException("File '" + file + "' does not exist");
	}
	return new FileInputStream(file);
    }

    public static FileOutputStream openOutputStream(File file)
	    throws IOException {
	return openOutputStream(file, false);
    }

    public static FileOutputStream openOutputStream(File file, boolean append)
	    throws IOException {
	if (file.exists()) {
	    if (file.isDirectory()) {
		throw new IOException("File '" + file + "' exists but is a directory");
	    }
	    if (!file.canWrite()) {
		throw new IOException("File '" + file + "' cannot be written to");
	    }
	} else {
	    File parent = file.getParentFile();
	    if ((parent != null)
		    && (!parent.mkdirs()) && (!parent.isDirectory())) {
		throw new IOException("Directory '" + parent + "' could not be created");
	    }
            file.createNewFile();
	}

	return new FileOutputStream(file, append);
    }
    
    public static File[] searchSuffix(File dir, String suffix) {
        ArrayList<File> al = new ArrayList();
        File[] files = dir.listFiles();
        for(File f : files)
            if(f.getName().endsWith(suffix)) al.add(f);
        return al.toArray(new File[0]);
    }
}
