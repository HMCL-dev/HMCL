/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.jfoenix.utils;

import javafx.beans.property.ReadOnlyBooleanPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Window;

/**
 * Used to observe changes in tree showing status for a {@link Node}.  For a Node's tree to be showing
 * it must be visible, its ancestors must be visible, the node must be part of a {@link Scene} and
 * the scene must have a {@link Window} which is currently showing.<p>
 *
 * This class provides the exact same functionality as {@link JFXNodeUtils#isTreeShowing(Node)} in
 * an observable form.
 */
public class TreeShowingProperty extends ReadOnlyBooleanPropertyBase {
    private final ChangeListener<Boolean> windowShowingChangedListener = (obs, old, current) -> updateTreeShowing();
    private final ChangeListener<Window> sceneWindowChangedListener = (obs, old, current) -> windowChanged(old, current);
    private final ChangeListener<Scene> nodeSceneChangedListener = (obs, old, current) -> sceneChanged(old, current);

    private final Node node;
    private final ObservableBooleanValue treeVisibleProperty;

    private boolean valid;
    private boolean treeShowing;

    /**
     * Constructs a new instance.
     *
     * @param node a {@link Node} for which the tree showing status should be observed, cannot be null
     */
    public TreeShowingProperty(Node node) {
        this.node = node;
        this.treeVisibleProperty = JFXNodeUtils.treeVisibleProperty(node);

        this.node.sceneProperty().addListener(nodeSceneChangedListener);
        this.treeVisibleProperty.addListener(windowShowingChangedListener);

        sceneChanged(null, node.getScene());
    }

    @Override
    public Object getBean() {
        return node;
    }

    @Override
    public String getName() {
        return "treeShowing";
    }

    /**
     * Cleans up any listeners that this class may have registered on the {@link Node}
     * that was supplied at construction.
     */
    public void dispose() {
        node.sceneProperty().removeListener(nodeSceneChangedListener);

        if (treeVisibleProperty != null)
            treeVisibleProperty.removeListener(windowShowingChangedListener);

        valid = false;  // prevents unregistration from triggering an invalidation notification
        sceneChanged(node.getScene(), null);
    }

    protected void invalidate() {
        if (valid) {
            valid = false;
            fireValueChangedEvent();
        }
    }

    @Override
    public boolean get() {
        if (!valid) {
            updateTreeShowing();
            valid = true;
        }

        return treeShowing;
    }

    private void sceneChanged(Scene oldScene, Scene newScene) {
        if (oldScene != null) {
            oldScene.windowProperty().removeListener(sceneWindowChangedListener);
        }
        if (newScene != null) {
            newScene.windowProperty().addListener(sceneWindowChangedListener);
        }

        windowChanged(
                oldScene == null ? null : oldScene.getWindow(),
                newScene == null ? null : newScene.getWindow()
        );
    }

    private void windowChanged(Window oldWindow, Window newWindow) {
        if (oldWindow != null) {
            oldWindow.showingProperty().removeListener(windowShowingChangedListener);
        }
        if (newWindow != null) {
            newWindow.showingProperty().addListener(windowShowingChangedListener);
        }

        updateTreeShowing();
    }

    private void updateTreeShowing() {
        boolean newValue = JFXNodeUtils.isTreeShowing(node);

        if (newValue != treeShowing) {
            treeShowing = newValue;
            invalidate();
        }
    }
}
