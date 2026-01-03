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

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.Tag;
import org.jetbrains.annotations.NotNull;

public interface NBTEditCommand {
    void execute();

    void undo();

    public record EditValueInCompoundTagCommand(@NotNull Tag target, @NotNull CompoundTag father,
                                                @NotNull String tagName,
                                                @NotNull Tag newTag) implements NBTEditCommand {
        @Override
        public void execute() {
            father.put(tagName, newTag);
        }

        @Override
        public void undo() {
            father.remove(tagName);
            father.put(tagName, target);
        }
    }

    public record EditValueInListCommand<T extends Tag>(@NotNull T target, @NotNull ListTag<T> father,
                                                        int index,
                                                        @NotNull T newTag) implements NBTEditCommand {

        @Override
        public void execute() {
            father.set(index, newTag);
        }

        @Override
        public void undo() {
            father.set(index, target);
        }
    }

    public record EditNameInCompoundTagCommand(@NotNull Tag target, @NotNull CompoundTag father,
                                               @NotNull String newName,
                                               @NotNull String oldName) implements NBTEditCommand {

        @Override
        public void execute() {
            father.remove(oldName);
            father.put(newName, target);
        }

        @Override
        public void undo() {
            father.remove(newName);
            father.put(oldName, target);
        }
    }

    public record AddInCompoundTagCommand(@NotNull Tag target, @NotNull CompoundTag father,
                                          @NotNull String name) implements NBTEditCommand {

        @Override
        public void execute() {
            father.put(name, target);
        }

        @Override
        public void undo() {
            father.remove(name);
        }
    }

    public record AddInListCommand<T extends Tag>(@NotNull T target,
                                                  @NotNull ListTag<T> father) implements NBTEditCommand {

        @Override
        public void execute() {
            father.add(target);
        }

        @Override
        public void undo() {
            father.remove(target);
        }
    }

    public record DeleteInCompoundTagCommand(@NotNull Tag target, @NotNull CompoundTag father,
                                             @NotNull String tagName) implements NBTEditCommand {

        @Override
        public void execute() {
            father.remove(tagName);
        }

        @Override
        public void undo() {
            father.put(tagName, target);
        }
    }

    public record DeleteInListCommand<T extends Tag>(@NotNull T target,
                                                     @NotNull ListTag<T> father) implements NBTEditCommand {

        @Override
        public void execute() {
            father.remove(target);
        }

        @Override
        public void undo() {
            father.add(target);
        }
    }
}
