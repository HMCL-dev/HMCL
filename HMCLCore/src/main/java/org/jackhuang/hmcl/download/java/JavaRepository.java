package org.jackhuang.hmcl.download.java;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class JavaRepository {
    private JavaRepository() {
    }

    public static Task<JavaVersion> downloadJava(GameJavaVersion javaVersion, DownloadProvider downloadProvider) {
        return new JavaDownloadTask(javaVersion, getJavaStoragePath(), downloadProvider)
                .thenSupplyAsync(() -> {
                    String platform = getSystemJavaPlatform().orElseThrow(JavaDownloadTask.UnsupportedPlatformException::new);
                    return addJava(getJavaHome(javaVersion, platform));
                });
    }

    public static JavaVersion addJava(Path javaHome) throws InterruptedException, IOException {
        if (Files.isDirectory(javaHome)) {
            Path executable = JavaVersion.getExecutable(javaHome);
            if (Files.isRegularFile(executable)) {
                JavaVersion javaVersion = JavaVersion.fromExecutable(executable);
                JavaVersion.getJavas().add(javaVersion);
                return javaVersion;
            }
        }

        throw new IOException("Incorrect java home " + javaHome);
    }

    public static void initialize() throws IOException, InterruptedException {
        Optional<String> platformOptional = getSystemJavaPlatform();
        if (platformOptional.isPresent()) {
            String platform = platformOptional.get();
            Path javaStoragePath = getJavaStoragePath();
            if (Files.isDirectory(javaStoragePath)) {
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(javaStoragePath)) {
                    for (Path component : dirStream) {
                        Path javaHome = component.resolve(platform).resolve(component.getFileName());
                        try {
                            addJava(javaHome);
                        } catch (IOException e) {
                            LOG.log(Level.WARNING, "Failed to determine Java at " + javaHome, e);
                        }
                    }
                }
            }
        }
    }

    public static Optional<String> getSystemJavaPlatform() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86) {
                return Optional.of("linux-i386");
            } else if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                return Optional.of("linux");
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86_64 || Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                return Optional.of("mac-os");
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86) {
                return Optional.of("windows-x86");
            } else if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                return Optional.of("windows-x64");
            } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                if (OperatingSystem.SYSTEM_BUILD_NUMBER >= 21277) {
                    return Optional.of("windows-x64");
                } else {
                    return Optional.of("windows-x86");
                }
            }
        }
        return Optional.empty();
    }

    public static Path getJavaStoragePath() {
        return CacheRepository.getInstance().getCacheDirectory().resolve("java");
    }

    public static Path getJavaHome(GameJavaVersion javaVersion, String platform) {
        return getJavaStoragePath().resolve(javaVersion.getComponent()).resolve(platform).resolve(javaVersion.getComponent());
    }
}
