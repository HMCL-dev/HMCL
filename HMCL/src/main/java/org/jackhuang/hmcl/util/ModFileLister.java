package org.jackhuang.hmcl.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ModFileLister {

    public static void listFiles(Path dir, List<String> stringList) throws IOException {
        listFilesHelper(dir, "|", stringList);
    }

    private static void listFilesHelper(Path dir, String indent, List<String> stringList) throws IOException {
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir);) {
                files.forEach(file -> {
                    if (Files.isDirectory(file)) {
                        stringList.add(indent + "=> " + file.getFileName());
                        try {
                            listFilesHelper(file, indent + " | ", stringList);
                        } catch (IOException e) {
                            LOG.error("Failed to list files in " + file, e);
                        }
                    } else {
                        stringList.add(indent + "-> " + file.getFileName());
                   }
               });
           }
        }
    }

    private static void listZipContents(Path zipFile, String indent, List<String> stringList) {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
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

    public static void listAllFiles(Path dir, List<Path> fileList) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(fileList::add);
        }
    }
}
