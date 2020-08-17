package org.jackhuang.hmcl.mod.ftb;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.mod.curse.CurseCompletionException;
import org.jackhuang.hmcl.mod.multimc.MultiMCInstanceConfiguration;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class FTBCompletionTask extends Task<Void> {
    private final List<Task<?>> dependencies = new LinkedList<>();

    private final AtomicBoolean allFetchSuccess = new AtomicBoolean(true);
    private final AtomicInteger finished = new AtomicInteger(0);

    public FTBCompletionTask(DefaultDependencyManager dependencyManager, String version, FTBManifest manifest) {
        DefaultGameRepository repository = dependencyManager.getGameRepository();

        File root = repository.getVersionRoot(version);

        Set<String> paths = manifest.versionManifest.files.stream().map(files -> files.path).collect(Collectors.toSet());
        for (String path : paths) {
            File combinedPath = combinePath(root, path);
            if (!combinedPath.exists() && !combinedPath.mkdirs()) {
                Logging.LOG.log(Level.WARNING, "Unable to create path: " + combinedPath.getAbsolutePath());
            }
        }

        for (FTBVersionManifest.Files file : manifest.versionManifest.files) {
            if (StringUtils.isNotBlank(file.name)) {
                File fileOnDisk = combinePath(combinePath(root, file.path), file.name);
                FileDownloadTask task = new FileDownloadTask(
                        NetworkUtils.toURL(file.url),
                        fileOnDisk,
                        FileDownloadTask.IntegrityCheck.of("SHA1", file.sha1)
                );
                task.setCacheRepository(dependencyManager.getCacheRepository());
                task.setCaching(true);
                dependencies.add(task.whenComplete(exception -> {
                    if (exception == null)
                        updateProgress(finished.incrementAndGet(), manifest.versionManifest.files.size());
                    else allFetchSuccess.set(false);
                }).withCounter());
            }
        }
    }

    private static File combinePath(File root, String p2) {
        return Paths.get(root.toURI()).resolve(p2).normalize().toFile();
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isRelyingOnDependencies() {
        return true;
    }

    @Override
    public void execute() throws Exception {
    }
}
