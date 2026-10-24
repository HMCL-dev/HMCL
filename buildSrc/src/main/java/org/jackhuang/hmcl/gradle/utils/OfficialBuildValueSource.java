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
package org.jackhuang.hmcl.gradle.utils;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.jetbrains.annotations.NotNullByDefault;

/// Supplies the signing-key verification result without retaining key contents in task inputs.
@NotNullByDefault
public abstract class OfficialBuildValueSource implements ValueSource<Boolean, OfficialBuildValueSource.Parameters> {
    /// Declares the locations and fingerprint needed to verify official signing credentials.
    @NotNullByDefault
    public interface Parameters extends ValueSourceParameters {
        /// Returns the public-key resource to verify.
        RegularFileProperty getPublicKeyFile();

        /// Returns the optional PKCS#8 private-key location.
        Property<String> getPrivateKeyLocation();

        /// Returns the pinned lowercase public-key SHA-256 fingerprint.
        Property<String> getExpectedSha256();
    }

    /// Reads the current key files and returns only their official-build verification result.
    @Override
    public Boolean obtain() {
        Parameters parameters = getParameters();
        return OfficialBuildVerifier.isOfficialBuild(
                parameters.getPublicKeyFile().get().getAsFile().toPath(),
                parameters.getPrivateKeyLocation().getOrNull(),
                parameters.getExpectedSha256().get());
    }
}
