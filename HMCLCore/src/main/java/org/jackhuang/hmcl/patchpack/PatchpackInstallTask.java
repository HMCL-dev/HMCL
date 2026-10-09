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
package org.jackhuang.hmcl.patchpack;

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@NotNullByDefault
public final class PatchpackInstallTask extends Task<Void> {

    private final Path zipFile;
    private final Charset charset;
    private final PatchpackInfo info;
    private final Path destination;

    public PatchpackInstallTask(Path zipFile, Charset charset, PatchpackInfo info, Path destination) {
        this.zipFile = zipFile;
        this.charset = charset;
        this.info = info;
        this.destination = destination;

        setStage("hmcl.patchpack");
    }

    @Override
    public void execute() throws Exception {
        applyDiff();
        extract();
    }

    private void applyDiff() throws IOException {
        PatchpackInfo.Diff diff = info.diff();
        if (diff == null)
            return;

        List<Path> deleteTargets = new ArrayList<>();
        if (diff.delete() != null) {
            for (String path : diff.delete()) {
                deleteTargets.add(resolveInside(path));
            }
        }

        deleteTargets.sort(Comparator.comparingInt(Path::getNameCount).reversed());
        for (Path path : deleteTargets) {
            if (Files.isDirectory(path)) {
                FileUtils.deleteDirectory(path);
            } else {
                Files.deleteIfExists(path);
            }
        }

        if (diff.rename() != null) {
            for (PatchpackInfo.Diff.Rename rename : diff.rename()) {
                Path from = resolveInside(rename.from());
                if (!Files.exists(from))
                    continue;

                Path to = resolveInside(rename.to());
                @Nullable Path parent = to.getParent();
                if (parent != null)
                    Files.createDirectories(parent);

                if (Files.isDirectory(to)) {
                    FileUtils.deleteDirectory(to);
                } else {
                    Files.deleteIfExists(to);
                }

                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void extract() throws IOException {
        Files.createDirectories(destination);

        new Unzipper(zipFile, destination)
                .setReplaceExistentFile(true)
                .setEncoding(charset)
                .setFilter((entry, destFile, relativePath) -> !PatchpackInfo.FILE_NAME.equals(relativePath))
                .unzip();
    }

    private Path resolveInside(String path) throws IOException {
        Path base = destination.toAbsolutePath().normalize();
        Path resolved = base.resolve(path).toAbsolutePath().normalize();
        if (resolved.equals(base) || !resolved.startsWith(base))
            throw new IOException("Patch pack is trying to access a path outside of the instance directory: " + path);
        return resolved;
    }
}
