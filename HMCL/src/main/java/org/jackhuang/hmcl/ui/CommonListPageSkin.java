/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.CommonMDListCell;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public abstract class CommonListPageSkin<T> extends SkinBase<CommonListPage<T>> {

    private final JFXListView<T> listView = new JFXListView<>();
    private final TransitionPane toolbarPane = new TransitionPane();

    private final AtomicInteger lastNotShiftClickIndex = new AtomicInteger(-1);
    private final AtomicBoolean requestMenu = new AtomicBoolean(false);

    public CommonListPageSkin(CommonListPage<T> skinnable) {
        super(skinnable);
        initPane();
    }

    public CommonListPageSkin(CommonListPage<T> skinnable, CommonListPage.SelectionType selectionType) {
        super(skinnable);
        skinnable.setSelectionType(selectionType);
        initPane();
    }

    private void initPane() {

        StackPane pagePane = new StackPane();
        {
            pagePane.setPadding(new Insets(10));
            getChildren().setAll(pagePane);
        }

        ComponentList rootPane = new ComponentList();
        {
            rootPane.getStyleClass().add("no-padding");
            pagePane.getChildren().setAll(rootPane);

            rootPane.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (listView.getSelectionModel().getSelectedItem() != null) {
                        listView.getSelectionModel().clearSelection();
                        e.consume();
                    }
                }
            });
        }
        {
            toolbarPane.disableProperty().bind(getSkinnable().loadingProperty());
            SpinnerPane center = new SpinnerPane();
            {
                ComponentList.setVgrow(center, Priority.ALWAYS);
                center.getStyleClass().add("large-spinner-pane");
                center.loadingProperty().bind(getSkinnable().loadingProperty());
                center.failedReasonProperty().bind(getSkinnable().failedReasonProperty());
                center.onFailedActionProperty().bind(getSkinnable().onFailedActionProperty());

                rootPane.getContent().addAll(toolbarPane, center);
            }
            {
                // ListViewBehavior would consume ESC pressed event, preventing us from handling it, so we ignore it here
                FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
                listView.setCellFactory(listView -> createListCell(getListView()));
                if (getSkinnable().getSelectionType() == CommonListPage.SelectionType.MULTIPLE) {
                    listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                } else {
                    listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
                }
                this.listView.itemsProperty().bind(getSkinnable().itemsProperty());
                center.setContent(listView);
            }
        }
    }

    public void setToolbar(Node toolbar) {
        Node oldToolbar = getToolBar().getCurrentNode();
        if (toolbar != oldToolbar) {
            toolbarPane.setContent(toolbar, ContainerAnimations.FADE);
        }
    }

    public TransitionPane getToolBar() {
        return toolbarPane;
    }

    public JFXListView<T> getListView() {
        return listView;
    }

    public ObservableList<T> getSelectedItems() {
        return listView.getSelectionModel().getSelectedItems();
    }

    public T getSelectedItem() {
        return listView.getSelectionModel().getSelectedItems().get(0);
    }

    public ReadOnlyObjectProperty<T> selectedItemProperty() {
        return listView.getSelectionModel().selectedItemProperty();
    }

    // Override this method to customize the cell rendering.
    // Default: Renders the item as a Node if possible.
    public CommonMDListCell<T> listCell(JFXListView<T> listView) {
        return new CommonMDListCell<>(listView) {

            @Override
            protected void updateControl(T item, boolean empty) {
                if (!empty && item instanceof Node node) {
                    getContainer().getChildren().setAll(node);
                }
            }
        };
    }

    private ListCell<T> createListCell(JFXListView<T> listView) {
        CommonMDListCell<T> commonMDListCell = listCell(listView);
        switch (getSkinnable().getSelectionType()) {
            case SINGLE -> {
                commonMDListCell.addCellEventHandler(MouseEvent.MOUSE_PRESSED, mouseEvent -> handleSingleSelect(commonMDListCell, mouseEvent));
                commonMDListCell.addCellEventHandler(MouseEvent.MOUSE_RELEASED, mouseEvent -> handleSingleRelease(commonMDListCell, mouseEvent));
            }
            case MULTIPLE -> {
                commonMDListCell.addCellEventHandler(MouseEvent.MOUSE_PRESSED, mouseEvent -> handleMultipleSelect(commonMDListCell, mouseEvent));
                commonMDListCell.addCellEventHandler(MouseEvent.MOUSE_RELEASED, mouseEvent -> handleMultipleRelease(commonMDListCell, mouseEvent));
            }
            case NONE -> {
                commonMDListCell.addCellEventHandler(MouseEvent.MOUSE_PRESSED, mouseEvent -> handleNoneSelect(commonMDListCell, mouseEvent));
                commonMDListCell.addCellEventHandler(MouseEvent.MOUSE_RELEASED, mouseEvent -> handleNoneRelease(commonMDListCell, mouseEvent));
            }
        }
        if (getSkinnable().getSelectionType() != CommonListPage.SelectionType.NONE) {
            commonMDListCell.setSelectable();
        }
        return commonMDListCell;
    }

    private void toggleSelect(int index) {
        if (listView.getSelectionModel().isSelected(index)) {
            listView.getSelectionModel().clearSelection(index);
        } else {
            listView.getSelectionModel().select(index);
        }
    }

    private void handleMultipleSelect(ListCell<?> cell, MouseEvent mouseEvent) {
        if (cell.isEmpty()) {
            mouseEvent.consume();
            return;
        }

        int currentIndex = cell.getIndex();
        if (mouseEvent.isSecondaryButtonDown()) {
            requestMenu.set(true);
        } else if (mouseEvent.isShiftDown()) {
            if (listView.getItems().size() >= lastNotShiftClickIndex.get() && lastNotShiftClickIndex.get() >= 0) {
                if (cell.isSelected()) {
                    IntStream.rangeClosed(Math.min(lastNotShiftClickIndex.get(), currentIndex), Math.max(lastNotShiftClickIndex.get(), currentIndex)).forEach(listView.getSelectionModel()::clearSelection);
                } else {
                    listView.getSelectionModel().selectRange(lastNotShiftClickIndex.get(), currentIndex);
                    listView.getSelectionModel().select(currentIndex);
                }
            } else {
                lastNotShiftClickIndex.set(currentIndex);
                listView.getSelectionModel().select(currentIndex);
            }
        } else {
            toggleSelect(cell.getIndex());
            lastNotShiftClickIndex.set(currentIndex);
        }
        cell.requestFocus();
        mouseEvent.consume();
    }

    private void handleSingleSelect(ListCell<?> cell, MouseEvent mouseEvent) {
        if (cell.isEmpty()) {
            mouseEvent.consume();
            return;
        }
        if (mouseEvent.isSecondaryButtonDown()) {
            requestMenu.set(true);
        } else if (cell.isSelected()) {
            listView.getSelectionModel().clearSelection();
        } else {
            listView.getSelectionModel().select(cell.getIndex());
        }
        cell.requestFocus();
        mouseEvent.consume();
    }

    private void handleNoneSelect(ListCell<?> cell, MouseEvent mouseEvent) {
        if (cell.isEmpty()) {
            mouseEvent.consume();
            return;
        }

        if (mouseEvent.isSecondaryButtonDown()) {
            requestMenu.set(true);
        }

        cell.requestFocus();
        mouseEvent.consume();
    }

    private void handleMultipleRelease(ListCell<T> cell, MouseEvent mouseEvent) {
        if (!requestMenu.get()) {
            return;
        }

        switch (getSkinnable().getCellMenuRequestSupportType()) {
            case SINGLE ->
                    getSkinnable().fireEvent(new CommonListPage.CellMenuRequestEvent<>(CommonListPage.CellMenuRequestEvent.SINGLE_CELL, cell, listView));
            case MULTIPLE ->
                    getSkinnable().fireEvent(new CommonListPage.CellMenuRequestEvent<>(CommonListPage.CellMenuRequestEvent.MULTIPLE_CELL, cell, listView));
            case BOTH -> {
                if (listView.getSelectionModel().getSelectedItems().size() > 1) {
                    getSkinnable().fireEvent(new CommonListPage.CellMenuRequestEvent<>(CommonListPage.CellMenuRequestEvent.MULTIPLE_CELL, cell, listView));
                } else {
                    getSkinnable().fireEvent(new CommonListPage.CellMenuRequestEvent<>(CommonListPage.CellMenuRequestEvent.SINGLE_CELL, cell, listView));
                }
            }
        }
        requestMenu.set(false);
    }

    private void handleSingleRelease(ListCell<T> cell, MouseEvent mouseEvent) {
        if (!requestMenu.get()) {
            return;
        }
        getSkinnable().fireEvent(new CommonListPage.CellMenuRequestEvent<>(CommonListPage.CellMenuRequestEvent.SINGLE_CELL, cell, listView));
    }

    private void handleNoneRelease(ListCell<T> cell, MouseEvent mouseEvent) {
        handleSingleRelease(cell, mouseEvent);
    }

    public static Node wrap(Node node) {
        StackPane stackPane = new StackPane(node);
        stackPane.setPadding(new Insets(0, 5, 0, 2));
        return stackPane;
    }

    public static JFXButton createToolbarButton(String text, SVG svg, Runnable onClick, Consumer<JFXButton> initializer) {
        JFXButton ret = new JFXButton(text, wrap(svg.createIcon()));
        ret.getStyleClass().add("jfx-tool-bar-button");
        ret.setOnAction(e -> onClick.run());
        if (initializer != null) {
            initializer.accept(ret);
        }
        return ret;
    }

    public static JFXButton createToolbarButton(String text, SVG svg, Runnable onClick) {
        return createToolbarButton(text, svg, onClick, null);
    }

    public static JFXButton createToolbarButton(String text, SVG svg, BooleanProperty disableProperty, Runnable onClick) {
        return createToolbarButton(text, svg, onClick, jfxButton -> jfxButton.disableProperty().bind(disableProperty));
    }
}
