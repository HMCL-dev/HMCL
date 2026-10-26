// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.ui.Controllers;

/**
 * Indicates a close operation on the dialog.
 *
 * @author yushijinhun
 * @see Controllers#dialog(Region)
 */
public class DialogCloseEvent extends Event {

    public static final EventType<DialogCloseEvent> CLOSE = new EventType<>("DIALOG_CLOSE");

    public DialogCloseEvent() {
        super(CLOSE);
    }

    public DialogCloseEvent(Object source, EventTarget target) {
        super(source, target, CLOSE);
    }

}
