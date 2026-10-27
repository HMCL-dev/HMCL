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
    /// The nullable text displayed in the header.
    private final StringProperty text = new SimpleStringProperty(this, "text");
    /// The content node, or null when no content is assigned.
    private final ObjectProperty<@Nullable T> node = new SimpleObjectProperty<>(this, "node");
    /// The optional factory used when content has not been assigned.
    private @Nullable Supplier<? extends @Nullable T> nodeSupplier;

    /// Creates a tab with no text, content, or content factory.
    public TabItem() {
    }

    /// Creates a tab whose content is supplied lazily by [#initializeIfNeeded()].
    public TabItem(Supplier<? extends @Nullable T> nodeSupplier) {
        this.nodeSupplier = nodeSupplier;
    }

    /// Returns the content factory, or null if none has been set.
    public @Nullable Supplier<? extends @Nullable T> getNodeSupplier() {
        return nodeSupplier;
    }

    /// Sets the content factory, or clears it if null. Existing content is unchanged.
    public void setNodeSupplier(@Nullable Supplier<? extends @Nullable T> nodeSupplier) {
        this.nodeSupplier = nodeSupplier;
    }

    /// Returns the header text, which may be null.
    public @Nullable String getText() {
        return text.get();
    }

    /// Returns the writable header text property, whose value may be null.
    public StringProperty textProperty() {
        return text;
    }

    /// Sets the header text, which may be null.
    public void setText(@Nullable String text) {
        this.text.set(text);
    }

    /// Returns the content node, or null if none is assigned; does not invoke the factory.
    public @Nullable T getNode() {
        return node.get();
    }

    /// Returns the writable content property. A null value permits lazy initialization.
    public ObjectProperty<@Nullable T> nodeProperty() {
        return node;
    }

    /// Sets the content node, or clears it if null. Does not change the content factory.
    public void setNode(@Nullable T node) {
        this.node.set(node);
    }

    /// Returns whether a non-null content node is currently assigned.
    public boolean isInitialized() {
        return getNode() != null;
    }

    /// Invokes the content factory and assigns its result if the content is null
    /// and a factory is present. A null result permits another attempt on the next call.
    /// Factory exceptions propagate without assigning a result.
    ///
    /// @return true if the factory returned and its result was assigned, even if null;
    /// false if content was already assigned or no factory was present
    public boolean initializeIfNeeded() {
        if (getNode() == null) {
            if (getNodeSupplier() == null) {
                return false;
            }
            setNode(getNodeSupplier().get());
            return true;
        }
        return false;
    }
}
