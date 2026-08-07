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
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.jackhuang.hmcl.game.GameComponentType.*;

/// Resolves the library classpath used for one launch attempt.
@NotNullByDefault
public final class LaunchClasspathResolver {
    /// Prevents construction of this utility class.
    private LaunchClasspathResolver() {
    }

    /// Returns a mutable classpath containing installed libraries selected for this launch.
    ///
    /// For Forge or LiteLoader installations containing OptiFine, an installed OptiFine installer
    /// artifact replaces the ordinary artifact. With ModLauncher, the installer is omitted from the
    /// ordinary classpath because transformer discovery loads it separately. The incompatible
    /// `launchwrapper-of` artifact is also omitted.
    ///
    /// @param repository the repository that owns the installed libraries
    /// @param manifest   the effective launch manifest
    /// @return a mutable insertion-ordered set of absolute classpath entries
    public static Set<String> resolve(
            GameRepository repository,
            GameInstanceManifest manifest) {
        Set<String> classpath = new LinkedHashSet<>(repository.getClasspath(manifest));
        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(manifest, null);
        if (!analyzer.has(OPTIFINE) || (!analyzer.has(LITELOADER) && !analyzer.has(FORGE))) {
            return classpath;
        }

        boolean removeFromClasspath = GameComponentAnalyzer.MOD_LAUNCHER_MAIN.equals(manifest.mainClass());
        @Nullable Path selectedInstallerFile = null;

        for (Library library : manifest.getLibraries()) {
            Path libraryFile = repository.getLayout().getLibraryFile(manifest.id(), library);
            if (library.is("optifine", "OptiFine")) {
                Library installer = new Library(
                        new Artifact("optifine", "OptiFine", library.version(), "installer"));
                Path installerFile = repository.getLayout().getLibraryFile(manifest.id(), installer);
                if (Files.exists(installerFile)) {
                    classpath.remove(FileUtils.getAbsolutePath(libraryFile));
                    selectedInstallerFile = installerFile;
                }
            } else if (library.is("optifine", "launchwrapper-of")) {
                classpath.remove(FileUtils.getAbsolutePath(libraryFile));
            }
        }

        if (!removeFromClasspath
                && selectedInstallerFile != null
                && Files.isRegularFile(selectedInstallerFile)) {
            classpath.add(FileUtils.getAbsolutePath(selectedInstallerFile));
        }
        return classpath;
    }
}
