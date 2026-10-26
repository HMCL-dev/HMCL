// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// @author huangyuhui
public record ExtractRules(@Unmodifiable List<String> exclude) {

    public static final ExtractRules EMPTY = new ExtractRules(List.of());

    public ExtractRules {
        exclude = exclude != null ? List.copyOf(exclude) : List.of();
    }

    public boolean shouldExtract(String path) {
        return exclude.stream().noneMatch(path::startsWith);
    }

}
