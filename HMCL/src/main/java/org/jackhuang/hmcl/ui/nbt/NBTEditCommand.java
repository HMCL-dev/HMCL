package org.jackhuang.hmcl.ui.nbt;

import com.github.steveice10.opennbt.tag.builtin.Tag;

public interface NBTEditCommand {
    void execute();

    void undo();

    public record EditValueCommand(Tag target, String newValue, String oldValue) implements NBTEditCommand {

        @Override
        public void execute() {

        }

        @Override
        public void undo() {

        }
    }

    public record EditNameCommand(Tag target, String newName, String oldName) implements NBTEditCommand {

        @Override
        public void execute() {

        }

        @Override
        public void undo() {

        }
    }
}