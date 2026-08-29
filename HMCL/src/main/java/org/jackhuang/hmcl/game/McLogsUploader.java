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
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /// Placeholder replacing IPv4 addresses before upload.
    private static final String IPV4_REDACTED = "<IPv4>";

    /// Placeholder replacing IPv6 addresses before upload.
    private static final String IPV6_REDACTED = "<IPv6>";

    /// Placeholder replacing the current user's home directory before upload.
    private static final String HOME_REDACTED = "<user home>";

    /// Placeholder replacing a credential value after redaction.
    private static final String TOKEN_REDACTED = "<access token>";

    /// Matches IPv4 addresses whose octets are all within 0-255, so unrelated dotted numbers are not touched.
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}\\b");

    /// Matches IPv6 addresses in their full or `::`-compressed form, without touching MAC addresses or other
    /// colon-separated hex sequences of the wrong length.
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "(?i)(?<![0-9a-f:])(?:"
                    + "(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}"
                    + "|(?:[0-9a-f]{1,4}:){1,7}:"
                    + "|(?:[0-9a-f]{1,4}:){1,6}:[0-9a-f]{1,4}"
                    + "|(?:[0-9a-f]{1,4}:){1,5}(?::[0-9a-f]{1,4}){1,2}"
                    + "|(?:[0-9a-f]{1,4}:){1,4}(?::[0-9a-f]{1,4}){1,3}"
                    + "|(?:[0-9a-f]{1,4}:){1,3}(?::[0-9a-f]{1,4}){1,4}"
                    + "|(?:[0-9a-f]{1,4}:){1,2}(?::[0-9a-f]{1,4}){1,5}"
                    + "|[0-9a-f]{1,4}:(?::[0-9a-f]{1,4}){1,6}"
                    + "|:(?:(?::[0-9a-f]{1,4}){1,7}|:)"
                    + ")(?![0-9a-f:])");

    /// Matches `name=<value>`, `name: <value>` and `"name": "<value>"` credentials so only the value is masked.
    private static final Pattern TOKEN_PROPERTY_PATTERN = Pattern.compile(
            "(?i)(access[_\\-]?token|refresh[_\\-]?token|client[_\\-]?token|session[_\\-]?token|auth[_\\-]?token)"
                    + "(\"?\\s*[:=]\\s*[\"']?)([^\\s\"';]+)");

    /// Matches `--name <value>` command-line credentials so only the value is masked.
    private static final Pattern TOKEN_ARGUMENT_PATTERN = Pattern.compile(
            "(?i)(--(?:access[_\\-]?token|refresh[_\\-]?token|client[_\\-]?token|session[_\\-]?token|auth[_\\-]?token)"
                    + "\\s+)([^\\s\"']+)");

    /// Uploads the game log of a crashed game and resolves to the URL of the shared log.
    ///
    /// The log content is read from `logs/latest.log` under [runDirectory] when available, and
    /// falls back to the launcher-collected console output ([logs]) otherwise. The content is
    /// redacted for potentially sensitive data and truncated on the client side to the mclo.gs limits.
    ///
    /// @param runDirectory the game run directory
    /// @param logs         the launcher-collected console output used as fallback content
    /// @return a future resolving to the mclo.gs URL of the uploaded log
    public static CompletableFuture<String> uploadGameLog(Path runDirectory, List<Log> logs) {
        return CompletableFuture.supplyAsync(wrap(() -> uploadGameLogSync(runDirectory, logs)), Schedulers.io());
    }

    private static String uploadGameLogSync(Path runDirectory, List<Log> logs) throws IOException {
        String content = truncate(sanitize(readGameLog(runDirectory, logs)));

        JsonObject payload = new JsonObject();
        payload.addProperty("content", content);
        payload.addProperty("source", SOURCE);

        String responseJson = HttpRequest.POST(API_URL).json(payload).ignoreHttpCode().getString();
        return parseResponse(responseJson);
    }

    /// Reads the game log, preferring `logs/latest.log` and falling back to the console output.
    ///
    /// @param runDirectory the game run directory
    /// @param logs         the launcher-collected console output
    /// @return the game log content
    private static String readGameLog(Path runDirectory, List<Log> logs) {
        @Nullable String content = readLatestLog(runDirectory.resolve("logs/latest.log"));
        if (content == null || content.isEmpty()) {
            content = logs.stream().map(Log::getLog).collect(Collectors.joining("\n"));
        }
        return content;
    }

    /// Reads the tail of `latest.log`, or `null` when it is missing, empty, or unreadable.
    ///
    /// @param latestLog the path to `logs/latest.log`
    /// @return the tail of the file, or `null` when it cannot be read
    private static @Nullable String readLatestLog(Path latestLog) {
        if (!Files.isRegularFile(latestLog) || !Files.isReadable(latestLog)) {
            return null;
        }
        try {
            return FileUtils.readTextTailMaybeNativeEncoding(latestLog, MAX_BYTES);
        } catch (IOException e) {
            LOG.warning("Failed to read the game log, falling back to the console output", e);
            return null;
        }
    }

    /// Truncates the log content to the mclo.gs limits while keeping the tail, where the crash details live.
    ///
    /// The line limit is applied first (keeping the last [MAX_LINES] lines), then the byte limit, so the
    /// result always fits both limits and never splits a multi-byte character.
    ///
    /// @param content the raw log content
    /// @return the truncated log content
    static String truncate(String content) {
        return FileUtils.truncateUtf8ToByteLimit(limitLines(content), MAX_BYTES);
    }

    /// Keeps at most the last [MAX_LINES] lines, scanning backwards for newlines instead of splitting the
    /// whole content into per-line strings.
    ///
    /// @param content the log content
    /// @return the content limited to the last [MAX_LINES] lines
    private static String limitLines(String content) {
        // A trailing newline terminates the last line rather than adding an empty one, so skip it before
        // counting; otherwise exactly MAX_LINES lines ending in `\n` would lose their first line.
        int fromIndex = content.length();
        while (fromIndex > 0 && content.charAt(fromIndex - 1) == '\n') {
            fromIndex--;
        }

        // Walk back across MAX_LINES line terminators; fromIndex ends at the oldest newline to cut before,
        // and the loop returns early (keeping everything) when there are fewer lines than the limit.
        int remaining = MAX_LINES;
        while (remaining-- > 0) {
            int index = content.lastIndexOf('\n', fromIndex - 1);
            if (index < 0) {
                return content;
            }
            fromIndex = index;
        }
        return content.substring(fromIndex + 1);
    }

    /// Redacts potentially sensitive information before the log leaves the machine for a third-party service.
    ///
    /// This masks credential arguments, the user's home directory and IP addresses, then lets
    /// {@link Logger#filterForbiddenToken(String)} replace any registered access token occurrences.
    ///
    /// @param content the raw log content
    /// @return the redacted log content
    static String sanitize(String content) {
        return Logger.filterForbiddenToken(redactSensitiveData(content));
    }

    /// Applies the local redaction rules that do not depend on registered tokens.
    ///
    /// @param content the raw log content
    /// @return the redacted log content
    private static String redactSensitiveData(String content) {
        content = redactUserHome(content);
        content = redactTokens(content);
        content = redactIpv4(content);
        return redactIpv6(content);
    }

    /// Replaces the current user's home directory (in either slash style) with a placeholder.
    ///
    /// Windows paths are compared case-insensitively, while Unix and macOS paths are compared
    /// case-sensitively, so a sibling directory differing only in letter case is never redacted.
    ///
    /// @param content the log content
    /// @return the content with the home directory redacted
    private static String redactUserHome(String content) {
        String home = System.getProperty("user.home", "");
        if (home.length() < 3) {
            return content;
        }

        boolean caseInsensitive = OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS;
        content = redactPathVariant(content, home, caseInsensitive);
        String alternate = home.indexOf('\\') >= 0 ? home.replace('\\', '/') : home.replace('/', '\\');
        if (!alternate.equals(home)) {
            content = redactPathVariant(content, alternate, caseInsensitive);
        }
        return content;
    }

    /// Replaces [path] with a placeholder only when it is followed by a path separator, whitespace, quote or
    /// the end of the content, so a home directory is not matched as the prefix of a sibling directory.
    ///
    /// @param content         the log content
    /// @param path            the path variant to redact
    /// @param caseInsensitive whether the comparison should ignore letter case (Windows paths)
    /// @return the content with occurrences of [path] redacted
    static String redactPathVariant(String content, String path, boolean caseInsensitive) {
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        Pattern pattern = Pattern.compile(Pattern.quote(path) + "(?=[/\\\\\\s\"']|\\z)", flags);
        return pattern.matcher(content).replaceAll(Matcher.quoteReplacement(HOME_REDACTED));
    }

    /// Masks values of known credential arguments without touching unrelated content.
    ///
    /// @param content the log content
    /// @return the content with credential values masked
    private static String redactTokens(String content) {
        content = TOKEN_PROPERTY_PATTERN.matcher(content).replaceAll("$1$2" + TOKEN_REDACTED);
        content = TOKEN_ARGUMENT_PATTERN.matcher(content).replaceAll("$1" + TOKEN_REDACTED);
        return content;
    }

    /// Masks IPv4 addresses, keeping any trailing port for diagnostics.
    ///
    /// @param content the log content
    /// @return the content with IPv4 addresses redacted
    private static String redactIpv4(String content) {
        return IPV4_PATTERN.matcher(content).replaceAll(Matcher.quoteReplacement(IPV4_REDACTED));
    }

    /// Masks IPv6 addresses, keeping any trailing port for diagnostics.
    ///
    /// @param content the log content
    /// @return the content with IPv6 addresses redacted
    private static String redactIpv6(String content) {
        return IPV6_PATTERN.matcher(content).replaceAll(Matcher.quoteReplacement(IPV6_REDACTED));
    }

    /// Parses the mclo.gs response into the URL of the uploaded log.
    ///
    /// @param responseJson the raw response body
    /// @return the mclo.gs URL of the uploaded log
    /// @throws IOException when the upload failed or the response is malformed
    static String parseResponse(String responseJson) throws IOException {
        @Nullable JsonObject response = JsonUtils.fromMaybeMalformedJson(responseJson, JsonObject.class);
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
