package org.jackhuang.hmcl.download.java;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;

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

    public static Stream<Optional<Path>> findMinecraftRuntimeDirs() {
        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                return Stream.of(
                        FileUtils.tryGetPath(System.getenv("localappdata"),
                                "Packages\\Microsoft.4297127D64EC6_8wekyb3d8bbwe\\LocalCache\\Local\\runtime"),
                        FileUtils.tryGetPath(
                                Optional.ofNullable(System.getenv("ProgramFiles(x86)")).orElse("C:\\Program Files (x86)"),
                                "Minecraft Launcher\\runtime"));
            case LINUX:
                return Stream.of(FileUtils.tryGetPath(System.getProperty("user.home", ".minecraft/runtime")));
            case OSX:
                return Stream.of(FileUtils.tryGetPath(System.getProperty("user.home"), "Library/Application Support/minecraft/runtime"));
            default:
                return Stream.empty();
        }
    }

    public static Stream<Path> findJavaHomeInMinecraftRuntimeDir(Path runtimeDir) {
        if (!Files.isDirectory(runtimeDir))
            return Stream.empty();
        // Examples:
        // $HOME/Library/Application Support/minecraft/runtime/java-runtime-beta/mac-os/java-runtime-beta/jre.bundle/Contents/Home
        // $HOME/.minecraft/runtime/java-runtime-beta/linux/java-runtime-beta
        Optional<String> platformOptional = getSystemJavaPlatform();
        if (!platformOptional.isPresent()) return Stream.empty();
        List<Path> javaHomes = new ArrayList<>();
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(runtimeDir)) {
            // component can be jre-legacy, java-runtime-alpha, java-runtime-beta, java-runtime-gamma or any other being added in the future.
            for (Path component : dir) {
                findJavaHomeInComponentDir(platformOptional.get(), component).ifPresent(javaHomes::add);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list java-runtime directory " + runtimeDir, e);
        }
        return javaHomes.stream();
    }

    private static Optional<Path> findJavaHomeInComponentDir(String platform, Path component) {
        Path sha1File = component.resolve(platform).resolve(component.getFileName() + ".sha1");
        if (!Files.isRegularFile(sha1File))
            return Optional.empty();
        Path dir = component.resolve(platform).resolve(component.getFileName());

        try (BufferedReader reader = Files.newBufferedReader(sha1File)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;

                int idx = line.indexOf(" /#//");
                if (idx <= 0)
                    throw new IOException("Illegal line: " + line);

                Path file = dir.resolve(line.substring(0, idx));

                // Should we check the sha1 of files? This will take a lot of time.
                if (Files.notExists(file))
                    throw new NoSuchFileException(file.toAbsolutePath().toString());
            }

            if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                Path macPath = dir.resolve("jre.bundle/Contents/Home");
                if (Files.exists(macPath))
                    return Optional.of(macPath);
                else
                    LOG.warning("The Java is not in 'jre.bundle/Contents/Home'");
            }

            return Optional.of(dir);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to verify Java in " + component, e);
            return Optional.empty();
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
        Path javaHome = getJavaStoragePath().resolve(javaVersion.getComponent()).resolve(platform).resolve(javaVersion.getComponent());
        if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX)
            javaHome = javaHome.resolve("jre.bundle/Contents/Home");
        return javaHome;
    }
}
