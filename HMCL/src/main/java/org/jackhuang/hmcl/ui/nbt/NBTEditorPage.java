package org.jackhuang.hmcl.ui.nbt;

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.*;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class NBTEditorPage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state;
    private final File file;
    private final NBTFileType type;

    public NBTEditorPage(File file) throws IOException {
        getStyleClass().add("gray-background");

        this.state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("nbt.title", file.getAbsolutePath())));
        this.file = file;
        this.type = NBTFileType.ofFile(file);

        if (type == null) {
            throw new IOException("Unknown type of file " + file);
        }

        setCenter(new ProgressIndicator());

        HBox actions = new HBox(8);
        actions.setPadding(new Insets(8));
        actions.setAlignment(Pos.CENTER_RIGHT);

        JFXButton saveButton = new JFXButton(i18n("button.save"));
        saveButton.getStyleClass().add("jfx-button-raised");
        saveButton.setButtonType(JFXButton.ButtonType.RAISED);
        saveButton.setOnAction(e -> {
            try {
                save();
            } catch (IOException ex) {
                LOG.warning("Failed to save NBT file", ex);
                Controllers.dialog(i18n("nbt.save.failed") + "\n\n" + StringUtils.getStackTrace(ex));
            }
        });

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("jfx-button-raised");
        cancelButton.setButtonType(JFXButton.ButtonType.RAISED);
        cancelButton.setOnAction(e -> fireEvent(new PageCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        actions.getChildren().setAll(saveButton, cancelButton);

        CompletableFuture.supplyAsync(Lang.wrap(() -> type.readAsTree(file)))
                .thenAcceptAsync(tree -> {
                    setCenter(new NBTTreeView(tree));
                    // setBottom(actions);
                }, Schedulers.javafx())
                .handleAsync((result, e) -> {
                    if (e != null) {
                        LOG.warning("Fail to open nbt file", e);
                        Controllers.dialog(i18n("nbt.open.failed") + "\n\n" + StringUtils.getStackTrace(e), null, MessageDialogPane.MessageType.WARNING, cancelButton::fire);
                    }
                    return null;
                }, Schedulers.javafx());
    }

    public void save() throws IOException {
        // TODO
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }
}
