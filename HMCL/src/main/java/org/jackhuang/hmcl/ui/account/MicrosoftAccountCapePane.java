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
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService.MinecraftProfileResponseCape;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService.MinecraftServicesRateLimitException;
import org.jackhuang.hmcl.game.CapePreview;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Dialog listing every cape owned by a Microsoft account and letting the user
/// activate one of them or remove the active cape.
///
/// Cape data is always read from the Minecraft Services profile, never from a
/// locally fabricated list. A cape change is debounced and serialized so rapid
/// clicks yield a single `PUT`/`DELETE` instead of hammering the rate-limited
/// Minecraft Services endpoint.
@NotNullByDefault
public final class MicrosoftAccountCapePane extends StackPane {

    /// Idle delay before a pending cape change is actually sent. Rapid clicks
    /// within this window are coalesced into the final selection.
    private static final int CAPE_CHANGE_DEBOUNCE_MILLIS = 400;

    /// Preview display width in the list; the height is derived from the 10:16
    /// cape aspect ratio so the front face is never stretched into a square.
    private static final double CAPE_PREVIEW_WIDTH = 20;
    private static final double CAPE_PREVIEW_HEIGHT = CAPE_PREVIEW_WIDTH / CapePreview.ASPECT_RATIO;

    private final MicrosoftAccount account;
    private final AdvancedListBox listBox = new AdvancedListBox();
    private final SpinnerPane spinnerPane = new SpinnerPane();

    /// Guards against concurrent cape-change requests, so only one
    /// `PUT`/`DELETE` runs at a time.
    private final AtomicBoolean changingCape = new AtomicBoolean(false);

    /// The cape list currently displayed; reused when a `DELETE` returns no body.
    private List<MinecraftProfileResponseCape> currentCapes = List.of();
    /// Whether a debounced cape change is pending.
    private boolean hasPendingChange;
    /// The pending cape id (`null` means "remove the active cape").
    private @Nullable String pendingCapeId;

    /// Debounce timer coalescing rapid selections.
    private final PauseTransition debounce = new PauseTransition(Duration.millis(CAPE_CHANGE_DEBOUNCE_MILLIS));

    public MicrosoftAccountCapePane(MicrosoftAccount account) {
        this.account = account;

        setPrefWidth(480);

        JFXDialogLayout layout = new JFXDialogLayout();
        getChildren().setAll(layout);
        layout.setHeading(new Label(i18n("account.cape.manage")));

        spinnerPane.setContent(listBox);
        spinnerPane.setPrefHeight(360);
        spinnerPane.setOnFailedAction(e -> reload());

        debounce.setOnFinished(e -> runPendingChange());

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        layout.setActions(cancelButton);
        layout.setBody(spinnerPane);

        reload();
    }

    /// Reloads the cape list. Deduplication and rate-limit handling happen inside
    /// [MicrosoftAccount], so no pane-level guard is needed here.
    private void reload() {
        spinnerPane.showSpinner();
        Task.supplyAsync(account::getCapes)
                .whenComplete(Schedulers.javafx(), this::renderCapes)
                .start();
    }

    /// Renders the initial-load result, or the failure reason when loading failed.
    private void renderCapes(@Nullable List<MinecraftProfileResponseCape> capes, @Nullable Exception exception) {
        spinnerPane.hideSpinner();
        if (exception != null) {
            spinnerPane.setFailedReason(Accounts.localizeErrorMessage(exception));
            return;
        }
        renderList(capes == null ? List.of() : capes, false);
    }

    /// Re-renders the list. When `allInactive` is true, every cape is shown as
    /// inactive regardless of its stored state, used for a `DELETE` that returned
    /// no body.
    private void renderList(List<MinecraftProfileResponseCape> capes, boolean allInactive) {
        currentCapes = capes;
        boolean hasActive = !allInactive && capes.stream().anyMatch(cape -> "ACTIVE".equals(cape.state));

        listBox.clear();
        listBox.add(buildNoCapeItem(!hasActive));
        for (MinecraftProfileResponseCape cape : capes) {
            listBox.add(buildCapeItem(cape, !allInactive && "ACTIVE".equals(cape.state)));
        }
    }

