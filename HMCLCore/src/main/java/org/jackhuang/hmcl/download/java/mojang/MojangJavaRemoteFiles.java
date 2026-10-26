// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.java.mojang;

import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.util.gson.JsonSubtype;
import org.jackhuang.hmcl.util.gson.JsonType;

import java.util.Collections;
import java.util.Map;

public record MojangJavaRemoteFiles(Map<String, Remote> files) {

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
            return downloads == null ? Collections.emptyMap() : downloads;
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
