package org.jackhuang.hmcl.util;

import javafx.beans.property.BooleanProperty;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.List;
import java.util.function.Consumer;

public final class AllSelectPolicy {
    private final BooleanProperty allSelected;

    private final List<BooleanProperty> children;

    private int childSelectedCount = 0;

    private boolean updating = false;

    public AllSelectPolicy(BooleanProperty allSelected, List<BooleanProperty> children) {
        this.allSelected = allSelected;
        this.children = children;
        int itemCount = children.size();

        for (BooleanProperty child : children) {
            if (child.get()) {
                childSelectedCount++;
            }

            FXUtils.onChange(child, wrap(value -> {
                if (value) {
                    childSelectedCount++;
                } else {
                    childSelectedCount--;
                }

                allSelected.set(childSelectedCount == itemCount);
            }));
        }

        allSelected.set(childSelectedCount == itemCount);

        FXUtils.onChange(allSelected, wrap(value -> {
            for (BooleanProperty child : children) {
                child.setValue(value);
            }
            if (value) {
                childSelectedCount = itemCount;
            } else {
                childSelectedCount = 0;
            }
        }));
    }

    private <T> Consumer<T> wrap(Consumer<T> c) {
        return value -> {
            if (!updating) {
                try {
                    updating = true;

                    c.accept(value);
                } finally {
                    updating = false;
                }
            }
        };
    }
}
