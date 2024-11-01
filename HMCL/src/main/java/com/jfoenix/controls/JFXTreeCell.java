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

package com.jfoenix.controls;

import com.jfoenix.utils.JFXNodeUtils;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.lang.ref.WeakReference;

/**
 * JFXTreeCell is simple material design implementation of a tree cell.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2017-02-15
 */
public class JFXTreeCell<T> extends TreeCell<T> {

    protected JFXRippler cellRippler = new JFXRippler(this){
        @Override
        protected Node getMask() {
            Region clip = new Region();
            JFXNodeUtils.updateBackground(JFXTreeCell.this.getBackground(), clip);
            double width = control.getLayoutBounds().getWidth();
            double height = control.getLayoutBounds().getHeight();
            clip.resize(width, height);
            return clip;
        }

        @Override
        protected void positionControl(Node control) {
            // do nothing
        }
    };
    private HBox hbox;
    private final StackPane selectedPane = new StackPane();

    private final InvalidationListener treeItemGraphicInvalidationListener = observable -> updateDisplay(getItem(),
        isEmpty());
    private final WeakInvalidationListener weakTreeItemGraphicListener = new WeakInvalidationListener(
        treeItemGraphicInvalidationListener);

    private WeakReference<TreeItem<T>> treeItemRef;

    public JFXTreeCell() {

        selectedPane.getStyleClass().add("selection-bar");
        selectedPane.setBackground(new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY, Insets.EMPTY)));
        selectedPane.setPrefWidth(3);
        selectedPane.setMouseTransparent(true);
        selectedProperty().addListener((o, oldVal, newVal) -> selectedPane.setVisible(newVal));

        final InvalidationListener treeItemInvalidationListener = observable -> {
            TreeItem<T> oldTreeItem = treeItemRef == null ? null : treeItemRef.get();
            if (oldTreeItem != null) {
                oldTreeItem.graphicProperty().removeListener(weakTreeItemGraphicListener);
            }

            TreeItem<T> newTreeItem = getTreeItem();
            if (newTreeItem != null) {
                newTreeItem.graphicProperty().addListener(weakTreeItemGraphicListener);
                treeItemRef = new WeakReference<>(newTreeItem);
            }
        };
        final WeakInvalidationListener weakTreeItemListener = new WeakInvalidationListener(treeItemInvalidationListener);
        treeItemProperty().addListener(weakTreeItemListener);
        if (getTreeItem() != null) {
            getTreeItem().graphicProperty().addListener(weakTreeItemGraphicListener);
        }
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (!getChildren().contains(selectedPane)) {
            getChildren().add(0, cellRippler);
            cellRippler.rippler.clear();
            getChildren().add(0, selectedPane);
        }
        cellRippler.resizeRelocate(0, 0, getWidth(), getHeight());
        cellRippler.releaseRipple();
        selectedPane.resizeRelocate(0, 0, selectedPane.prefWidth(-1), getHeight());
        selectedPane.setVisible(isSelected());
    }

    private void updateDisplay(T item, boolean empty) {
        if (item == null || empty) {
            hbox = null;
            setText(null);
            setGraphic(null);
        } else {
            TreeItem<T> treeItem = getTreeItem();
            if (treeItem != null && treeItem.getGraphic() != null) {
                if (item instanceof Node) {
                    setText(null);
                    if (hbox == null) {
                        hbox = new HBox(3);
                    }
                    hbox.getChildren().setAll(treeItem.getGraphic(), (Node) item);
                    setGraphic(hbox);
                } else {
                    hbox = null;
                    setText(item.toString());
                    setGraphic(treeItem.getGraphic());
                }
            } else {
                hbox = null;
                if (item instanceof Node) {
                    setText(null);
                    setGraphic((Node) item);
                } else {
                    setText(item.toString());
                    setGraphic(null);
                }
            }
        }
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        updateDisplay(item, empty);
        setMouseTransparent(item == null || empty);
    }
}
