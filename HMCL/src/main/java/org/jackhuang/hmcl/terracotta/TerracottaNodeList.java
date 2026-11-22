// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.terracotta;

import com.google.gson.JsonParseException;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.util.RegionUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public final class TerracottaNodeList {
    private static final String NODE_LIST_URL = "https://terracotta.glavo.site/nodes";

    @JsonSerializable
    private record TerracottaNode(String url, @Nullable String region) implements Validation {
        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            Validation.requireNonNull(url, "TerracottaNode.url cannot be null");
            try {
                WebURL.parse(url);
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("Invalid URL: " + url, e);
            }
        }
    }

    /// Cached node URLs, or `null` before the first fetch completes.
    private static volatile @Nullable @Unmodifiable List<WebURL> list;

    /// Returns the cached unmodifiable node URLs, fetching once and caching an empty list on failure.
    public static @Unmodifiable List<WebURL> fetch() {
        @Nullable @Unmodifiable List<WebURL> list = TerracottaNodeList.list;
        if (list != null)
            return list;

        synchronized (TerracottaNodeList.class) {
            list = TerracottaNodeList.list;
            if (list != null)
                return list;

            try {
                List<TerracottaNode> nodes = HttpRequest.GET(NODE_LIST_URL)
                        .getJson(JsonUtils.listTypeOf(TerracottaNode.class));

                if (nodes == null) {
                    list = List.of();
                    LOG.info("No available Terracotta nodes found");
                } else {
                    list = nodes.stream()
                            .filter(node -> {
                                if (node == null)
                                    return false;

                                try {
                                    node.validate();
                                } catch (Exception e) {
                                    LOG.warning("Invalid terracotta node: " + node, e);
                                    return false;
                                }

                                return StringUtils.isBlank(node.region) || node.region.equalsIgnoreCase(RegionUtils.MACHINE_REGION);
                            })
                            .map(it -> WebURL.parse(it.url()))
                            .toList();
                    LOG.info("Terracotta node list: " + list);
                }
            } catch (Exception e) {
                LOG.warning("Failed to fetch terracotta node list", e);
                list = List.of();
            }

            TerracottaNodeList.list = list;
            return list;
        }
    }

    private TerracottaNodeList() {
    }
}
