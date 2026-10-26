// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.tree;

import kala.compress.archivers.zip.ZipArchiveReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public final class ZipFileTreeTest {
    private static Path getTestFile(String name) {
        try {
            return Path.of(ZipFileTreeTest.class.getResource("/zip/" + name).toURI());
        } catch (URISyntaxException | NullPointerException e) {
            throw new AssertionError("Resource not found: " + name, e);
        }
    }

    @Test
    public void testClose() throws IOException {
        Path testFile = getTestFile("utf-8.zip");

        try (var channel = FileChannel.open(testFile, StandardOpenOption.READ)) {
            var reader = new ZipArchiveReader(channel);

            try (var ignored = new ZipFileTree(reader, false)) {
            }

            assertTrue(channel.isOpen());

            try (var ignored = new ZipFileTree(reader)) {
            }

            assertFalse(channel.isOpen());
        }
    }

    @Test
    public void test() throws IOException {
        Path testFile = getTestFile("utf-8.zip");

        try (var tree = new ZipFileTree(new ZipArchiveReader(testFile))) {
            var root = tree.getRoot();
            assertEquals(2, root.getFiles().size());
            assertEquals(0, root.getSubDirs().size());

            assertEquals("test.txt", root.getFiles().get("test.txt").getName());
            assertEquals("中文.txt", root.getFiles().get("中文.txt").getName());
            assertNull(root.getFiles().get("other.txt"));
        }
    }
}
