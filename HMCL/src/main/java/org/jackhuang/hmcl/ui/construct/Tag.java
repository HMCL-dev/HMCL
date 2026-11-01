// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Describes an immutable text tag that can be displayed by multiple components.
/// Replace a tag in a component's tag list to change its text or type.
///
/// @param text the tag text
/// @param type the tag's semantic type
@NotNullByDefault
public record Tag(String text, Type type) {

    /// Defines the semantic types supported by tag renderers.
    @NotNullByDefault
    public enum Type {
        /// Marks ordinary descriptive information.
        NORMAL,

        /// Marks information that requires the user's attention.
        WARNING
    }

    /// Creates a tag with the given text.
    ///
    /// @throws NullPointerException if text or type is null
    public Tag(String text) {
        this(text, Type.NORMAL);
    }

    /// Creates a tag with the given text and type.
    ///
    /// @throws NullPointerException if text or type is null
    public Tag {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(type, "type");
    }

    /// Creates a tag with the [Type#WARNING] type.
    ///
    /// @param text the tag text
    /// @return a new warning tag
    public static Tag warning(String text) {
        return new Tag(text, Type.WARNING);
    }
}
