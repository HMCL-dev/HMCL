package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public final class InfiniteSizeList<T> extends ArrayList<T> {
    private int actualSize;

    public InfiniteSizeList(int initialCapacity) {
        super(initialCapacity);

        this.actualSize = 0;
    }

    public InfiniteSizeList() {
        this.actualSize = 0;
    }

    public InfiniteSizeList(@NotNull Collection<? extends T> c) {
        super(c);

        this.actualSize = 0;
        for (int i = super.size() - 1; i >= 0; i--) {
            if (super.get(i) != null) {
                actualSize = i + 1;
                break;
            }
        }
    }

    @Override
    public T get(int index) {
        if (index >= super.size()) {
            return null;
        }

        return super.get(index);
    }

    @Override
    public T set(int index, T element) {
        if (element == null) { // The element is null.
            if (index >= super.size()) {
                return null; // The element (actually null) is out of the allocated size.
            }

            T previous = super.get(index);
            if (previous != null) { // !null -> null
                super.set(index, null);

                if (index == this.actualSize - 1) { // Recalculate the actualSize.
                    this.actualSize = 0;

                    for (int i = index - 1; i >= 0; i--) {
                        if (super.get(i) != null) {
                            this.actualSize = i + 1;
                            break;
                        }
                    }
                }

                return previous;
            } else { // null -> null
                return null;
            }
        } else {
            // The element isn't null.

            if (index >= super.size()) {
                allocate0(index);
            }

            T previous = super.get(index);
            if (previous != null) { // !null -> !null
                super.set(index, element);
                return previous;
            } else { // null -> !null
                super.set(index, element);
                if (index >= this.actualSize) {
                    this.actualSize = index + 1;
                }
                return null;
            }
        }
    }

    private void allocate0(int index) {
        super.ensureCapacity(index + 1);
    }

    @Override
    public int size() {
        return actualSize;
    }
}
