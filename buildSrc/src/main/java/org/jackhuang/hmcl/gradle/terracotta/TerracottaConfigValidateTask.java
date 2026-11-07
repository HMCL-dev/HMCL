// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.terracotta;

import com.google.gson.JsonObject;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public abstract class TerracottaConfigValidateTask extends AbstractTerracottaTask {

    @Internal
    public abstract Property<@NotNull String> getUpgradeTaskPath();

    @InputFile
    public abstract RegularFileProperty getConfigFile();

    @TaskAction
    public void run() throws IOException {
        Path output = getConfigFile().get().getAsFile().toPath();
        if (Files.isReadable(output)) {
            String version = GSON.fromJson(Files.readString(output, StandardCharsets.UTF_8), JsonObject.class)
                    .get("version_latest").getAsJsonPrimitive().getAsString();
            if (Objects.equals(version, getVersion().get())) {
                return;
            }
        }

        throw new GradleException(String.format("Terracotta config isn't up-to-date! " +
                "You might have just edited the version number in libs.version.toml. " +
                "Please run task %s to resolve the new config.", getUpgradeTaskPath().get()));
    }
}
