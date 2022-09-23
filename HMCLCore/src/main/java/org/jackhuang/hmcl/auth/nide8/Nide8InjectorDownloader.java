package org.jackhuang.hmcl.auth.nide8;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.FileDownloadTask;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public class Nide8InjectorDownloader implements Nide8InjectorArtifactProvider {

    private static final String LATEST_BUILD_URL = "https://login.mc-user.com:233/download/nide8auth.jar";

    private final Path artifactLocation;
    private final Supplier<DownloadProvider> downloadProvider;

    /**
     * @param artifactsDirectory where to save authlib-injector artifacts
     */
    public Nide8InjectorDownloader(Path artifactLocation, Supplier<DownloadProvider> downloadProvider) {
        this.artifactLocation = artifactLocation;
        this.downloadProvider = downloadProvider;
    }

    @Override
    public Nide8InjectorArtifactInfo getArtifactInfo() throws IOException {
        Optional<Nide8InjectorArtifactInfo> cached = getArtifactInfoImmediately();
        if (cached.isPresent()) {
            return cached.get();
        }

        synchronized (this) {
            Optional<Nide8InjectorArtifactInfo> local = getLocalArtifact();
            if (local.isPresent()) {
                return local.get();
            }
            LOG.info("No local nide8auth found, downloading");
            updateChecked.set(true);
            update();
            local = getLocalArtifact();
            return local.orElseThrow(() -> new IOException("The downloaded nide8auth cannot be recognized"));
        }
    }

    @Override
    public Optional<Nide8InjectorArtifactInfo> getArtifactInfoImmediately() {
        return getLocalArtifact();
    }

    private final AtomicBoolean updateChecked = new AtomicBoolean(false);

    public void checkUpdate() throws IOException {
        // this method runs only once
        if (updateChecked.compareAndSet(false, true)) {
            synchronized (this) {
                LOG.info("Checking nide8auth");
                update();
            }
        }
    }

    private void update() throws IOException {
        Optional<Nide8InjectorArtifactInfo> local = getLocalArtifact();
        if (local.isPresent()) {
            return;
        }

        try {
            new FileDownloadTask(new URL(downloadProvider.get().injectURL(LATEST_BUILD_URL)), artifactLocation.toFile()).run();
        } catch (Exception e) {
            throw new IOException("Failed to download nide8auth.jar", e);
        }

        LOG.info("Download nide8auth.jar");
    }

    private Optional<Nide8InjectorArtifactInfo> getLocalArtifact() {
        return parseArtifact(artifactLocation);
    }

    protected static Optional<Nide8InjectorArtifactInfo> parseArtifact(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Nide8InjectorArtifactInfo.from(path));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Bad nide8auth artifact", e);
            return Optional.empty();
        }
    }

}
