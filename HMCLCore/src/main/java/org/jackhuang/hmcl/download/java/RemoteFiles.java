/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.java;

import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.util.gson.JsonSubtype;
import org.jackhuang.hmcl.util.gson.JsonType;

import java.util.Map;

public class RemoteFiles {
    private final Map<String, RemoteFile> files;

    public RemoteFiles(Map<String, RemoteFile> files) {
        this.files = files;
    }

    public Map<String, RemoteFile> getFiles() {
        return files;
    }

    @JsonType(
            property = "type",
            subtypes = {
                    @JsonSubtype(clazz = RemoteFile.class, name = "file"),
                    @JsonSubtype(clazz = RemoteDirectory.class, name = "directory"),
                    @JsonSubtype(clazz = RemoteLink.class, name = "link")
            }
    )
    public static class Remote {
        private final String type;

        public Remote(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    public static class RemoteFile extends Remote {
        private final boolean executable;
        private final Map<String, DownloadInfo> downloads;

        public RemoteFile(boolean executable, Map<String, DownloadInfo> downloads) {
            super("file");
            this.executable = executable;
            this.downloads = downloads;
        }

        public boolean isExecutable() {
            return executable;
        }

        public Map<String, DownloadInfo> getDownloads() {
            return downloads;
        }
    }

    public static class RemoteDirectory extends Remote {
        public RemoteDirectory() {
            super("directory");
        }
    }

    public static class RemoteLink extends Remote {
        private final String target;

        public RemoteLink(String target) {
            super("link");
            this.target = target;
        }

        public String getTarget() {
            return target;
        }
    }
}
