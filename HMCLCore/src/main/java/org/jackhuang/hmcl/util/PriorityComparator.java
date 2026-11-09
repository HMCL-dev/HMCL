// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/// A comparator that orders elements based on a predefined priority map,
/// falling back to another comparator for elements not present in the map.
///
/// Elements found in the priority map are compared by their associated priority values.
/// When only one of the two elements has a priority, the `prioritizedFirst` flag
/// determines whether the prioritized element is ordered before or after the other.
///
/// @param <T> the type of elements to be compared
public final class PriorityComparator<T> implements Comparator<T> {

    /// Creates a new `PriorityComparator` with a priority map and a fallback comparator.
    /// Elements with a defined priority are ordered before those without.
    public static <T extends Comparable<T>> PriorityComparator<T> of(List<T> values) {
        Objects.requireNonNull(values);
        return of(values, Comparator.naturalOrder(), true);
    }

    /// Creates a new `PriorityComparator` with a priority map, a fallback comparator, and a flag to determine the order of prioritized elements.
    /// Elements with a defined priority are ordered before those without if `prioritizedFirst` is `true`, otherwise they are ordered after.
    public static <T> PriorityComparator<T> of(List<T> values, Comparator<? super T> fallback, boolean prioritizedFirst) {
        Objects.requireNonNull(fallback);

        Map<T, Integer> priorities = new HashMap<>();
        for (int i = 0; i < values.size(); i++)
            priorities.put(values.get(i), i);
        return new PriorityComparator<>(priorities, fallback, prioritizedFirst);
    }

    /// A mapping from values to their priority. Lower values indicate higher priority.
    private final @NotNull Map<T, Integer> priorities;

    /// The fallback comparator used when neither element has a defined priority.
    private final @NotNull Comparator<? super T> fallback;

    /// If `true`, elements with a defined priority are ordered before those without;
    /// if `false`, they are ordered after.
    private final boolean prioritizedFirst;

    /// Creates a new `PriorityComparator`.
    ///
    /// @param priorities       a map from values to their priority (lower value = higher priority)
    /// @param fallback         the comparator to use when neither element has a defined priority
    /// @param prioritizedFirst if `true`, prioritized elements come before non-prioritized ones
    private PriorityComparator(
            Map<T, Integer> priorities,
            Comparator<? super T> fallback,
            boolean prioritizedFirst) {
        this.priorities = priorities;
        this.fallback = fallback;
        this.prioritizedFirst = prioritizedFirst;
    }

    @Override
    public int compare(T value1, T value2) {
        Integer p1 = priorities.get(value1);
        Integer p2 = priorities.get(value2);

        if (p1 != null) {
            if (p2 != null) {
                return p1.compareTo(p2);
            }

            return prioritizedFirst ? -1 : 1;
        } else {
            if (p2 != null) {
                return prioritizedFirst ? 1 : -1;
            } else {
                return fallback.compare(value1, value2);
            }
        }
    }
}
