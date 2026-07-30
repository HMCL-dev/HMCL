/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.terracotta;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
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
                new URI(url);
            } catch (URISyntaxException e) {
                throw new JsonParseException("Invalid URL: " + url, e);
            }
        }
    }

    private static volatile List<URI> list;

    public static List<URI> fetch() {
        List<URI> list = TerracottaNodeList.list;
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

                                return StringUtils.isBlank(node.region) || LocaleUtils.IS_CHINA_MAINLAND == "CN".equalsIgnoreCase(node.region);
                            })
                            .map(it -> URI.create(it.url()))
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
