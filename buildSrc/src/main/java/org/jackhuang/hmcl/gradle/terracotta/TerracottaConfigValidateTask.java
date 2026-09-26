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
package org.jackhuang.hmcl.gradle.terracotta;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import kala.compress.archivers.tar.TarArchiveEntry;
import kala.compress.archivers.tar.TarArchiveReader;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

@CacheableTask
public abstract class TerracottaConfigValidateTask extends AbstractTerracottaTask {

    @Input
    public abstract Property<@NotNull String> getUpgradeTaskPath();

    @TaskAction
    public void run() throws IOException {
        Path output = getOutputFile().get().getAsFile().toPath();
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
