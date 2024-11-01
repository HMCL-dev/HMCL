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

package com.jfoenix.skins;

import com.jfoenix.adapters.VirtualFlowAdapter;
import com.jfoenix.adapters.skins.ListViewSkinAdapter;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.effects.JFXDepthManager;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;

/**
 * <h1>Material Design ListView Skin</h1>
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class JFXListViewSkin<T> extends ListViewSkinAdapter<T> {

    private final VirtualFlowAdapter<ListCell<T>> flow;

    public JFXListViewSkin(final JFXListView<T> listView) {
        super(listView);
        flow = VirtualFlowAdapter.wrap(getChildren().get(0));
        JFXDepthManager.setDepth(flow.getFlow(), listView.depthProperty().get());
        listView.depthProperty().addListener((o, oldVal, newVal) -> JFXDepthManager.setDepth(flow.getFlow(), newVal));
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return 200;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        final int itemsCount = getSkinnable().getItems().size();
        if (getSkinnable().maxHeightProperty().isBound() || itemsCount <= 0) {
            return super.computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
        }

        final double fixedCellSize = getSkinnable().getFixedCellSize();
        double computedHeight = fixedCellSize != Region.USE_COMPUTED_SIZE ?
            fixedCellSize * itemsCount + snapVerticalInsets() : estimateHeight();
        double height = super.computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
        if (height > computedHeight) {
            height = computedHeight;
        }

        if (getSkinnable().getMaxHeight() > 0 && computedHeight > getSkinnable().getMaxHeight()) {
            return getSkinnable().getMaxHeight();
        }

        return height;
    }

    private double estimateHeight() {
        // compute the border/padding for the list
        double borderWidth = snapVerticalInsets();
        // compute the gap between list cells

        JFXListView<T> listview = (JFXListView<T>) getSkinnable();
        double gap = listview.isExpanded() ? ((JFXListView<T>) getSkinnable()).getVerticalGap() * (getSkinnable().getItems()
            .size()) : 0;
        // compute the height of each list cell
        double cellsHeight = 0;

        for (int i = 0; i < flow.getCellCount(); i++) {
            ListCell<T> cell = flow.getCell(i);
            cellsHeight += cell.getHeight();
        }
        return cellsHeight + gap + borderWidth;
    }

    private double snapVerticalInsets() {
        return getSkinnable().snappedBottomInset() + getSkinnable().snappedTopInset();
    }

}
