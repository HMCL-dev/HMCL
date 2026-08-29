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
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService.MinecraftProfileResponseCape;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Dialog listing every cape owned by a Microsoft account and letting the user
/// activate one of them or remove the active cape.
///
/// Cape data is always read from the Minecraft Services profile, never from a
/// locally fabricated list.
@NotNullByDefault
public final class MicrosoftAccountCapePane extends StackPane {

    private final MicrosoftAccount account;
    private final AdvancedListBox listBox = new AdvancedListBox();
    private final SpinnerPane spinnerPane = new SpinnerPane();

    public MicrosoftAccountCapePane(MicrosoftAccount account) {
        this.account = account;

        setPrefWidth(480);

        JFXDialogLayout layout = new JFXDialogLayout();
        getChildren().setAll(layout);
        layout.setHeading(new Label(i18n("account.cape.manage")));

        spinnerPane.setContent(listBox);
        spinnerPane.setPrefHeight(360);
        spinnerPane.setOnFailedAction(e -> reload());

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        layout.setActions(cancelButton);
        layout.setBody(spinnerPane);

        reload();
    }

    /// Reloads the cape list from the server and re-renders the item list.
    private void reload() {
        spinnerPane.showSpinner();
        Task.supplyAsync(account::getCapes)
                .whenComplete(Schedulers.javafx(), this::renderCapes)
                .start();
    }

    /// Renders the loading result, or the failure reason when loading failed.
    private void renderCapes(@Nullable List<MinecraftProfileResponseCape> capes, @Nullable Exception exception) {
        spinnerPane.hideSpinner();
        if (exception != null) {
            spinnerPane.setFailedReason(Accounts.localizeErrorMessage(exception));
            return;
        }

        boolean hasActiveCape = capes.stream().anyMatch(cape -> "ACTIVE".equals(cape.state));

        listBox.clear();
        listBox.add(buildNoCapeItem(!hasActiveCape));
        for (MinecraftProfileResponseCape cape : capes) {
            listBox.add(buildCapeItem(cape));
        }
    }

    /// Builds the "no cape" entry used to remove the currently active cape.
    private AdvancedListItem buildNoCapeItem(boolean active) {
        AdvancedListItem item = new AdvancedListItem();
        item.setTitle(i18n("account.cape.none"));
        item.setActive(active);
        item.setOnAction(e -> confirmCapeChange(i18n("account.cape.remove.confirm"), () -> applyCape(null)));
        return item;
    }

    /// Builds the list entry for one cape with a lazily loaded preview.
    private AdvancedListItem buildCapeItem(MinecraftProfileResponseCape cape) {
        AdvancedListItem item = new AdvancedListItem();
        item.setTitle(StringUtils.isBlank(cape.alias) ? cape.id : cape.alias);
        if ("ACTIVE".equals(cape.state)) {
            item.setSubtitle(i18n("account.cape.active"));
            item.setActive(true);
        }
        ImageView preview = createPreview();
        item.setLeftGraphic(preview);
        item.setOnAction(e -> confirmCapeChange(i18n("account.cape.set.confirm"), () -> applyCape(cape.id)));
        bindPreview(preview, cape.url);
        return item;
    }

    /// Asks the user to confirm before mutating the active cape, then runs `commit`.
    private void confirmCapeChange(String message, Runnable commit) {
        Controllers.confirm(message, i18n("account.cape.manage"), commit, null);
    }

    /// Applies a cape change in the background and reloads the list afterwards.
    ///
    /// @param capeId the cape to activate, or `null` to remove the active cape
    private void applyCape(@Nullable String capeId) {
        spinnerPane.showSpinner();
        Task.runAsync(() -> {
                    if (capeId == null) {
                        account.hideCape();
                    } else {
                        account.showCape(capeId);
                    }
                })
                .whenComplete(Schedulers.javafx(), exception -> {
                    spinnerPane.hideSpinner();
                    if (exception != null) {
                        Controllers.showToast(Accounts.localizeErrorMessage(exception));
                    }
                    reload();
                })
                .start();
    }

    /// Creates a sized, aligned preview view for a cape texture.
    private static ImageView createPreview() {
        ImageView view = new ImageView();
        view.setFitWidth(AdvancedListItem.LEFT_ICON_SIZE);
        view.setFitHeight(AdvancedListItem.LEFT_ICON_SIZE);
        view.setSmooth(true);
        view.setPreserveRatio(true);
        BorderPane.setMargin(view, AdvancedListItem.LEFT_ICON_MARGIN);
        BorderPane.setAlignment(view, Pos.CENTER);
        view.setMouseTransparent(true);
        return view;
    }

    /// Downloads and displays the cape texture preview in the background.
    private static void bindPreview(ImageView view, String url) {
        Task.supplyAsync(() -> {
                    try {
                        return TexturesLoader.loadTexture(new Texture(url, null));
                    } catch (Throwable e) {
                        LOG.warning("Failed to load cape texture " + url, e);
                        return null;
                    }
                })
                .whenComplete(Schedulers.javafx(), (loadedTexture, exception) -> {
                    if (exception == null && loadedTexture != null) {
                        view.setImage(loadedTexture.image());
                    }
                })
                .start();
    }
}