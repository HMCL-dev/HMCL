package net.burningtnt.hmclprs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import javax.swing.*;
import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Hooks {
    private Hooks() {
    }

    public static void onApplicationLaunch() {
        try {
            Optional<ButtonType> opt = new Alert(Alert.AlertType.CONFIRMATION, i18n("prs.warning")).showAndWait();
            if (opt.isPresent() && opt.get() == ButtonType.YES) {
                return;
            }
        } catch (Throwable ignored) {
            if (JOptionPane.showConfirmDialog(
                    null, i18n("prs.warning"), i18n("message.warning"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE
            ) == JOptionPane.OK_OPTION) {
                return;
            }
        }
        System.exit(1);
    }
}
