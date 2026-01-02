/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.nbt;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class EditHistoryManager {
    private final Deque<MacroCommand> undoStack = new ArrayDeque<>();
    private final Deque<MacroCommand> redoStack = new ArrayDeque<>();
    private static final int MAX_HISTORY = 100;

    public void pushAndExecute(@NotNull NBTEditCommand... cmd) {
        MacroCommand macroCmd = new MacroCommand(List.of(cmd));
        pushAndExecute(macroCmd);
    }

    public void pushAndExecute(MacroCommand macroCmd) {
        macroCmd.execute();
        undoStack.push(macroCmd);
        redoStack.clear();
        if (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        MacroCommand macroCmd = undoStack.pop();
        macroCmd.undo();
        redoStack.push(macroCmd);
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        MacroCommand macroCmd = redoStack.pop();
        macroCmd.execute();
        undoStack.push(macroCmd);
    }

    record MacroCommand(List<NBTEditCommand> commands) {
        public void execute() {
            for (NBTEditCommand cmd : commands) {
                cmd.execute();
            }
        }

        public void undo() {
            for (int i = commands.size() - 1; i >= 0; i--) {
                commands.get(i).undo();
            }
        }
    }
}
