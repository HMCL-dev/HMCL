package org.jackhuang.hmcl.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ModFileLister {

    public static void listFiles(Path dir, List<String> stringList) {
        listFilesHelper(dir, "|", stringList);
    }

    private static void listFilesHelper(Path dir, String indent, List<String> stringList) {
        if (Files.isDirectory(dir)) {
            File[] files = dir.toFile().listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        stringList.add(indent + "=> " + file.getName());
                        listFilesHelper(file.toPath(), indent + " | ", stringList);
                    } else {
                        stringList.add(indent + "-> " + file.getName());
                        if (file.getName().toLowerCase().endsWith(".zip")) {
                            listZipContents(file, indent + " | ", stringList);
                        }
                    }
                }
            }
        }
    }

    private static void listZipContents(File zipFile, String indent, List<String> stringList) {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath()), StandardCharsets.UTF_8)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    stringList.add(indent + "-> " + zipEntry.getName());
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        } catch (IOException e) {
            LOG.error("Failed to list zip contents", e);
        }
    }

    private static void listAllFiles(File dir, List<File> fileList) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        listAllFiles(file, fileList);
                    } else {
                        fileList.add(file);
                    }
                }
            }
        }
    }
}
