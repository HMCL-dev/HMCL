// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.authlibinjector;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public record AuthlibInjectorArtifactInfo(int buildNumber, String version, Path location) {

    public static AuthlibInjectorArtifactInfo from(Path location) throws IOException {
        try (JarFile jarFile = new JarFile(location.toFile())) {
            Attributes attributes = jarFile.getManifest().getMainAttributes();

            String title = Optional.ofNullable(attributes.getValue("Implementation-Title"))
                    .orElseThrow(() -> new IOException("Missing Implementation-Title"));
            if (!"authlib-injector".equals(title)) {
                throw new IOException("Bad Implementation-Title");
            }

            String version = Optional.ofNullable(attributes.getValue("Implementation-Version"))
                    .orElseThrow(() -> new IOException("Missing Implementation-Version"));

            int buildNumber;
            try {
                buildNumber = Optional.ofNullable(attributes.getValue("Build-Number"))
                        .map(Integer::parseInt)
                        .orElseThrow(() -> new IOException("Missing Build-Number"));
            } catch (NumberFormatException e) {
                throw new IOException("Bad Build-Number", e);
            }
            return new AuthlibInjectorArtifactInfo(buildNumber, version, location.toAbsolutePath());
        }
    }

    @Override
    public @NotNull String toString() {
        return "authlib-injector [buildNumber=" + buildNumber + ", version=" + version + "]";
    }
}
