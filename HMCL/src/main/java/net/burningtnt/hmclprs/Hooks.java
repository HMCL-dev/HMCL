package net.burningtnt.hmclprs;

import javax.swing.*;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Hooks {
    private Hooks() {
    }

    public static void onApplicationLaunch() {
        if (JOptionPane.showConfirmDialog(
                null, i18n("prs.warning"), i18n("message.warning"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.OK_OPTION) {
            return;
        }
        System.exit(1);
    }
}
