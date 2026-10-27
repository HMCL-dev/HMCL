// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.terracotta;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractTerracottaTask extends DefaultTask {

    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Input
    public abstract Property<@NotNull String> getVersion();

}
