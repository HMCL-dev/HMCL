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
package org.jackhuang.hmcl.addon.repository;

import org.jackhuang.hmcl.util.MurmurHash2;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests CurseForge fingerprint calculation for local files.
@NotNullByDefault
public final class CurseForgeRemoteAddonRepositoryTest {

    /// Verifies that streaming calculation remains identical to the previous in-memory algorithm.
    @Test
    public void calculatesFingerprintWithoutChangingTheResult(@TempDir Path tempDir) throws IOException {
        byte[] boundarySample = new byte[8192 + 17];
        new Random(0).nextBytes(boundarySample);
        for (int i = 0; i < boundarySample.length; i += 97) {
            boundarySample[i] = 0x20;
        }

        byte[][] samples = {
                {},
                {0x9, 0xa, 0xd, 0x20},
                {1},
                {1, 2},
                {1, 2, 3},
                {1, 2, 3, 4},
                {1, 0x20, 2, 0xa, 3, 0xd, 4, 0x9, 5},
                boundarySample
        };

        for (int i = 0; i < samples.length; i++) {
            byte[] sample = samples[i];
            Path file = tempDir.resolve("sample-" + i);
            Files.write(file, sample);

            ByteArrayOutputStream filtered = new ByteArrayOutputStream();
            for (byte b : sample) {
                if (b != 0x9 && b != 0xa && b != 0xd && b != 0x20) {
                    filtered.write(b);
                }
            }
            long expected = Integer.toUnsignedLong(MurmurHash2.hash32(filtered.toByteArray(), filtered.size(), 1));

            assertEquals(expected, CurseForgeRemoteAddonRepository.calculateFingerprint(file));
        }
    }
}
