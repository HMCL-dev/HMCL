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

package com.jfoenix.controls.datamodels.treetable;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeTableColumn;

/**
 * data model that is used in JFXTreeTableView, it's used to implement
 * the grouping feature.
 * <p>
 * <b>Note:</b> the data object used in JFXTreeTableView <b>must</b> extends this class
 *
 * @param <T> is the concrete object of the Tree table
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class RecursiveTreeObject<T> {

    /**
     * grouped children objects
     */
    ObservableList<T> children = FXCollections.observableArrayList();

    public ObservableList<T> getChildren() {
        return children;
    }

    public void setChildren(ObservableList<T> children) {
        this.children = children;
    }

    /**
     * Whether or not the object is grouped by a specified tree table column
     */
    ObjectProperty<TreeTableColumn<T, ?>> groupedColumn = new SimpleObjectProperty<>();

    public final ObjectProperty<TreeTableColumn<T, ?>> groupedColumnProperty() {
        return this.groupedColumn;
    }

    public final TreeTableColumn<T, ?> getGroupedColumn() {
        return this.groupedColumnProperty().get();
    }

    public final void setGroupedColumn(final TreeTableColumn<T, ?> groupedColumn) {
        this.groupedColumnProperty().set(groupedColumn);
    }

    /**
     * the value that must be shown when grouped
     */
    ObjectProperty<Object> groupedValue = new SimpleObjectProperty<>();

    public final ObjectProperty<Object> groupedValueProperty() {
        return this.groupedValue;
    }

    public final Object getGroupedValue() {
        return this.groupedValueProperty().get();
    }

    public final void setGroupedValue(final Object groupedValue) {
        this.groupedValueProperty().set(groupedValue);
    }

}
