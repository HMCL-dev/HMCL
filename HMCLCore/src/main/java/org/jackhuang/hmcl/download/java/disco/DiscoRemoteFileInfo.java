/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.java.disco;

import com.google.gson.annotations.SerializedName;

/**
 * @author Glavo
 */
public final class DiscoRemoteFileInfo {
    @SerializedName("filename")
    private final String fileName;

    @SerializedName("direct_download_uri")
    private final String directDownloadUri;

    @SerializedName("checksum_type")
    private final String checksumType;

    @SerializedName("checksum")
    private final String checksum;

    @SerializedName("checksum_uri")
    private final String checksumUri;

    public DiscoRemoteFileInfo(String fileName, String directDownloadUri, String checksumType, String checksum, String checksumUri) {
        this.fileName = fileName;
        this.directDownloadUri = directDownloadUri;
        this.checksumType = checksumType;
        this.checksum = checksum;
        this.checksumUri = checksumUri;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDirectDownloadUri() {
        return directDownloadUri;
    }

    public String getChecksumType() {
        return checksumType;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getChecksumUri() {
        return checksumUri;
    }
}
