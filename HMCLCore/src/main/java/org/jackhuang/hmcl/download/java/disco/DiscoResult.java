// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.java.disco;

import com.google.gson.reflect.TypeToken;

import java.util.List;

/**
 * @author Glavo
 */
public final class DiscoResult<T> {

    @SuppressWarnings("unchecked")
    public static <T> TypeToken<DiscoResult<T>> typeOf(Class<T> argType) {
        return (TypeToken<DiscoResult<T>>) TypeToken.getParameterized(DiscoResult.class, argType);
    }

    private final List<T> result;
    private final String message;

    private DiscoResult(List<T> result, String message) {
        this.result = result;
        this.message = message;
    }

    public List<T> getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }
}
