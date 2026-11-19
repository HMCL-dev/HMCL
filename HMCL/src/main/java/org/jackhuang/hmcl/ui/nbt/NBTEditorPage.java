// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.nbt;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.TreeItem;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3TreeView;
import org.glavo.nbt.tag.Tag;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ScrollUtils;
import org.jackhuang.hmcl.ui.construct.MessageType;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author Glavo
 */
public final class NBTEditorPage extends SpinnerPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state;
    private final Path file;
    private final NBTFileType type;

    private final BorderPane root = new BorderPane();

    public NBTEditorPage(Path file) throws IOException {
        getStyleClass().add("gray-background");

        this.state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("nbt.title", file.toString())));
        this.file = file;

        //noinspection DataFlowIssue
        this.type = NBTFileType.ofFile(file);
        if (type == null) {
            throw new IOException("Unknown type of file " + file);
        }

        setContent(root);
        setLoading(true);

        HBox actions = new HBox(8);
        actions.setPadding(new Insets(8));
        actions.setAlignment(Pos.CENTER_RIGHT);

        M3Button saveButton = new M3Button(I18n.i18n("button.save"), M3ButtonVariant.FILLED);
        saveButton.setOnAction(e -> {
            try {
                save();
            } catch (IOException ex) {
                LOG.warning("Failed to save NBT file", ex);
                Controllers.dialog(i18n("nbt.save.failed") + "\n\n" + StringUtils.getStackTrace(ex));
            }
        });

        M3Button cancelButton = new M3Button(I18n.i18n("button.cancel"), M3ButtonVariant.FILLED);
        cancelButton.setOnAction(e -> fireEvent(new PageCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        actions.getChildren().setAll(saveButton, cancelButton);

        Task.supplyAsync(() -> type.read(file))
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        setLoading(false);

                        NBTTreeItem root = new NBTTreeItem(result, FileUtils.getName(file));
                        var view = createTreeView(root);
                        BorderPane.setMargin(view, new Insets(10));
                        onEscPressed(view, cancelButton::fire);
                        this.root.setCenter(view);
                    } else {
                        LOG.warning("Fail to open nbt file", exception);
                        Controllers.dialog(i18n("nbt.open.failed") + "\n\n" + StringUtils.getStackTrace(exception), null, MessageType.WARNING, cancelButton::fire);
                    }
                }).start();
    }

    /// Creates the NBT tree with smooth scrolling and automatically expands single-child tags.
    static M3TreeView<NBTTreeItem.Entry> createTreeView(NBTTreeItem root) {
        var view = new M3TreeView<>(root);
        view.skinProperty().addListener((observable, oldSkin, newSkin) -> {
            if (newSkin != null && view.lookup(".virtual-flow") instanceof VirtualFlow<?> flow)
                ScrollUtils.installSmoothScrolling(flow);
        });
        view.setCellFactory(ignored -> new NBTTreeCell());
        root.addEventHandler(TreeItem.<NBTTreeItem.Entry>branchExpandedEvent(), event -> {
            var item = event.getTreeItem();
            if (item.getValue().element() instanceof Tag && item.getChildren().size() == 1)
                item.getChildren().get(0).setExpanded(true);
        });
        root.setExpanded(true);
        return view;
    }

    public void save() throws IOException {
        // TODO
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }
}
