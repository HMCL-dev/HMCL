// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.*;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/// Stores a tab's label and optionally lazily created content.
///
/// @param <T> the type of content node
@NotNullByDefault
public final class TabItem<T extends Node> {
    private @Nullable T node;
    private @Nullable Supplier<? extends @Nullable T> nodeSupplier;

    /// Creates a tab with no text, content, or content factory.
    public TabItem() {
    }

    /// Creates a tab whose content is supplied lazily by [#initializeIfNeeded()].
    public TabItem(Supplier<? extends @Nullable T> nodeSupplier) {
        this.nodeSupplier = nodeSupplier;
    }

    /// The  text displayed in the header.
    private @Nullable StringProperty text;

    /// Returns the writable header text property, whose value may be null.
    public StringProperty textProperty() {
        if (text == null)
            text = new SimpleStringProperty(this, "text");
        return text;
    }

    /// Returns the header text, which may be null.
    public @Nullable String getText() {
        return text != null ? text.get() : null;
    }

    /// Sets the header text, which may be null.
    public void setText(@Nullable String text) {
        if (this.text != null || text != null)
            textProperty().set(text);
    }

    /// Returns the content node, or null if none is assigned; does not invoke the factory.
    public @Nullable T getNode() {
        initializeIfNeeded();
        return node;
    }

    /// Returns whether a non-null content node is currently assigned.
    public boolean isInitialized() {
        return node != null;
    }

    /// Invokes the content factory and assigns its result if the content is null
    /// and a factory is present. A null result permits another attempt on the next call.
    /// Factory exceptions propagate without assigning a result.
    ///
    /// @return true if the factory returned and its result was assigned, even if null;
    /// false if content was already assigned or no factory was present
    public boolean initializeIfNeeded() {
        if (node == null) {
            if (nodeSupplier == null) {
                return false;
            }
            node = nodeSupplier.get();
            return true;
        }
        return false;
    }
}
