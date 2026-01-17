package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
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
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public abstract class CommonListPageSkin<T>  extends SkinBase<CommonListPage<T>> {

    private final JFXListView<T> listView = new JFXListView<>();
    private final TransitionPane toolbarPane = new TransitionPane();

    private final AtomicInteger lastShiftClickIndex = new AtomicInteger(-1);
    private final AtomicBoolean requestMenu = new AtomicBoolean(false);
    private final Consumer<Integer> toggleSelect;

    public CommonListPageSkin(CommonListPage<T> skinnable) {
        super(skinnable);

        toggleSelect = i -> {
            if (listView.getSelectionModel().isSelected(i)) {
                listView.getSelectionModel().clearSelection(i);
            } else {
                listView.getSelectionModel().select(i);
            }
        };

        StackPane pane = new StackPane();
        {
            pane.setPadding(new Insets(10));
            getChildren().setAll(pane);
        }

        ComponentList root = new ComponentList();
        {
            root.getStyleClass().add("no-padding");
            pane.getChildren().setAll(root);

            root.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (listView.getSelectionModel().getSelectedItem() != null) {
                        listView.getSelectionModel().clearSelection();
                        e.consume();
                    }
                }
            });
        }
        {
            toolbarPane.disableProperty().bind(skinnable.loadingProperty());
            SpinnerPane center = new SpinnerPane();
            {
                ComponentList.setVgrow(center, Priority.ALWAYS);
                center.getStyleClass().add("large-spinner-pane");
                center.loadingProperty().bind(skinnable.loadingProperty());
                center.failedReasonProperty().bind(skinnable.failedReasonProperty());
                center.onFailedActionProperty().bind(skinnable.onFailedActionProperty());

                root.getContent().addAll(toolbarPane, center);
            }
            {
                // ListViewBehavior would consume ESC pressed event, preventing us from handling it, so we ignore it here
                FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
                listView.setCellFactory(listView -> createListCell(getListView()));
                listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                Bindings.bindContent(this.listView.getItems(), skinnable.itemsProperty());
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

    public ObservableList<T> getSelectedItem() {
        return listView.getSelectionModel().getSelectedItems();
    }

    public ReadOnlyObjectProperty<T> selectedItemProperty() {
        return listView.getSelectionModel().selectedItemProperty();
    }

    public abstract MDListCell<T> listCell(JFXListView<T> listView);

    private ListCell<T> createListCell(JFXListView<T> listView) {
        MDListCell<T> mdListCell = listCell(listView);
        mdListCell.addCellEventHandler(MouseEvent.MOUSE_PRESSED, mouseEvent -> handleSelect(mdListCell, mouseEvent));
        mdListCell.addCellEventHandler(MouseEvent.MOUSE_RELEASED, mouseEvent -> handleRelease(mdListCell, mouseEvent));
        return mdListCell;
    }

    private void handleSelect(ListCell<?> cell, MouseEvent mouseEvent) {
        if (cell.isEmpty()) {
            mouseEvent.consume();
            return;
        }

        if (mouseEvent.isSecondaryButtonDown()) {
            requestMenu.set(true);
        } else if (mouseEvent.isShiftDown()) {
            int currentIndex = cell.getIndex();
            if (lastShiftClickIndex.get() == -1) {
                lastShiftClickIndex.set(currentIndex);
                toggleSelect.accept(cell.getIndex());
            } else if (listView.getItems().size() >= lastShiftClickIndex.get() && !(lastShiftClickIndex.get() < -1)) {
                if (cell.isSelected()) {
                    IntStream.rangeClosed(
                                    Math.min(lastShiftClickIndex.get(), currentIndex),
                                    Math.max(lastShiftClickIndex.get(), currentIndex))
                            .forEach(listView.getSelectionModel()::clearSelection);
                } else {
                    listView.getSelectionModel().selectRange(lastShiftClickIndex.get(), currentIndex);
                    listView.getSelectionModel().select(currentIndex);
                }
                lastShiftClickIndex.set(-1);
            } else {
                lastShiftClickIndex.set(currentIndex);
                listView.getSelectionModel().select(currentIndex);
            }
        } else {
            toggleSelect.accept(cell.getIndex());
        }
        cell.requestFocus();
        mouseEvent.consume();
    }

    private void handleRelease(ListCell<T> cell, MouseEvent mouseEvent) {
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

}
