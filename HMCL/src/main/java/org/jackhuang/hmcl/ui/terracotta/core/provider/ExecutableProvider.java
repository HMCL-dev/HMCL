package org.jackhuang.hmcl.ui.terracotta.core.provider;

import javafx.beans.property.DoubleProperty;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.terracotta.core.TerracottaMetadata;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExecutableProvider implements ITerracottaProvider {
    public static final URI TARGET = Map.of(
            Platform.WINDOWS_X86_64, TerracottaMetadata.WINDOWS_X86_64,
            Platform.WINDOWS_ARM64, TerracottaMetadata.WINDOWS_ARM64,
            Platform.LINUX_X86_64, TerracottaMetadata.LINUX_X86_64,
            Platform.LINUX_ARM64, TerracottaMetadata.LINUX_ARM64
    ).get(Platform.SYSTEM_PLATFORM);

    private static final Path PATH = Metadata.DEPENDENCIES_DIRECTORY.resolve(String.format(
            "terracota/%s/%s", TerracottaMetadata.VERSION, Path.of(TARGET.getPath()).getFileName()
    )).toAbsolutePath();

    @Override
    public boolean exist() {
        return Files.exists(PATH);
    }

    @Override
    public Task<?> install(DoubleProperty progress) {
        Task<?> task = new FileDownloadTask(TARGET, PATH);
        progress.bind(task.progressProperty());
        if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
            task = task.thenRunAsync(() -> Files.setPosixFilePermissions(PATH, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE
            )));
        }
        return task;
    }

    @Override
    public List<String> launch(Path path) {
        return List.of(PATH.toString(), "--hmcl", path.toString());
    }
}
