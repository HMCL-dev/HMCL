package org.jackhuang.hmcl.util.javafx;

import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;

import java.util.List;
import java.util.function.Consumer;

public final class SelectionBinding {
    private final BooleanProperty allSelected;

    private final List<BooleanProperty> children;

    private int childSelectedCount = 0;

    private boolean updating = false;

    public SelectionBinding(BooleanProperty allSelected, List<BooleanProperty> children) {
        this.allSelected = allSelected;
        this.children = children;
        int itemCount = children.size();

        for (BooleanProperty child : children) {
            if (child.get()) {
                childSelectedCount++;
            }

            onChange(child, wrap(value -> {
                if (value) {
                    childSelectedCount++;
                } else {
                    childSelectedCount--;
                }

                allSelected.set(childSelectedCount == itemCount);
            }));
        }

        allSelected.set(childSelectedCount == itemCount);

        onChange(allSelected, wrap(value -> {
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

    private static <T> void onChange(ObservableValue<T> value, Consumer<T> consumer) {
        value.addListener((a, b, c) -> consumer.accept(c));
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
