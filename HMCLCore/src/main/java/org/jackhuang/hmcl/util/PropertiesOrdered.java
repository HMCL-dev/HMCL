// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/// Copied from Optifine
public class PropertiesOrdered extends Properties {
    private final Set<Object> keysOrdered = new LinkedHashSet<>();

    public PropertiesOrdered() {
        super();
    }

    public synchronized Object put(Object key, Object value) {
        if (key instanceof String stringKey) {
            key = stringKey.trim();
        }

        if (value instanceof String stringValue) {
            value = stringValue.trim();
        }

        this.keysOrdered.add(key);
        return super.put(key, value);
    }

    public @NotNull Set<Object> keySet() {
        this.keysOrdered.retainAll(super.keySet());
        return Collections.unmodifiableSet(this.keysOrdered);
    }

    public synchronized Enumeration<Object> keys() {
        return Collections.enumeration(this.keySet());
    }
}

