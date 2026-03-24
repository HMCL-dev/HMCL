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

import java.util.List;

public final class StorageStatus {
    private final List<DriveInfo> drives;

    public StorageStatus(List<DriveInfo> drives) {
        this.drives = drives;
    }

    public List<DriveInfo> getDrives() {
        return drives;
    }

    public boolean isValid() {
        return drives != null && !drives.isEmpty();
    }

    public static final StorageStatus INVALID = new StorageStatus(null);

    public static final class DriveInfo {
        private final String mountPoint;
        private final String fileSystem;
        private final long total;
        private final long available;

        public DriveInfo(String mountPoint, String fileSystem, long total, long available) {
            this.mountPoint = mountPoint;
            this.fileSystem = fileSystem;
            this.total = total;
            this.available = available;
        }

        public String getMountPoint() {
            return mountPoint;
        }

        public String getFileSystem() {
            return fileSystem;
        }

        public long getTotal() {
            return total;
        }

        public long getAvailable() {
            return available;
        }

        public long getUsed() {
            return total - available;
        }
    }
}
