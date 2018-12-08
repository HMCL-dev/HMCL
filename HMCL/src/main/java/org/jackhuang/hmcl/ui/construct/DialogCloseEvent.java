/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
