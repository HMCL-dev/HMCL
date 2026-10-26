// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.authlibinjector;

import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class AuthlibInjectorExtractor implements AuthlibInjectorArtifactProvider {
    private final URL source;
    private final Path artifactLocation;

    public AuthlibInjectorExtractor(URL source, Path artifactLocation) {
        if (source == null)
            throw new IllegalArgumentException("Missing authlib injector");
        this.source = source;
        this.artifactLocation = artifactLocation;
    }

    @Override
    public AuthlibInjectorArtifactInfo getArtifactInfo() throws IOException {
        Optional<AuthlibInjectorArtifactInfo> cached = getArtifactInfoImmediately();
        if (cached.isPresent())
            return cached.get();

        synchronized (this) {
            cached = getArtifactInfoImmediately();
            if (cached.isPresent())
                return cached.get();

            LOG.info("No local authlib-injector found, extracting");
            Files.createDirectories(artifactLocation.getParent());
            try (InputStream inputStream = source.openStream()) {
                FileUtils.saveSafely(artifactLocation, inputStream::transferTo);
            }
            return getArtifactInfoImmediately().orElseThrow(() ->
                    new IOException("Failed to extract authlib-injector artifact"));
        }
    }

    @Override
    public Optional<AuthlibInjectorArtifactInfo> getArtifactInfoImmediately() {
        if (!Files.isRegularFile(artifactLocation))
            return Optional.empty();

        try {
            return Optional.of(AuthlibInjectorArtifactInfo.from(artifactLocation));
        } catch (IOException e) {
            LOG.warning("Bad authlib-injector artifact", e);
            return Optional.empty();
        }
    }
}
