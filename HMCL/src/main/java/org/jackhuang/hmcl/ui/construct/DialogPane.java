// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.jackhuang.hmcl.ui.FXUtils;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DialogPane extends JFXDialogLayout {
    private final StringProperty title = new SimpleStringProperty();
    private final BooleanProperty valid = new SimpleBooleanProperty(true);
    /// Submits the dialog while its inputs are valid and no request is pending.
    protected final M3Button acceptButton = new M3Button(i18n("button.ok"), M3ButtonVariant.TEXT);
    /// Tracks the pending submission shown on the accept button.
    private final BooleanProperty submitting = FXUtils.newButtonLoadingProperty(acceptButton);
    protected final M3Button cancelButton = new M3Button("", M3ButtonVariant.TEXT);
    protected final Label warningLabel = new Label();

    public DialogPane() {
        Label titleLabel = new Label();
        titleLabel.textProperty().bind(title);
        setHeading(titleLabel);

        acceptButton.setOnAction(e -> onAccept());
        acceptButton.disableProperty().bind(submitting.or(valid.not()));

        cancelButton.setText(i18n("button.cancel"));
        cancelButton.setOnAction(e -> onCancel());
        onEscPressed(this, cancelButton::fire);

        setActions(warningLabel, cancelButton, acceptButton);
    }

    public String getTitle() {
        return title.get();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public boolean isValid() {
        return valid.get();
    }

    public BooleanProperty validProperty() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid.set(valid);
    }

    protected void onCancel() {
        fireEvent(new DialogCloseEvent());
    }

    protected void onAccept() {
        fireEvent(new DialogCloseEvent());
    }

    protected void setLoading() {
        submitting.set(true);
        warningLabel.setText("");
    }

    protected void onSuccess() {
        submitting.set(false);
        fireEvent(new DialogCloseEvent());
    }

    protected void onFailure(String msg) {
        submitting.set(false);
        warningLabel.setText(msg);
    }
}
