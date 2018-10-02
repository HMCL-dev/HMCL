package org.jackhuang.hmcl.ui.construct;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.function.Consumer;

public class ListFirstElementListener<T> implements ListChangeListener<T> {

    private final Consumer<? super T> first, last;

    public ListFirstElementListener(Consumer<? super T> first, Consumer<? super T> last) {
        this.first = first;
        this.last = last;
    }

    @Override
    public void onChanged(Change<? extends T> c) {
        ObservableList<? extends T> list = c.getList();
        if (list.isEmpty()) return;
        while (c.next()) {
            int from = c.getFrom();
            int to = c.getTo();

            if (c.wasPermutated()) {
                for (int i = from; i < to; ++i) {
                    int newIdx = c.getPermutation(i);
                    if (i == 0 && newIdx != 0)
                        last.accept(list.get(newIdx));
                    else if (i != 0 && newIdx == 0)
                        first.accept(list.get(newIdx));
                }
            } else {
                if (c.wasRemoved()) {
                    if (!list.isEmpty() && from == 0)
                        first.accept(list.get(0));
                }
                if (c.wasAdded()) {
                    for (int i = from; i < to; ++i) {
                        if (i == 0)
                            first.accept(list.get(0));
                        else
                            last.accept(list.get(i));
                    }
                    if (list.size() > to)
                        last.accept(list.get(to));
                }
            }
        }
    }

    public static <T> void observe(ObservableList<? extends T> list, Consumer<? super T> first, Consumer<? super T> last) {
        for (int i = 0; i < list.size(); ++i) {
            if (i == 0) first.accept(list.get(i));
            else last.accept(list.get(i));
        }
        list.addListener(new ListFirstElementListener<>(first, last));
    }
}