    /// Builds the "no cape" entry used to remove the currently active cape.
    private AdvancedListItem buildNoCapeItem(boolean active) {
        AdvancedListItem item = new AdvancedListItem();
        item.setTitle(i18n("account.cape.none"));
        item.setActive(active);
        item.setOnAction(e -> requestCapeChange(null));
        return item;
    }

    /// Builds the list entry for one cape with a lazily loaded preview.
    private AdvancedListItem buildCapeItem(MinecraftProfileResponseCape cape, boolean active) {
        AdvancedListItem item = new AdvancedListItem();
        item.setTitle(StringUtils.isBlank(cape.alias) ? cape.id : cape.alias);
        item.setSubtitle(i18n(active ? "account.cape.active" : "account.cape.inactive"));
        item.setActive(active);
        ImageView preview = createPreview();
        item.setLeftGraphic(preview);
        item.setOnAction(e -> requestCapeChange(cape.id));
        bindPreview(preview, cape.url);
        return item;
    }

    /// Records a cape change request, coalescing rapid clicks through the debounce timer.
    ///
    /// @param capeId the cape to activate, or `null` to remove the active cape
    private void requestCapeChange(@Nullable String capeId) {
        hasPendingChange = true;
        pendingCapeId = capeId;
        debounce.playFromStart();
    }

    /// Runs the pending cape change after the debounce delay expires.
    ///
    /// Factors is ignored while a change is already in flight; the in-flight
    /// completion re-arms the debounce and applies the newest pending selection.
    private void runPendingChange() {
        if (!hasPendingChange || changingCape.get()) {
            return;
        }
        @Nullable String capeId = pendingCapeId;
        if (!changingCape.compareAndSet(false, true)) {
            return;
        }
        hasPendingChange = false;
        applyChange(capeId);
    }

    /// Applies one cape change in the background, then renders the returned state.
    private void applyChange(@Nullable String capeId) {
        spinnerPane.showSpinner();
        Task.supplyAsync(() -> {
                    if (capeId == null) {
                        return account.hideCape();
                    } else {
                        return account.showCape(capeId);
                    }
                })
                .whenComplete(Schedulers.javafx(), (capes, exception) -> {
                    changingCape.set(false);
                    handleChangeResult(capes, exception);
                    if (hasPendingChange) {
                        // A newer selection arrived while this request was in flight.
                        debounce.playFromStart();
                    }
                })
                .start();
    }

    /// Updates the UI from a completed cape change. It never reloads the profile.
    private void handleChangeResult(@Nullable List<MinecraftProfileResponseCape> capes, @Nullable Exception exception) {
        spinnerPane.hideSpinner();
        if (exception != null) {
            // A rate-limited change must not trigger a reload nor auto-apply a
            // pending selection; drop it so the cooldown is not fought against.
            if (exception instanceof MinecraftServicesRateLimitException) {
                clearPendingChange();
            }
            Controllers.showToast(Accounts.localizeErrorMessage(exception));
            return;
        }
        if (capes == null) {
            // DELETE returned no body: keep owned capes, clear ACTIVE locally.
            renderList(currentCapes, true);
        } else {
            renderList(capes, false);
        }
    }

    /// Cancels any queued cape change and stops the debounce timer.
    private void clearPendingChange() {
        hasPendingChange = false;
        pendingCapeId = null;
        debounce.stop();
    }

    /// Creates a sized, aligned preview view for a cape front face.
    private static ImageView createPreview() {
        ImageView view = new ImageView();
        view.setFitWidth(CAPE_PREVIEW_WIDTH);
        view.setFitHeight(CAPE_PREVIEW_HEIGHT);
        view.setSmooth(true);
        view.setPreserveRatio(true);
        BorderPane.setMargin(view, AdvancedListItem.LEFT_ICON_MARGIN);
        BorderPane.setAlignment(view, Pos.CENTER);
        view.setMouseTransparent(true);
        return view;
    }

    /// Loads the cape front-face preview in the background and displays it.
    ///
    /// On any failure (download, decode, size) the view is simply left empty and
    /// the item remains fully functional; the cape can still be selected by its
    /// alias and switched through its `id`.
    private static void bindPreview(ImageView view, @Nullable String url) {
        Task.supplyAsync(() -> CapePreview.load(url))
                .whenComplete(Schedulers.javafx(), (preview, exception) -> {
                    if (exception == null && preview != null) {
                        view.setImage(preview);
                    }
                })
                .start();
    }
}