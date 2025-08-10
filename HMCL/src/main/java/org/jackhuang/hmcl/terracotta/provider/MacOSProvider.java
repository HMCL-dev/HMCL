package org.jackhuang.hmcl.terracotta.provider;

import javafx.beans.property.DoubleProperty;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.TerracottaDaemon;
import org.jackhuang.hmcl.terracotta.TerracottaMetadata;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class MacOSProvider implements ITerracottaProvider {
    public static final TerracottaDaemon INSTALLER, BINARY;

    static {
        if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
            INSTALLER = TerracottaMetadata.MACOS_INSTALLER_X86_64;
            BINARY = TerracottaMetadata.MACOS_BIN_X86_64;
        } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
            INSTALLER = TerracottaMetadata.MACOS_INSTALLER_ARM64;
            BINARY = TerracottaMetadata.MACOS_BIN_ARM64;
        } else {
            INSTALLER = null;
            BINARY = null;
        }
    }

    @Override
    public boolean exists() throws IOException {
        assert BINARY != null;

        return Files.exists(Path.of("/Applications/terracotta.app")) && BINARY.exists();
    }

    @Override
    public Task<?> install(DoubleProperty progress) throws IOException {
        assert INSTALLER != null && BINARY != null;

        Task<?> installerTask = INSTALLER.create();
        Task<?> binaryTask = BINARY.create();
        progress.bind(installerTask.progressProperty().add(binaryTask.progressProperty()).multiply(0.4)); // (1 + 1) * 0.4 = 0.8

        installerTask = installerTask.thenComposeAsync(() -> {
            ManagedProcess process = new ManagedProcess(new ProcessBuilder(
                    "osascript",
                    "-e",
                    String.format(
                            "do shell script \"%s\" with prompt \"%s\" with administrator privileges",
                            String.format("installer -pkg %s -target /Applications", INSTALLER.getPath()),
                            i18n("terracotta.sudo_installing")
                    )
            ));
            process.pumpInputStream(SystemUtils::onLogLine);
            process.pumpErrorStream(SystemUtils::onLogLine);

            return Task.fromCompletableFuture(process.getProcess().onExit());
        });
        binaryTask = binaryTask.thenRunAsync(() -> {
            Files.setPosixFilePermissions(BINARY.getPath(), Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE
            ));
        });

        return Task.allOf(installerTask, binaryTask);
    }

    @Override
    public List<String> launch(Path path) {
        assert BINARY != null;

        return List.of(BINARY.getPath().toString(), "--hmcl", path.toString());
    }
}
