package org.jackhuang.hmcl.terracotta.provider;

import javafx.beans.property.DoubleProperty;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.TerracottaNative;
import org.jackhuang.hmcl.terracotta.TerracottaMetadata;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeneralProvider implements ITerracottaProvider {
    private final TerracottaNative target = Map.of(
            Platform.WINDOWS_X86_64, TerracottaMetadata.WINDOWS_X86_64,
            Platform.WINDOWS_ARM64, TerracottaMetadata.WINDOWS_ARM64,
            Platform.LINUX_X86_64, TerracottaMetadata.LINUX_X86_64,
            Platform.LINUX_ARM64, TerracottaMetadata.LINUX_ARM64
    ).get(Platform.SYSTEM_PLATFORM);

    @Override
    public Status status() throws IOException {
        return target.status();
    }

    @Override
    public Task<?> install(DoubleProperty progress) {
        Task<?> task = target.create();
        progress.bind(task.progressProperty());

        if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
            task = task.thenRunAsync(() -> Files.setPosixFilePermissions(target.getPath(), Set.of(
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
        return List.of(target.getPath().toString(), "--hmcl", path.toString());
    }
}
