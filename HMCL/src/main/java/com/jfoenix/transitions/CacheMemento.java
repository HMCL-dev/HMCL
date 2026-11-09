/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.transitions;

import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.layout.Region;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CacheMemento {
    private boolean cache;
    private boolean cacheShape;
    private boolean snapToPixel;
    private CacheHint cacheHint = CacheHint.DEFAULT;
    private final Node node;
    private final AtomicBoolean isCached = new AtomicBoolean(false);

    public CacheMemento(Node node) {
        this.node = node;
    }

    /**
     * this method will cache the node only if it wasn't cached before
     */
    public void cache() {
        if (!isCached.getAndSet(true)) {
            this.cache = node.isCache();
            this.cacheHint = node.getCacheHint();
            node.setCache(true);
            node.setCacheHint(CacheHint.SPEED);
            if (node instanceof Region region) {
                this.cacheShape = region.isCacheShape();
                this.snapToPixel = region.isSnapToPixel();
                region.setCacheShape(true);
                region.setSnapToPixel(true);
            }
        }
    }

    public void restore() {
        if (isCached.getAndSet(false)) {
            node.setCache(cache);
            node.setCacheHint(cacheHint);
            if (node instanceof Region region) {
                region.setCacheShape(cacheShape);
                region.setSnapToPixel(snapToPixel);
            }
        }
    }
}
