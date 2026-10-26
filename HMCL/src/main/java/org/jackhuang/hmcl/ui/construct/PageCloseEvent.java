// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

/**
 * Indicates a close operation on the navigator page.
 *
 * @author huangyuhui
 */
public class PageCloseEvent extends Event {

    public static final EventType<PageCloseEvent> CLOSE = new EventType<>("PAGE_CLOSE");

    public PageCloseEvent() {
        super(CLOSE);
    }

    public PageCloseEvent(Object source, EventTarget target) {
        super(source, target, CLOSE);
    }

}
