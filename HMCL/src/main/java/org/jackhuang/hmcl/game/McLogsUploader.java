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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Lang.wrap;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Uploads Minecraft game logs to mclo.gs so that a crashed game log can be shared as a link.
///
/// mclo.gs stores a log for a limited time and adds analysis on top of it. The returned URL is
/// intended to be copied or opened for troubleshooting.
///
/// @see <a href="https://mclo.gs/">mclo.gs</a>
@NotNullByDefault
public final class McLogsUploader {
    private McLogsUploader() {
    }

    /// The mclo.gs "create a log" endpoint.
    private static final String API_URL = "https://api.mclo.gs/1/log";

    /// Identifier of this application, sent alongside every uploaded log.
    private static final String SOURCE = "HMCL";

    /// Maximum uploaded line count, mirroring the server-side `limit-lines` filter.
    private static final int MAX_LINES = 25_000;

    /// Maximum uploaded size in bytes, mirroring the server-side `limit-bytes` filter (10 MiB).
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    /// Uploads the game log of a crashed game and resolves to the URL of the shared log.
    ///
    /// The log content is read from `logs/latest.log` under [runDirectory] when available, and
    /// falls back to the launcher-collected console output ([logs]) otherwise. The content is
    /// filtered for forbidden tokens and truncated on the client side to the mclo.gs limits.
    ///
    /// @param runDirectory the game run directory
    /// @param logs         the launcher-collected console output used as fallback content
    /// @return a future resolving to the mclo.gs URL of the uploaded log
    public static CompletableFuture<String> uploadGameLog(Path runDirectory, List<Log> logs) {
        return CompletableFuture.supplyAsync(wrap(() -> uploadGameLogSync(runDirectory, logs)), Schedulers.io());
    }

    private static String uploadGameLogSync(Path runDirectory, List<Log> logs) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", truncate(readGameLog(runDirectory, logs)));
        payload.addProperty("source", SOURCE);

        String responseJson = HttpRequest.POST(API_URL).json(payload).ignoreHttpCode().getString();
        return parseResponse(responseJson);
    }

    /// Reads the game log, preferring `logs/latest.log` and falling back to the console output.
    ///
    /// The returned content has every known forbidden token filtered out before it leaves the machine.
    ///
    /// @param runDirectory the game run directory
    /// @param logs         the launcher-collected console output
    /// @return the game log content
    private static String readGameLog(Path runDirectory, List<Log> logs) {
        @Nullable String content = readLatestLog(runDirectory.resolve("logs/latest.log"));
        if (content == null || content.isEmpty()) {
            content = logs.stream().map(Log::getLog).collect(Collectors.joining("\n"));
        }
        return Logger.filterForbiddenToken(content);
    }

    /// Reads the content of `latest.log`, or `null` when it is missing or unreadable.
    ///
    /// @param latestLog the path to `logs/latest.log`
    /// @return the file content, or `null` when it cannot be read
    private static @Nullable String readLatestLog(Path latestLog) {
        if (!Files.isReadable(latestLog)) {
            return null;
        }
        try {
            return FileUtils.readTextMaybeNativeEncoding(latestLog);
        } catch (IOException e) {
            LOG.warning("Failed to read the game log, falling back to the console output", e);
            return null;
        }
    }

    /// Truncates the log content to the mclo.gs limits while keeping the tail, where the crash details live.
    ///
    /// @param content the raw log content
    /// @return the truncated log content
    static String truncate(String content) {
        String[] lines = content.split("\n", -1);
        if (lines.length > MAX_LINES) {
            content = String.join("\n", Arrays.copyOfRange(lines, lines.length - MAX_LINES, lines.length));
        }

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            content = new String(bytes, bytes.length - MAX_BYTES, MAX_BYTES, StandardCharsets.UTF_8);
        }
        return content;
    }

    /// Parses the mclo.gs response into the URL of the uploaded log.
    ///
    /// @param responseJson the raw response body
    /// @return the mclo.gs URL of the uploaded log
    /// @throws IOException when the upload failed or the response is malformed
    static String parseResponse(String responseJson) throws IOException {
        @Nullable JsonObject response = JsonUtils.fromJson(responseJson, JsonObject.class);
        boolean success = response != null && JsonUtils.getBoolean(response, "success", false);
        if (!success) {
            @Nullable String error = response != null ? JsonUtils.getString(response, "error") : null;
            throw new IOException("mclo.gs upload failed" + (error != null ? ": " + error : ""));
        }

        @Nullable String url = response != null ? JsonUtils.getString(response, "url") : null;
        if (url != null) {
            return url;
        }

        // The "url" field is part of the documented response; fall back to building it from the id.
        @Nullable String id = response != null ? JsonUtils.getString(response, "id") : null;
        if (id != null) {
            return "https://mclo.gs/" + id;
        }

        throw new IOException("mclo.gs upload failed: the response contains no log URL");
    }
}
