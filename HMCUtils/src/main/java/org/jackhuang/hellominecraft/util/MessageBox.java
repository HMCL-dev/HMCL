/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.util;

import org.jackhuang.hellominecraft.util.ui.SwingUtils;
import javax.swing.JOptionPane;

/**
 * @author huangyuhui
 */
public class MessageBox {

    private static final String TITLE = C.i18n("message.info");
    /**
     * Buttons: OK
     */
    public static final int DEFAULT_OPTION = -1;
    /**
     * Buttons: Yes No
     */
    public static final int YES_NO_OPTION = 10;
    /**
     * Buttons: Yes No Cancel
     */
    public static final int YES_NO_CANCEL_OPTION = 11;
    /**
     * Buttons: OK Cancel
     */
    public static final int OK_CANCEL_OPTION = 12;
    /**
     * User Operation: Yes
     */
    public static final int YES_OPTION = 0;
    /**
     * User Operation: No
     */
    public static final int NO_OPTION = 1;
    /**
     * User Operation: Cancel
     */
    public static final int CANCEL_OPTION = 2;
    /**
     * User Operation: OK
     */
    public static final int OK_OPTION = 0;
    /**
     * User Operation: Closed Message Box
     */
    public static final int CLOSED_OPTION = -1;
    /**
     * Message Box Type: Error
     */
    public static final int ERROR_MESSAGE = 0;
    /**
     * Message Box Type: Info
     */
    public static final int INFORMATION_MESSAGE = 1;
    /**
     * Message Box Type: Warning
     */
    public static final int WARNING_MESSAGE = 2;
    /**
     * Message Box Type: Question
     */
    public static final int QUESTION_MESSAGE = 3;
    /**
     * Message Box Type: Plain
     */
    public static final int PLAIN_MESSAGE = -1;

    /**
     * Show MsgBox with title and options
     *
     * @param Msg    The Message
     * @param Title  The title of MsgBox.
     * @param Option The type of MsgBox.
     *
     * @return user operation.
     */
    public static int show(String Msg, String Title, int Option) {
        switch (Option) {
        case YES_NO_OPTION:
        case YES_NO_CANCEL_OPTION:
        case OK_CANCEL_OPTION:
            return SwingUtils.invokeAndWait(() -> JOptionPane.showConfirmDialog(null, Msg, Title, Option - 10));
        default:
            SwingUtils.invokeAndWait(() -> JOptionPane.showMessageDialog(null, Msg, Title, Option));
        }
        return 0;
    }

    /**
     * Show MsgBox with options
     *
     * @param Msg    The Message
     * @param Option The type of MsgBox.
     *
     * @return User Operation
     */
    public static int show(String Msg, int Option) {
        return show(Msg, TITLE, Option);
    }

    /**
     * Show Default MsgBox
     *
     * @param Msg The Message
     *
     * @return User Operation
     */
    public static int show(String Msg) {
        return show(Msg, TITLE, INFORMATION_MESSAGE);
    }

    public static int showLocalized(String msg) {
        return show(C.i18n(msg));
    }
}
