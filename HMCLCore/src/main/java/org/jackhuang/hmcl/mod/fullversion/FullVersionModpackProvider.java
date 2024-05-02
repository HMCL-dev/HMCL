package org.jackhuang.hmcl.mod.fullversion;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.mod.MismatchedModpackTypeException;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackInstallTask;
import org.jackhuang.hmcl.mod.ModpackProvider;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class FullVersionModpackProvider implements ModpackProvider {
    public static final FullVersionModpackProvider INSTANCE = new FullVersionModpackProvider();

    private FullVersionModpackProvider() {
    }

    @Override
    public String getName() {
        return "FullVersion";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        return Task.completed(null);
    }

    @Override
    public Task<?> createUpdateTask(DefaultDependencyManager dependencyManager, String name, File zipFile, Modpack modpack) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof FullVersionModpackManifest))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        return Task.completed(null);
    }

    @Override
    public Modpack readManifest(FileSystem fileSystem, Path file, Charset encoding) throws IOException, JsonParseException {
        Path root = fileSystem.getPath("/");
        String versionName = getVersionName(root);

        if (versionName != null) {
            Path candidate = null;

            try (Stream<Path> paths = Files.list(root)) {
                for (Path path : Lang.toIterable(paths)) {
                    if (Files.isDirectory(path)) {
                        if (candidate != null) {
                            // There are two folders.
                            throw new IOException("Not a full version modpack");
                        }
                        candidate = path;
                    } else {
                        if (!FileUtils.getExtension(path).equals("txt")) {
                            // There are human unreadable files by human.
                            throw new IOException("Not a full version modpack");
                        }
                    }
                }
            }

            if (candidate != null) {
                root = candidate;
                versionName = getVersionName(candidate);
            }
        }

        String r = FileUtils.getName(root);
        String vn = versionName;
        return new Modpack() {
            @Override
            public Task<?> getInstallTask(DefaultDependencyManager dependencyManager, File zipFile, String name) {
                return new ModpackInstallTask<Void>(
                        file.toFile(),
                        dependencyManager.getGameRepository().getRunDirectory(vn),
                        encoding,
                        Collections.singletonList("/" + r),
                        s -> true,
                        null
                );
            }
        };
    }

    private String getVersionName(Path root) throws IOException {
        // Each compound is a boolean[2], which stands for whether xxx.jar / .json exists.
        Map<String, boolean[]> compounds = new HashMap<>();

        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : Lang.toIterable(paths)) {
                int currentI = 1;
                switch (FileUtils.getExtension(path)) {
                    case "jar": {
                        currentI = 0;
                        // fallthrough
                    }
                    case "json": {
                        // currentI: 0 -> JAR, 1 -> JSON
                        String versionName = FileUtils.getNameWithoutExtension(path);

                        boolean[] current = compounds.computeIfAbsent(versionName, _v -> new boolean[2]);
                        current[currentI] = true;

                        if (current[1 - currentI]) { // Check whether another one exists.
                            return versionName;
                        }
                        break;
                    }
                    default: {
                    }
                }
            }
        }

        return null;
    }
}
