// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Immutable;

/**
 *
 * @author huangyuhui
 */
@Immutable
public class AssetIndexInfo extends IdDownloadInfo {

    private final long totalSize;

    public AssetIndexInfo() {
        this("", "");
    }

    public AssetIndexInfo(String id, String url) {
        this(id, url, null);
    }

    public AssetIndexInfo(String id, String url, String sha1) {
        this(id, url, sha1, 0);
    }

    public AssetIndexInfo(String id, String url, String sha1, int size) {
        this(id, url, sha1, size, 0);
    }

    public AssetIndexInfo(String id, String url, String sha1, int size, long totalSize) {
        super(id, url, sha1, size);
        this.totalSize = totalSize;
    }

    public long getTotalSize() {
        return totalSize;
    }
}
