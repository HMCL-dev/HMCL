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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.*;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Displays an instance and provides its launch, management and list-organization actions.
public final class GameListCell extends ListCell<GameListItem> {

    /// Ripple container reused while the list virtualizes its cells.
    private final RipplerContainer graphic;

    /// Instance icon displayed beside its title.
    private final ImageContainer imageView;
    /// Instance title, components, modpack version and organization tags.
    private final TwoLineListItem content;

    /// Selects the instance used by the home-page launch button.
    private final JFXRadioButton chkSelected;
    /// Opens the modpack update action when supported.
    private final JFXButton btnUpgrade;
    /// Starts a test launch.
    private final JFXButton btnLaunch;
    /// Opens the instance action menu.
    private final JFXButton btnManage;

    /// Holds the actions currently available for the instance.
    private final HBox right;

    /// Modpack version bound to the instance currently displayed by this cell.
    private final StringProperty modpackVersion = new SimpleStringProperty("");

    /// Category bound to the current instance, including instances without local settings.
    private final StringProperty category = new SimpleStringProperty("");

    /// Launch-list visibility bound to the current instance.
    private final BooleanProperty shownInLaunchMenu = new SimpleBooleanProperty(true);

    /// Creates a reusable instance row using the standard list controls.
    public GameListCell() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("md-list-cell");
        root.setPadding(new Insets(8, 8, 8, 0));

        RipplerContainer container = new RipplerContainer(root);
        this.graphic = container;

        {
            this.chkSelected = new JFXRadioButton() {
                @Override
                public void fire() {
                    if (!isDisable() && !isSelected()) {
                        fireEvent(new ActionEvent());
                        @Nullable GameListItem item = GameListCell.this.getItem();
                        if (item != null) {
                            item.getRepository().setSelectedInstance(item.getGameInstance());
                        }
                    }
                }
            };
            root.setLeft(chkSelected);
            BorderPane.setAlignment(chkSelected, Pos.CENTER);
        }

        {
            HBox center = new HBox();
            BorderPane.setMargin(center, new Insets(0, 0, 0, 8));
            center.setMouseTransparent(true);
            root.setCenter(center);
            center.setPrefWidth(Region.USE_PREF_SIZE);
            center.setSpacing(8);
            center.setAlignment(Pos.CENTER_LEFT);

            this.imageView = new ImageContainer(32);

            this.content = new TwoLineListItem();
            BorderPane.setAlignment(content, Pos.CENTER);

            FXUtils.onChangeAndOperate(modpackVersion, ignored -> updateTags());
            FXUtils.onChange(category, ignored -> updateTags());
            FXUtils.onChange(shownInLaunchMenu, ignored -> updateTags());

            center.getChildren().setAll(imageView, content);
        }

        {
            this.right = new HBox();
            root.setRight(right);

            right.setAlignment(Pos.CENTER_RIGHT);

            this.btnUpgrade = FXUtils.newToggleButton4(SVG.UPDATE);
            btnUpgrade.setOnAction(e -> {
                @Nullable GameListItem item = this.getItem();
                if (item != null)
                    item.update();
            });
            FXUtils.installFastTooltip(btnUpgrade, i18n("instance.update"));
            right.getChildren().add(btnUpgrade);

            this.btnLaunch = FXUtils.newToggleButton4(SVG.ROCKET_LAUNCH);
            btnLaunch.setOnAction(e -> {
                @Nullable GameListItem item = this.getItem();
                if (item != null)
                    item.testGame();
            });
            BorderPane.setAlignment(btnLaunch, Pos.CENTER);
            FXUtils.installFastTooltip(btnLaunch, i18n("instance.launch.test"));
            right.getChildren().add(btnLaunch);

            this.btnManage = FXUtils.newToggleButton4(SVG.MORE_VERT);
            btnManage.setOnAction(e -> {
                @Nullable GameListItem item = this.getItem();
                if (item == null)
                    return;

                JFXPopup popup = getPopup(item);
                JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(root, popup);
                popup.show(root, vPosition, JFXPopup.PopupHPosition.RIGHT, 0, vPosition == JFXPopup.PopupVPosition.TOP ? root.getHeight() : -root.getHeight());
            });
            BorderPane.setAlignment(btnManage, Pos.CENTER);
            FXUtils.installFastTooltip(btnManage, i18n("settings.game.management"));
            right.getChildren().add(btnManage);
        }

