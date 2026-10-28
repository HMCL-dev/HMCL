// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import com.google.gson.*;
import org.glavo.url.WebURL;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Downloads one HTTP(S) candidate through a local aria2 JSON-RPC server.
@NotNullByDefault
public final class Aria2Downloader {
    public static final String DEFAULT_URL = "http://127.0.0.1:6800/jsonrpc";

    private static volatile Configuration configuration = new Configuration(false, DEFAULT_URL, "");
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private Aria2Downloader() {
    }

    /// Updates the settings snapshot used by newly started downloads.
    public static void configure(boolean enabled, String url, String token) {
        configuration = new Configuration(enabled, url, token);
    }

    public static boolean isEnabled() {
        return configuration.enabled();
    }

    /// Downloads a single candidate into a dedicated, newly created directory.
    ///
    /// @param url       the candidate URL
    /// @param directory the private, empty output directory
    /// @param progress  receives completed and total byte counts
    /// @param cancelled reports cancellation of the owning task
    /// @return the verified output file within the directory
    public static Path download(WebURL url, Path directory, BiConsumer<Long, Long> progress, BooleanSupplier cancelled) throws IOException, InterruptedException {
        Configuration snapshot = configuration;
        String gid = null;
        boolean finished = false;
        Rpc rpc = new Rpc(URI.create(snapshot.url()), snapshot.token());
        Path output = directory.resolve("payload").toAbsolutePath().normalize();

        try {
            JsonArray uris = new JsonArray();
            uris.add(url.href());
            JsonObject options = new JsonObject();
            options.addProperty("dir", directory.toAbsolutePath().toString());
            options.addProperty("out", "payload");
            options.addProperty("auto-file-renaming", "false");
            options.addProperty("allow-overwrite", "false");
            options.addProperty("continue", "false");
            gid = rpc.call("aria2.addUri", uris, options).getAsString();

            while (true) {
                if (cancelled.getAsBoolean()) throw new InterruptedException();
                JsonObject status = rpc.call("aria2.tellStatus", gid).getAsJsonObject();
                String state;
                long completed;
                long total;
                try {
                    state = status.get("status").getAsString();
                    completed = Long.parseLong(status.get("completedLength").getAsString());
                    total = Long.parseLong(status.get("totalLength").getAsString());
                } catch (IllegalStateException | NullPointerException | NumberFormatException e) {
                    throw new Aria2Exception("Invalid aria2 download status", e);
                }

                progress.accept(completed, total);
                switch (state) {
                    case "complete" -> {
                        JsonArray files = rpc.call("aria2.getFiles", gid).getAsJsonArray();
                        if (files.size() != 1 || !Path.of(files.get(0).getAsJsonObject().get("path").getAsString()).toAbsolutePath().normalize().equals(output)
                                || !Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) {
                            throw new Aria2Exception("aria2 returned an unexpected output file");
                        }
                        finished = true;
                        return output;
                    }
                    case "error", "removed" ->
                            throw new Aria2Exception("aria2 download ended with status " + state + " (code "
                                    + (status.has("errorCode") ? status.get("errorCode").getAsString() : "unknown") + ")");
                    case "active", "waiting", "paused" -> Thread.sleep(500);
                    default -> throw new Aria2Exception("Unknown aria2 download status: " + state);
                }
            }
        } finally {
            if (!finished && gid != null) {
                boolean interrupted = Thread.interrupted();
                try {
                    rpc.call("aria2.forceRemove", gid);
                } catch (IOException | InterruptedException e) {
                    LOG.warning("Failed to remove aria2 download " + gid, e);
                } finally {
                    if (interrupted) Thread.currentThread().interrupt();
                }
            }
        }
    }

    /// Immutable settings used for one download.
    private record Configuration(boolean enabled, String url, String token) {
    }

    /// Sends JSON-RPC calls without exposing the secret in logs or URLs.
    private record Rpc(URI endpoint, String token) {
        /// Calls one aria2 method and validates the matching response.
        private JsonElement call(String method, Object... arguments) throws IOException, InterruptedException {
            String id = UUID.randomUUID().toString();
            JsonObject request = new JsonObject();
            request.addProperty("jsonrpc", "2.0");
            request.addProperty("id", id);
            request.addProperty("method", method);
            JsonArray params = new JsonArray();
            if (!token.isEmpty()) params.add("token:" + token);
            for (Object argument : arguments) {
                if (argument instanceof JsonElement element) params.add(element);
                else params.add(argument.toString());
            }
            request.add("params", params);

            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(request.toString(), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200)
                throw new Aria2Exception("aria2 RPC returned HTTP " + response.statusCode());

            try {
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!id.equals(result.get("id").getAsString()) || !"2.0".equals(result.get("jsonrpc").getAsString()))
                    throw new Aria2Exception("Mismatched aria2 RPC response");
                if (result.has("error")) {
                    JsonObject error = result.getAsJsonObject("error");
                    throw new Aria2Exception("aria2 RPC error " + error.get("code").getAsInt());
                }
                return result.get("result");
            } catch (JsonParseException | IllegalStateException | NullPointerException e) {
                throw new Aria2Exception("Invalid aria2 RPC response", e);
            }
        }
    }

    public static final class Aria2Exception extends IOException {
        public Aria2Exception() {
            super();
        }

        public Aria2Exception(String message) {
            super(message);
        }

        public Aria2Exception(String message, Throwable cause) {
            super(message, cause);
        }

        public Aria2Exception(Throwable cause) {
            super(cause);
        }
    }
}
