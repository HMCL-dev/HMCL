/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.SingleSelectionModel;

import java.util.function.Supplier;

public interface TabControl {
    ObservableList<Tab<?>> getTabs();

    class TabControlSelectionModel extends SingleSelectionModel<Tab<?>> {
        private final TabControl tabHeader;

        public TabControlSelectionModel(final TabControl t) {
            if (t == null) {
                throw new NullPointerException("TabPane can not be null");
            }
            this.tabHeader = t;

            // watching for changes to the items list content
            final ListChangeListener<Tab<?>> itemsContentObserver = c -> {
                while (c.next()) {
                    for (Tab<?> tab : c.getRemoved()) {
                        if (tab != null && !tabHeader.getTabs().contains(tab)) {
                            if (tab.isSelected()) {
                                tab.setSelected(false);
                                final int tabIndex = c.getFrom();

                                // we always try to select the nearest, non-disabled
                                // tab from the position of the closed tab.
                                findNearestAvailableTab(tabIndex, true);
                            }
                        }
                    }
                    if (c.wasAdded() || c.wasRemoved()) {
                        // The selected tab index can be out of sync with the list of tab if
                        // we add or remove tabs before the selected tab.
                        if (getSelectedIndex() != tabHeader.getTabs().indexOf(getSelectedItem())) {
                            clearAndSelect(tabHeader.getTabs().indexOf(getSelectedItem()));
                        }
                    }
                }
                if (getSelectedIndex() == -1 && getSelectedItem() == null && tabHeader.getTabs().size() > 0) {
                    // we go looking for the first non-disabled tab, as opposed to
                    // just selecting the first tab (fix for RT-36908)
                    findNearestAvailableTab(0, true);
                } else if (tabHeader.getTabs().isEmpty()) {
                    clearSelection();
                }
            };
            if (this.tabHeader.getTabs() != null) {
                this.tabHeader.getTabs().addListener(itemsContentObserver);
            }
        }

        // API Implementation
        @Override public void select(int index) {
            if (index < 0 || (getItemCount() > 0 && index >= getItemCount()) ||
                    (index == getSelectedIndex() && getModelItem(index).isSelected())) {
                return;
            }

            // Unselect the old tab
            if (getSelectedIndex() >= 0 && getSelectedIndex() < tabHeader.getTabs().size()) {
                tabHeader.getTabs().get(getSelectedIndex()).setSelected(false);
            }

            setSelectedIndex(index);

            Tab tab = getModelItem(index);
            if (tab != null) {
                setSelectedItem(tab);
            }

            // Select the new tab
            if (getSelectedIndex() >= 0 && getSelectedIndex() < tabHeader.getTabs().size()) {
                tabHeader.getTabs().get(getSelectedIndex()).setSelected(true);
            }

            /* Does this get all the change events */
            ((Node) tabHeader).notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_ITEM);
        }

        @Override public void select(Tab tab) {
            final int itemCount = getItemCount();

            for (int i = 0; i < itemCount; i++) {
                final Tab value = getModelItem(i);
                if (value != null && value.equals(tab)) {
                    select(i);
                    return;
                }
            }
            if (tab != null) {
                setSelectedItem(tab);
            }
        }

        @Override protected Tab<?> getModelItem(int index) {
            final ObservableList<Tab<?>> items = tabHeader.getTabs();
            if (items == null) return null;
            if (index < 0 || index >= items.size()) return null;
            return items.get(index);
        }

        @Override protected int getItemCount() {
            final ObservableList<Tab<?>> items = tabHeader.getTabs();
            return items == null ? 0 : items.size();
        }

        private Tab<?> findNearestAvailableTab(int tabIndex, boolean doSelect) {
            // we always try to select the nearest, non-disabled
            // tab from the position of the closed tab.
            final int tabCount = getItemCount();
            int i = 1;
            Tab<?> bestTab = null;
            while (true) {
                // look leftwards
                int downPos = tabIndex - i;
                if (downPos >= 0) {
                    Tab<?> _tab = getModelItem(downPos);
                    if (_tab != null) {
                        bestTab = _tab;
                        break;
                    }
                }

                // look rightwards. We subtract one as we need
                // to take into account that a tab has been removed
                // and if we don't do this we'll miss the tab
                // to the right of the tab (as it has moved into
                // the removed tabs position).
                int upPos = tabIndex + i - 1;
                if (upPos < tabCount) {
                    Tab<?> _tab = getModelItem(upPos);
                    if (_tab != null) {
                        bestTab = _tab;
                        break;
                    }
                }

                if (downPos < 0 && upPos >= tabCount) {
                    break;
                }
                i++;
            }

            if (doSelect && bestTab != null) {
                select(bestTab);
            }

            return bestTab;
        }
    }

    class Tab<T extends Node> {
        private final StringProperty id = new SimpleStringProperty(this, "id");
        private final StringProperty text = new SimpleStringProperty(this, "text");
        private final ReadOnlyBooleanWrapper selected = new ReadOnlyBooleanWrapper(this, "selected");
        private final ObjectProperty<T> node = new SimpleObjectProperty<>(this, "node");
        private final ObjectProperty<Object> userData = new SimpleObjectProperty<>(this, "userData");
        private Supplier<? extends T> nodeSupplier;

        public Tab(String id) {
            setId(id);
        }

        public Tab(String id, String text) {
            setId(id);
            setText(text);
        }

        public Supplier<? extends T> getNodeSupplier() {
            return nodeSupplier;
        }

        public void setNodeSupplier(Supplier<? extends T> nodeSupplier) {
            this.nodeSupplier = nodeSupplier;
        }

        public String getId() {
            return id.get();
        }

        public StringProperty idProperty() {
            return id;
        }

        public void setId(String id) {
            this.id.set(id);
        }

        public String getText() {
            return text.get();
        }

        public StringProperty textProperty() {
            return text;
        }

        public void setText(String text) {
            this.text.set(text);
        }

        public boolean isSelected() {
            return selected.get();
        }

        public ReadOnlyBooleanProperty selectedProperty() {
            return selected.getReadOnlyProperty();
        }

        private void setSelected(boolean selected) {
            this.selected.set(selected);
        }

        public T getNode() {
            return node.get();
        }

        public ObjectProperty<T> nodeProperty() {
            return node;
        }

        public void setNode(T node) {
            this.node.set(node);
        }

        public Object getUserData() {
            return userData.get();
        }

        public ObjectProperty<?> userDataProperty() {
            return userData;
        }

        public void setUserData(Object userData) {
            this.userData.set(userData);
        }

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
}
