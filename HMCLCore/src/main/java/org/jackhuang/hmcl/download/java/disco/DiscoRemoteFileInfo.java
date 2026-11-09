// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.java.disco;

import com.google.gson.annotations.SerializedName;

/**
 * @author Glavo
 */
public record DiscoRemoteFileInfo(@SerializedName("filename") String fileName,
                                  @SerializedName("direct_download_uri") String directDownloadUri,
                                  @SerializedName("checksum_type") String checksumType,
                                  @SerializedName("checksum") String checksum,
                                  @SerializedName("checksum_uri") String checksumUri) {
}