        root.setCursor(Cursor.HAND);
        container.setOnMouseClicked(e -> {
            @Nullable GameListItem item = getItem();
            if (item == null)
                return;

            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 1) {
                    item.modifyGameSettings();
                }
            } else if (e.getButton() == MouseButton.SECONDARY) {
                JFXPopup popup = getPopup(item);
                JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(root, popup);
                popup.show(root, vPosition, JFXPopup.PopupHPosition.LEFT, e.getX(), vPosition == JFXPopup.PopupVPosition.TOP ? e.getY() : e.getY() - root.getHeight());
            }
        });
    }

    /// Rebinds the recycled row to its current instance and refreshes its organization tags.
    @Override
    public void updateItem(@Nullable GameListItem item, boolean empty) {
        @Nullable GameListItem oldItem = getItem();
        boolean oldEmpty = isEmpty();

        super.updateItem(item, empty);

        if (oldItem == item && oldEmpty == empty) return;

        this.graphic.releaseRippleImmediately();

        this.imageView.imageProperty().unbind();
        this.content.titleProperty().unbind();
        this.content.subtitleProperty().unbind();
        this.modpackVersion.unbind();
        this.category.unbind();
        this.shownInLaunchMenu.unbind();
        this.right.getChildren().clear();
        this.chkSelected.selectedProperty().unbind();

        if (empty || item == null) {
            setGraphic(null);
        } else {
            setGraphic(this.graphic);

            this.chkSelected.selectedProperty().bind(item.selectedProperty());
            this.imageView.imageProperty().bind(item.imageProperty());
            this.content.titleProperty().bind(item.titleProperty());
            this.content.subtitleProperty().bind(item.subtitleProperty());
            this.modpackVersion.bind(item.modpackVersionProperty());
            this.category.bind(item.getGameInstance().categoryProperty());
            this.shownInLaunchMenu.bind(item.getGameInstance().shownInLaunchMenuProperty());
            if (item.canUpdate())
                this.right.getChildren().add(btnUpgrade);
            this.right.getChildren().addAll(btnLaunch, btnManage);
        }
        updateTags();
    }

    /// Shows the modpack version, custom category, and launch-menu visibility tag.
    private void updateTags() {
        content.getTags().clear();
        @Nullable GameListItem item = getItem();
        if (isEmpty() || item == null) return;
        @Nullable String version = modpackVersion.get();
        if (StringUtils.isNotBlank(version)) content.addTag(version);
        String categoryName = category.get();
        if (!categoryName.isEmpty()) content.addTag(categoryName);
        if (!shownInLaunchMenu.get()) {
            content.addTag(i18n("instance.launch_menu.hidden"));
        }
    }

    /// Edits category and launch-menu visibility in the launcher's standard prompt dialog.
    private static void editListSettings(GameListItem item) {
        HMCLGameInstance instance = item.getGameInstance();
        PromptDialogPane.Builder.StringQuestion categoryQuestion = new PromptDialogPane.Builder.StringQuestion(
                i18n("instance.category"), instance.getCategory())
                .setPromptText(i18n("instance.category.prompt"));
        PromptDialogPane.Builder.BooleanQuestion visibilityQuestion = new PromptDialogPane.Builder.BooleanQuestion(
                i18n("instance.launch_menu.show"), instance.isShownInLaunchMenu());
        Controllers.prompt(new PromptDialogPane.Builder(i18n("instance.list_settings"), (questions, handler) -> {
            @Nullable HMCLGameInstance current = instance.getRepository().findInstance(instance.getId());
            if (current == null || current.isSettingsReadOnly()) {
                handler.reject(i18n("instance.list_settings.unavailable"));
                return;
            }
            GameSettings.@Nullable Instance localSettings = current.getSettingsOrCreate();
            if (localSettings == null) {
                handler.reject(i18n("instance.list_settings.unavailable"));
                return;
            }
            localSettings.categoryProperty().setValue(categoryQuestion.getValue().strip());
            localSettings.showInLaunchMenuProperty().setValue(visibilityQuestion.getValue());
            Task.runAsync(Schedulers.io(), () -> {
                        FileSaver.waitForAllSaves();
                        current.saveSettingsSync();
                    })
                    .whenComplete(Schedulers.javafx(), (@Nullable Exception exception) -> {
                        if (exception != null) {
                            handler.reject(i18n("instance.list_settings.save_failed"));
                            LOG.warning("Failed to save list settings for instance " + current.getId(), exception);
                        } else {
                            handler.resolve();
                        }
                    }).start();
        }).addQuestion(categoryQuestion).addQuestion(visibilityQuestion));
    }

    /// Creates the instance action menu, including category and launch-menu settings.
    private static JFXPopup getPopup(GameListItem item) {
        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);
        IconedMenuItem listSettingsMenuItem = new IconedMenuItem(
                SVG.TUNE, i18n("instance.list_settings"), () -> editListSettings(item), popup);
        Node listSettingsMenuItemNode = listSettingsMenuItem;
        if (item.getGameInstance().isSettingsReadOnly()) {
            listSettingsMenuItem.setDisable(true);
            // Disabled nodes do not receive hover events, so keep the tooltip on an enabled wrapper.
            StackPane tooltipAnchor = new StackPane(listSettingsMenuItem);
            FXUtils.installFastTooltip(tooltipAnchor, i18n("instance.list_settings.readonly"));
            listSettingsMenuItemNode = tooltipAnchor;
        }

        menu.getContent().setAll(
                new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch.test"), item::testGame, popup),
                new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), item::generateLaunchScript, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.SETTINGS, i18n("instance.manage.manage"), item::modifyGameSettings, popup),
                listSettingsMenuItemNode,
                new MenuSeparator(),
                new IconedMenuItem(SVG.EDIT, i18n("instance.manage.rename"), item::rename, popup),
                new IconedMenuItem(SVG.FOLDER_COPY, i18n("instance.manage.duplicate"), item::duplicate, popup),
                new IconedMenuItem(SVG.DELETE, i18n("instance.manage.remove"), item::remove, popup),
                new IconedMenuItem(SVG.OUTPUT, i18n("modpack.export"), item::export, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.FOLDER_OPEN, i18n("folder.game"), item::browse, popup));
        return popup;
    }
}
