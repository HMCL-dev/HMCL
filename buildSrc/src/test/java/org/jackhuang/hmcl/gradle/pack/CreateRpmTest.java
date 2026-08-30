/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.gradle.pack;

import org.eclipse.packager.rpm.RpmTag;
import org.eclipse.packager.rpm.deps.RpmDependencyFlags;
import org.eclipse.packager.rpm.parse.InputHeader;
import org.eclipse.packager.rpm.parse.RpmInputStream;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the metadata and reproducibility of RPM packages built without external tools.
@NotNullByDefault
public final class CreateRpmTest {
    /// Stable timestamp used to make two independently built packages comparable.
    private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

    /// Temporary directory containing test inputs and generated RPM packages.
    @TempDir
    public Path tempDir;

    /// Builds two packages and verifies their RPM metadata, scripts, dependencies, and file modes.
    @Test
    public void shouldBuildReproducibleRpmWithExpectedMetadata() throws IOException {
        Path appFile = tempDir.resolve("HMCL-test.sh");
        Files.writeString(appFile, "#!/usr/bin/env bash\necho HMCL\n");
        Path iconFile = tempDir.resolve("hmcl.png");
        Files.write(iconFile, new byte[]{1, 2, 3, 4});

        Path first = tempDir.resolve("first.rpm");
        Path second = tempDir.resolve("second.rpm");
        CreateRpm.buildRpm(appFile, iconFile, first, "3.17.SNAPSHOT", ReleaseType.NIGHTLY,
                "org.jackhuang.hmcl.Launcher", BUILD_TIME);
        CreateRpm.buildRpm(appFile, iconFile, second, "3.17.SNAPSHOT", ReleaseType.NIGHTLY,
                "org.jackhuang.hmcl.Launcher", BUILD_TIME);

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        try (RpmInputStream input = new RpmInputStream(new BufferedInputStream(Files.newInputStream(first)))) {
            InputHeader<RpmTag> header = input.getPayloadHeader();
            assertEquals("hmcl-nightly", header.getTag(RpmTag.NAME));
            assertEquals("3.17.snapshot", header.getTag(RpmTag.VERSION));
            assertEquals("1", header.getTag(RpmTag.RELEASE));
            assertEquals("noarch", header.getTag(RpmTag.ARCH));
            assertEquals("GPL-3.0-or-later", header.getTag(RpmTag.LICENSE));
            assertEquals("https://github.com/HMCL-dev/HMCL", header.getTag(RpmTag.URL));
            assertEquals("hmcl-nightly-3.17.snapshot-1.src.rpm", header.getTag(RpmTag.SOURCE_PACKAGE));
            assertEquals((int) BUILD_TIME.getEpochSecond(), header.getTag(RpmTag.BUILDTIME));
            assertEquals("/bin/sh", header.getTag(RpmTag.POSTINSTALL_SCRIPT_PROG));
            assertEquals("/bin/sh", header.getTag(RpmTag.PREREMOVE_SCRIPT_PROG));
            assertTrue(((String) header.getTag(RpmTag.POSTINSTALL_SCRIPT)).contains("alternatives --install"));
            assertTrue(((String) header.getTag(RpmTag.PREREMOVE_SCRIPT)).contains("alternatives --remove"));

            assertDependency(header, "/usr/sbin/alternatives", RpmDependencyFlags.SCRIPT_POST);
            assertDependency(header, "/usr/sbin/alternatives", RpmDependencyFlags.SCRIPT_PREUN);
            assertDependency(header, "rpmlib(FileDigests)", RpmDependencyFlags.RPMLIB);
            assertFileMetadata(header);
        }
    }

    /// Verifies that one dependency carries the expected RPM flag.
    private static void assertDependency(InputHeader<RpmTag> header, String expectedName,
                                         RpmDependencyFlags expectedFlag) {
        String @Unmodifiable [] names = (String[]) header.getTag(RpmTag.REQUIRE_NAME);
        Integer @Unmodifiable [] flags = (Integer[]) header.getTag(RpmTag.REQUIRE_FLAGS);
        int expectedMask = RpmDependencyFlags.encode(EnumSet.of(expectedFlag));

        for (int i = 0; i < names.length; i++) {
            if (expectedName.equals(names[i]) && (flags[i] & expectedMask) == expectedMask) {
                return;
            }
        }
        throw new AssertionError("Missing dependency: " + expectedName + " with " + expectedFlag);
    }

    /// Verifies paths, modes, owners, and groups for all packaged files and directories.
    private static void assertFileMetadata(InputHeader<RpmTag> header) {
        String @Unmodifiable [] basenames = (String[]) header.getTag(RpmTag.BASENAMES);
        String @Unmodifiable [] dirnames = (String[]) header.getTag(RpmTag.DIRNAMES);
        Integer @Unmodifiable [] dirIndexes = (Integer[]) header.getTag(RpmTag.DIR_INDEXES);
        Short @Unmodifiable [] modes = (Short[]) header.getTag(RpmTag.FILE_MODES);
        String @Unmodifiable [] users = (String[]) header.getTag(RpmTag.FILE_USERNAME);
        String @Unmodifiable [] groups = (String[]) header.getTag(RpmTag.FILE_GROUPNAME);

        assertFile(basenames, dirnames, dirIndexes, modes, users, groups, "/usr/bin/hmcl-nightly", 0100755);
        assertFile(basenames, dirnames, dirIndexes, modes, users, groups,
                "/usr/share/applications/hmcl-nightly.desktop", 0100644);
        assertFile(basenames, dirnames, dirIndexes, modes, users, groups,
                "/usr/share/icons/hicolor/256x256/apps/hmcl-nightly.png", 0100644);
        assertFile(basenames, dirnames, dirIndexes, modes, users, groups, "/usr/share/java/hmcl", 040755);
        assertFile(basenames, dirnames, dirIndexes, modes, users, groups,
                "/usr/share/java/hmcl/HMCL-test.sh", 0100755);
    }

    /// Verifies one packaged path and its mode, owner, and group.
    private static void assertFile(String[] basenames, String[] dirnames, Integer[] dirIndexes,
                                   Short[] modes, String[] users, String[] groups,
                                   String expectedPath, int expectedMode) {
        for (int i = 0; i < basenames.length; i++) {
            String path = dirnames[dirIndexes[i]] + basenames[i];
            if (expectedPath.equals(path)) {
                assertEquals(expectedMode, modes[i] & 0xffff);
                assertEquals("root", users[i]);
                assertEquals("root", groups[i]);
                return;
            }
        }
        throw new AssertionError("Missing packaged path: " + expectedPath);
    }
}
