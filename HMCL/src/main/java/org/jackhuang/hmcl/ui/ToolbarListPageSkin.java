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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.function.Predicate;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class ToolbarListPageSkin<E, P extends ListPageBase<E>> extends SkinBase<P> {

    protected final StackPane mainContainer;
    protected final ComponentList rootList;
    protected final JFXListView<E> listView;
    protected FilteredList<E> filteredList;

    protected final TransitionPane toolbarPane;
    protected final HBox normalToolbar;
    protected final HBox selectingToolbar;
    protected final HBox searchBar;
    protected JFXTextField searchField;

    protected final BooleanProperty isSearching = new SimpleBooleanProperty(false);
    protected final BooleanProperty isSelecting = new SimpleBooleanProperty(false);

    public ToolbarListPageSkin(P skinnable, boolean hasSearch) {
        super(skinnable);

        mainContainer = new StackPane();
        mainContainer.setPadding(new Insets(10));
        mainContainer.getStyleClass().addAll("notice-pane");

        rootList = new ComponentList();
        rootList.getStyleClass().add("no-padding");

        listView = new JFXListView<>();
        listView.setPadding(Insets.EMPTY);
        listView.getStyleClass().add("no-horizontal-scrollbar");
        FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

        toolbarPane = new TransitionPane();
        normalToolbar = new HBox();
        selectingToolbar = new HBox();
        searchBar = new HBox();

        if (hasSearch) {
            filteredList = new FilteredList<>(skinnable.getItems());
            listView.setItems(filteredList);

            searchField = new JFXTextField();
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);

            PauseTransition searchPause = new PauseTransition(Duration.millis(100));
            searchPause.setOnFinished(e -> {
                if (filteredList != null) {
                    filteredList.setPredicate(updateSearchPredicate(searchField.getText()));
                }
            });

            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (isSearching.get() || !StringUtils.isBlank(newValue)) {
                    searchPause.setRate(1);
                    searchPause.playFromStart();
                }
            });

            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE, () -> {
                searchField.clear();
                searchPause.stop();
                filteredList.setPredicate(null);
                isSearching.set(false);
            });

            FXUtils.onEscPressed(searchField, closeSearchBar::fire);

            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchBar.getChildren().setAll(searchField, closeSearchBar);
        } else {
            Bindings.bindContent(listView.getItems(), skinnable.itemsProperty());
        }
    }

    protected void setupSkin(Node[] normalBtns, Node[] selectingBtns) {
        if (normalBtns != null && normalBtns.length > 0) {
            normalToolbar.setAlignment(Pos.CENTER_LEFT);
            normalToolbar.setPickOnBounds(false);
            normalToolbar.getChildren().setAll(normalBtns);
        }

        if (selectingBtns != null && selectingBtns.length > 0) {
            selectingToolbar.setAlignment(Pos.CENTER_LEFT);
            selectingToolbar.setPickOnBounds(false);
            selectingToolbar.getChildren().setAll(selectingBtns);
        }

        toolbarPane.setContent(normalToolbar, ContainerAnimations.FADE);
        FXUtils.setOverflowHidden(toolbarPane, 8);
        rootList.getContent().add(toolbarPane);

        rootList.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                if (listView.getSelectionModel().getSelectedItem() != null) {
                    listView.getSelectionModel().clearSelection();
                    e.consume();
                }
            }
        });

        SpinnerPane center = new SpinnerPane();
        ComponentList.setVgrow(center, Priority.ALWAYS);
        center.loadingProperty().bind(getSkinnable().loadingProperty());
        center.failedReasonProperty().bind(getSkinnable().failedReasonProperty());
        center.onFailedActionProperty().bind(getSkinnable().onFailedActionProperty());
        center.setContent(listView);
        rootList.getContent().add(center);

        StackPane placeholderContainer = new StackPane();
        placeholderContainer.getStyleClass().add("notice-pane");
        Label placeholderLabel = new Label();
        placeholderLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            if (isSearching.get()) return i18n("search.no_results_found");
            return getEmptyPlaceholderText();
        }, isSearching));
        placeholderContainer.getChildren().add(placeholderLabel);
        listView.setPlaceholder(placeholderContainer);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            isSelecting.set(newV != null);
        });

        InvalidationListener toolbarSwitchListener = obs -> {
            if (isSelecting.get() && selectingBtns != null && selectingBtns.length > 0) {
                changeToolbar(selectingToolbar);
            } else if (isSearching.get() && hasSearch()) {
                changeToolbar(searchBar);
            } else {
                changeToolbar(normalToolbar);
            }
        };

        isSearching.addListener(toolbarSwitchListener);
        isSelecting.addListener(toolbarSwitchListener);

        toolbarSwitchListener.invalidated(null);

        mainContainer.getChildren().add(rootList);
        getChildren().setAll(mainContainer);
    }

    protected void changeToolbar(HBox newToolbar) {
        Node oldToolbar = toolbarPane.getCurrentNode();
        if (newToolbar != oldToolbar) {
            toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
            if (newToolbar == searchBar && searchField != null) {
                Platform.runLater(searchField::requestFocus);
            }
        }
    }

    protected boolean hasSearch() {
        return searchField != null;
    }

    protected void startSearch() {
        isSearching.set(true);
    }

    protected JFXButton createSelectAllButton() {
        JFXButton selectAll = createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () -> listView.getSelectionModel().selectRange(0, listView.getItems().size()));
        ListChangeListener<Object> listener = change -> {
            selectAll.setDisable(!listView.getItems().isEmpty()
                    && listView.getSelectionModel().getSelectedItems().size() == listView.getItems().size());
        };
        listView.getSelectionModel().getSelectedItems().addListener(listener);
        listView.getItems().addListener(listener);
        return selectAll;
    }

    protected JFXButton createCancelSelectionButton() {
        return createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () -> listView.getSelectionModel().clearSelection());
    }

    protected Predicate<E> updateSearchPredicate(String query) {
        return item -> true;
    }

    protected String getEmptyPlaceholderText() {
        return i18n("mods.empty");
    }

    public static JFXButton createToolbarButton2(String text, SVG svg, Runnable onClick) {
        JFXButton ret = new JFXButton();
        ret.getStyleClass().add("jfx-tool-bar-button");
        ret.setGraphic(svg.createIcon(20));
        ret.setText(text);
        ret.setOnAction(e -> onClick.run());
        return ret;
    }

    public static JFXButton createDecoratorButton(String tooltip, SVG svg, Runnable onClick) {
        JFXButton ret = new JFXButton();
        ret.getStyleClass().add("jfx-decorator-button");
        ret.setGraphic(svg.createIcon(20));
        FXUtils.installFastTooltip(ret, tooltip);
        ret.setOnAction(e -> onClick.run());
        return ret;
    }
}
