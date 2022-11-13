/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.io;

import org.jackhuang.hmcl.download.ArtifactMalformedException;
import org.jackhuang.hmcl.util.DigestUtils;

import java.io.IOException;
import java.nio.file.Path;

public class ChecksumMismatchException extends ArtifactMalformedException {

    private final String algorithm;
    private final String expectedChecksum;
    private final String actualChecksum;

    public ChecksumMismatchException(String algorithm, String expectedChecksum, String actualChecksum) {
        super("Incorrect checksum (" + algorithm + "), expected: " + expectedChecksum + ", actual: " + actualChecksum);
        this.algorithm = algorithm;
        this.expectedChecksum = expectedChecksum;
        this.actualChecksum = actualChecksum;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getExpectedChecksum() {
        return expectedChecksum;
    }

    public String getActualChecksum() {
        return actualChecksum;
    }

    public static void verifyChecksum(Path file, String algorithm, String expectedChecksum) throws IOException {
        String checksum = DigestUtils.digestToString(algorithm, file);
        if (!checksum.equalsIgnoreCase(expectedChecksum)) {
            throw new ChecksumMismatchException(algorithm, expectedChecksum, checksum);
        }
    }
}
