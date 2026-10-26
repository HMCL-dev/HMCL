// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.authlibinjector;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class SimpleAuthlibInjectorArtifactProvider implements AuthlibInjectorArtifactProvider {

    private Path location;

    public SimpleAuthlibInjectorArtifactProvider(Path location) {
        this.location = location;
    }

    @Override
    public AuthlibInjectorArtifactInfo getArtifactInfo() throws IOException {
        return AuthlibInjectorArtifactInfo.from(location);
    }

    @Override
    public Optional<AuthlibInjectorArtifactInfo> getArtifactInfoImmediately() {
        try {
            return Optional.of(getArtifactInfo());
        } catch (IOException e) {
            LOG.warning("Bad authlib-injector artifact", e);
            return Optional.empty();
        }
    }
}
