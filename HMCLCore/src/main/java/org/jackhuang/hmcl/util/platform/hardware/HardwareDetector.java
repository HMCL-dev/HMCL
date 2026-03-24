/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util.platform.hardware;

import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Glavo
 */
public class HardwareDetector {
    private static final boolean USE_FAST_FETCH = "true".equalsIgnoreCase(System.getProperty("hmcl.hardware.fastfetch", "true"));

    public @Nullable CentralProcessor detectCentralProcessor() {
        return USE_FAST_FETCH ? FastFetchUtils.detectCentralProcessor() : null;
    }

    public @Nullable List<GraphicsCard> detectGraphicsCards() {
        return USE_FAST_FETCH ? FastFetchUtils.detectGraphicsCards() : null;
    }

    public long getTotalMemorySize() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean) {
                return bean.getTotalMemorySize();
            }
        } catch (NoClassDefFoundError ignored) {
        }

        return 0L;
    }

    public long getFreeMemorySize() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean) {
                return bean.getFreeMemorySize();
            }
        } catch (NoClassDefFoundError ignored) {
        }

        return 0L;
    }

    public StorageStatus getStorageStatus() {
        List<StorageStatus.DriveInfo> drives = new ArrayList<>();

        // For Linux and MacOS, use /proc/mounts or mount command to get all mount points
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
            drives = getUnixStorageStatus();
        } else {
            // For Windows, use File.listRoots()
            File[] roots = File.listRoots();
            if (roots == null) {
                return StorageStatus.INVALID;
            }

            for (File root : roots) {
                String mountPoint = root.getAbsolutePath();

                // Skip hidden drives on Windows (drives with '$' in name like \\?\Volume{...})
                // Skip pseudo drives like \\.\, etc.
                if (mountPoint.startsWith("\\\\")) {
                    continue;
                }
                // Skip hidden volumes (like system reserved partitions on Windows)
                if (mountPoint.contains("$")) {
                    continue;
                }

                // Get filesystem type
                String fileSystem = getFileSystem(root);

                long total = root.getTotalSpace();
                long available = root.getUsableSpace();

                // Skip invalid drives (0 total space or negative available)
                if (total <= 0 || available < 0) {
                    continue;
                }

                drives.add(new StorageStatus.DriveInfo(mountPoint, fileSystem, total, available));
            }
        }

        return drives.isEmpty() ? StorageStatus.INVALID : new StorageStatus(drives);
    }

    private List<StorageStatus.DriveInfo> getUnixStorageStatus() {
        List<StorageStatus.DriveInfo> drives = new ArrayList<>();

        try {
            // Read /proc/mounts on Linux
            java.nio.file.Path mountsPath = java.nio.file.Paths.get("/proc/mounts");
            if (java.nio.file.Files.exists(mountsPath)) {
                List<String> lines = java.nio.file.Files.readAllLines(mountsPath);
                for (String line : lines) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) {
                        String mountPoint = parts[1];
                        String fileSystem = parts[2];

                        // Skip pseudo filesystems
                        if (fileSystem.equals("proc") || fileSystem.equals("sysfs") ||
                            fileSystem.equals("devpts") || fileSystem.equals("tmpfs") ||
                            fileSystem.equals("cgroup") || fileSystem.equals("cgroup2") ||
                            fileSystem.equals("pstore") || fileSystem.equals("securityfs") ||
                            fileSystem.equals("debugfs") || fileSystem.equals("hugetlbfs") ||
                            fileSystem.equals("mqueue") || fileSystem.equals("configfs") ||
                            fileSystem.equals("fusectl") || fileSystem.equals("selinuxfs") ||
                            fileSystem.equals("binfmt_misc") || fileSystem.equals("devtmpfs") ||
                            fileSystem.equals("autofs") || fileSystem.equals("overlay") ||
                            fileSystem.equals("shm") || fileSystem.equals("nsfs")) {
                            continue;
                        }

                        // Only show user-relevant mount points
                        // Include: root (/), home, media, mnt, run, tmp, var, opt, srv, and user-specific paths
                        // Exclude: /snap, /boot, /boot/efi, /usr (only include if user home is there)
                        String userHome = System.getProperty("user.home");

                        // Skip system directories that users typically don't interact with
                        if (mountPoint.startsWith("/snap") ||
                            mountPoint.startsWith("/boot") ||
                            mountPoint.startsWith("/sys") ||
                            mountPoint.startsWith("/proc") ||
                            mountPoint.startsWith("/dev") ||
                            mountPoint.startsWith("/run") && !mountPoint.equals("/run")) {
                            continue;
                        }

                        // Only include specific user-relevant paths
                        if (!mountPoint.equals("/") &&
                            !mountPoint.startsWith("/home") &&
                            !mountPoint.startsWith("/media") &&
                            !mountPoint.startsWith("/mnt") &&
                            !mountPoint.startsWith("/tmp") &&
                            !mountPoint.startsWith("/var") &&
                            !mountPoint.startsWith("/opt") &&
                            !mountPoint.startsWith("/srv") &&
                            !mountPoint.startsWith(userHome)) {
                            // Also include /usr if it's directly mounted
                            if (!mountPoint.equals("/usr") && !mountPoint.startsWith("/usr/")) {
                                continue;
                            }
                        }

                        // Skip if it's a subdirectory of already-added mount point
                        boolean isSubDir = false;
                        for (StorageStatus.DriveInfo existing : drives) {
                            if (mountPoint.startsWith(existing.getMountPoint() + "/")) {
                                isSubDir = true;
                                break;
                            }
                        }
                        if (isSubDir) {
                            continue;
                        }

                        try {
                            File mountFile = new File(mountPoint);
                            if (mountFile.exists()) {
                                long total = mountFile.getTotalSpace();
                                long available = mountFile.getUsableSpace();

                                if (total > 0 && available >= 0) {
                                    drives.add(new StorageStatus.DriveInfo(mountPoint, fileSystem, total, available));
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback: just use root
            try {
                File root = new File("/");
                long total = root.getTotalSpace();
                long available = root.getUsableSpace();
                if (total > 0 && available >= 0) {
                    drives.add(new StorageStatus.DriveInfo("/", "unknown", total, available));
                }
            } catch (Exception ignored2) {
            }
        }

        return drives;
    }

    private String getFileSystem(File drive) {
        try {
            // Try to get the filesystem type using Java's FileSystem
            // This is a best-effort approach
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                // Try using Windows-specific methods
                try {
                    java.nio.file.FileStore store = java.nio.file.Files.getFileStore(drive.toPath());
                    return store.type();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
