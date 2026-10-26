// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
