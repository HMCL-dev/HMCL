// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.java;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.platform.Platform;

import java.nio.file.Path;
import java.util.Collection;

/**
 * @author Glavo
 */
public interface JavaRepository {

    Path getJavaDir(Platform platform, String name);

    Path getManifestFile(Platform platform, String name);

    Collection<Path> getAllJava(Platform platform);

    Task<JavaRuntime> getDownloadJavaTask(DownloadProvider downloadProvider, Platform platform, GameJavaVersion gameJavaVersion);

    Task<Void> getUninstallJavaTask(Platform platform, String name);

    Task<Void> getUninstallJavaTask(JavaRuntime java);
}
