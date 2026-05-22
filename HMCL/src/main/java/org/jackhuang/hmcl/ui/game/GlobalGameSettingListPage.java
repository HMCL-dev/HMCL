/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.game;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXRadioButton;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ListCell;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.GameSetting;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ToolbarListPageSkin;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Page for managing global game setting presets.
@NotNullByDefault
public final class GlobalGameSettingListPage extends StackPane implements DecoratorPage {
    /// The page title translation key.
    private static final String TITLE_KEY = "settings.type.global.manage_all";

    /// The selected setting supplier.
    private final Supplier<GameSetting.@Nullable Global> selectedSettingSupplier;

    /// Selects a setting in the owning editor.
    private final Consumer<GameSetting.Global> settingSelector;

    /// Opens the owning editor for a setting.
    private final Consumer<GameSetting.Global> settingEditor;

    /// The page state.
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n(TITLE_KEY)));

    /// The global setting list view.
    private final JFXListView<GameSetting.Global> listView = new JFXListView<>();

    /// The selected setting radio group.
    private final ToggleGroup selectedGroup = new ToggleGroup();

    /// Creates the global game setting list page.
    public GlobalGameSettingListPage(
            Supplier<GameSetting.@Nullable Global> selectedSettingSupplier,
            Consumer<GameSetting.Global> settingSelector,
            Consumer<GameSetting.Global> settingEditor) {
        this.selectedSettingSupplier = selectedSettingSupplier;
        this.settingSelector = settingSelector;
        this.settingEditor = settingEditor;

        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");

        HBox toolbar = new HBox();
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPickOnBounds(false);
        toolbar.getChildren().setAll(ToolbarListPageSkin.createToolbarButton2(
                i18n("settings.type.global.create"),
                SVG.ADD,
                this::createGlobalSetting));
        root.getContent().add(toolbar);

        listView.setPadding(Insets.EMPTY);
        listView.setItems(config().getGameSettings());
        listView.setCellFactory(ignored -> new GlobalSettingListCell());
        listView.getStyleClass().add("no-horizontal-scrollbar");

        SpinnerPane center = new SpinnerPane();
        ComponentList.setVgrow(center, Priority.ALWAYS);
        center.setContent(listView);
        root.getContent().add(center);

        pane.getChildren().setAll(root);
        getChildren().setAll(pane);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /// Returns the display name for a global game setting.
    private static String getGlobalSettingDisplayName(GameSetting.Global setting) {
        return StringUtils.isBlank(setting.nameProperty().getValue())
                ? setting.idProperty().getValue().toString()
                : setting.nameProperty().getValue();
    }

    /// Creates a new global game setting and opens it in the editor.
    private void createGlobalSetting() {
        Controllers.prompt(i18n("settings.type.global.create"), (name, handler) -> {
            if (StringUtils.isBlank(name)) {
                handler.reject(i18n("input.not_empty"));
                return;
            }

            GameSetting.Global setting = new GameSetting.Global();
            setting.nameProperty().setValue(name.trim());
            config().getGameSettings().add(setting);
            settingSelector.accept(setting);
            settingEditor.accept(setting);
            handler.resolve();
        }, i18n("settings.type.global.new"), new RequiredValidator());
    }

    /// List cell for global game settings, matching the instance list row layout.
    private final class GlobalSettingListCell extends ListCell<GameSetting.@UnknownNullability Global> {
        /// The reusable row graphic.
        private final RipplerContainer graphic;

        /// The selected-state radio button.
        private final JFXRadioButton selectedButton;

        /// The row text.
        private final TwoLineListItem content;

        /// Creates a global setting list cell.
        private GlobalSettingListCell() {
            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8, 8, 8, 0));
            root.setCursor(Cursor.HAND);
            this.graphic = new RipplerContainer(root);

            this.selectedButton = new JFXRadioButton();
            selectedButton.setToggleGroup(selectedGroup);
            selectedButton.setOnAction(event -> selectCurrentItem());
            root.setLeft(selectedButton);
            BorderPane.setAlignment(selectedButton, Pos.CENTER);

            HBox center = new HBox();
            center.setMouseTransparent(true);
            center.setPrefWidth(Region.USE_PREF_SIZE);
            center.setAlignment(Pos.CENTER_LEFT);

            this.content = new TwoLineListItem();
            BorderPane.setAlignment(content, Pos.CENTER);
            center.getChildren().setAll(content);
            root.setCenter(center);

            HBox right = new HBox();
            right.setAlignment(Pos.CENTER_RIGHT);

            JFXButton editButton = FXUtils.newToggleButton4(SVG.EDIT, 20);
            editButton.setOnAction(event -> editCurrentItem());
            FXUtils.installFastTooltip(editButton, i18n("settings.type.global.edit"));
            right.getChildren().add(editButton);
            root.setRight(right);

            FXUtils.onClicked(graphic, () -> {
                if (!selectedButton.isSelected()) {
                    selectedButton.fire();
                }
            });
        }

        @Override
        protected void updateItem(@Nullable GameSetting.Global item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            setGraphic(graphic);
            content.setTitle(getGlobalSettingDisplayName(item));
            selectedButton.setSelected(Objects.equals(selectedSettingSupplier.get(), item));
        }

        /// Selects the item represented by this cell.
        private void selectCurrentItem() {
            GameSetting.Global item = getItem();
            if (item == null) {
                return;
            }

            settingSelector.accept(item);
            listView.refresh();
        }

        /// Opens the editor for the item represented by this cell.
        private void editCurrentItem() {
            GameSetting.Global item = getItem();
            if (item == null) {
                return;
            }

            settingSelector.accept(item);
            settingEditor.accept(item);
        }
    }
}
