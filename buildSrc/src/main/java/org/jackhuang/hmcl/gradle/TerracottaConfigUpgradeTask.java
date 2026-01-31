package org.jackhuang.hmcl.gradle;

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
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

public abstract class TerracottaConfigUpgradeTask extends DefaultTask {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Input
    public abstract ListProperty<@NotNull String> getClassifiers();

    @Input
    public abstract Property<@NotNull String> getVersion();

    @Input
    public abstract Property<@NotNull String> getDownloadURL();

    @InputFile
    public abstract RegularFileProperty getTemplateFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void run() throws Exception {
        JsonObject config = GSON.fromJson(
                Files.readString(getTemplateFile().get().getAsFile().toPath(), StandardCharsets.UTF_8),
                JsonObject.class
        );

        Map<String, Path> files = new LinkedHashMap<>();
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        try {
            List<CompletableFuture<HttpResponse<Path>>> tasks = new ArrayList<>();
            for (String classifier : getClassifiers().get()) {
                Path path = Files.createTempFile("terracotta-bundle-", ".tar.gz");
                String url = getDownloadURL().get().replace("${classifier}", classifier).replace("${version}", getVersion().get());
                files.put(classifier, path);

                tasks.add(client.sendAsync(
                        HttpRequest.newBuilder().GET().uri(URI.create(url)).build(),
                        HttpResponse.BodyHandlers.ofFile(path)
                ));
            }

            for (CompletableFuture<HttpResponse<Path>> task : tasks) {
                HttpResponse<Path> response = task.get();
                if (response.statusCode() != 200) {
                    throw new IOException(String.format("Unable to request %s: %d", response.uri(), response.statusCode()));
                }
            }
        } finally {
            if (client instanceof AutoCloseable) { // Since Java21, HttpClient implements AutoCloseable: https://bugs.openjdk.org/browse/JDK-8304165
                ((AutoCloseable) client).close();
            }
        }

        Map<String, Bundle> bundles = new LinkedHashMap<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        HexFormat hexFormat = HexFormat.of();

        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String classifier = entry.getKey();
            Path bundle = entry.getValue();
            Path decompressedBundle = Files.createTempFile("terracotta-bundle-", ".tar");
            try (InputStream is = new GZIPInputStream(new DigestInputStream(Files.newInputStream(bundle), digest));
                 OutputStream os = Files.newOutputStream(decompressedBundle)) {
                is.transferTo(os);
            }

            String bundleHash = hexFormat.formatHex(digest.digest());

            Map<String, String> bundleContents = new LinkedHashMap<>();
            try (TarArchiveReader reader = new TarArchiveReader(decompressedBundle)) {
                List<TarArchiveEntry> entries = new ArrayList<>(reader.getEntries());
                entries.sort(Comparator.comparing(TarArchiveEntry::getName));

                for (TarArchiveEntry archiveEntry : entries) {
                    String[] split = archiveEntry.getName().split("/", 2);
                    if (split.length != 1) {
                        throw new IllegalStateException(
                                String.format("Illegal bundle %s: files (%s) in sub directories are unsupported.", classifier, archiveEntry.getName())
                        );
                    }
                    String name = split[0];

                    try (InputStream is = new DigestInputStream(reader.getInputStream(archiveEntry), digest)) {
                        is.transferTo(OutputStream.nullOutputStream());
                    }
                    String hash = hexFormat.formatHex(digest.digest());

                    bundleContents.put(name, hash);
                }
            }

            bundles.put(classifier, new Bundle(bundleHash, bundleContents));

            Files.delete(bundle);
            Files.delete(decompressedBundle);
        }

        config.add("__comment__", new JsonPrimitive("THIS FILE IS MACHINE GENERATED! DO NOT EDIT!"));
        config.add("version_latest", new JsonPrimitive(getVersion().get()));
        config.add("packages", GSON.toJsonTree(bundles));

        Files.writeString(getOutputFile().get().getAsFile().toPath(), GSON.toJson(config), StandardCharsets.UTF_8);
    }

    public void checkValid() throws IOException {
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
                "Please run task %s to resolve the new config.", getPath()));
    }

    private record Bundle(
            @SerializedName("hash") String hash,
            @SerializedName("files") Map<String, String> files
    ) {
    }
}
