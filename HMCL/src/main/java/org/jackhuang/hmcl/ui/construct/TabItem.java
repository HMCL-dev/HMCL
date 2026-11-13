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
public final class TabItem<T extends @Nullable Node> {
    private @Nullable T node;
    private @Nullable Supplier<? extends T> nodeSupplier;

    /// Creates a tab with no text, content, or content factory.
    public TabItem() {
    }

    /// Creates a tab whose content is supplied lazily by `nodeSupplier`.
    public TabItem(Supplier<? extends T> nodeSupplier) {
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

    /// Returns the content node, invoking the factory if content has not been initialized.
    /// Returns null if no factory is set or the factory returns null; later calls may retry.
    /// Factory exceptions propagate without assigning content.
    public T getNode() {
        if (node != null)
            return node;

        if (nodeSupplier != null) {
            node = nodeSupplier.get();
            nodeSupplier = null;
            return node;
        }

        return null;
    }

    /// Returns whether a non-null content node is currently assigned.
    public boolean isInitialized() {
        return nodeSupplier == null;
    }

}
