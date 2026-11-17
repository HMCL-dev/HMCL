// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.java;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.platform.NativePlatform;

import java.nio.file.Path;
import java.util.Collection;

/**
 * @author Glavo
 */
public interface JavaRepository {

    Path getJavaDir(NativePlatform platform, String name);

    Path getManifestFile(NativePlatform platform, String name);

    Collection<Path> getAllJava(NativePlatform platform);

    Task<JavaRuntime> getDownloadJavaTask(DownloadProvider downloadProvider, NativePlatform platform, GameJavaVersion gameJavaVersion);

    Task<Void> getUninstallJavaTask(NativePlatform platform, String name);

    Task<Void> getUninstallJavaTask(JavaRuntime java);
}
