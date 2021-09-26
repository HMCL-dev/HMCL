/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import java.util.Comparator;
import java.util.Objects;

public class Range<T> {


    @SuppressWarnings({"rawtypes", "unchecked"})
    private enum ComparableComparator implements Comparator {
        INSTANCE;

        /**
         * Comparable based compare implementation.
         *
         * @param obj1 left hand side of comparison
         * @param obj2 right hand side of comparison
         * @return negative, 0, positive comparison value
         */
        @Override
        public int compare(final Object obj1, final Object obj2) {
            return ((Comparable) obj1).compareTo(obj2);
        }
    }

    /**
     * Serialization version.
     *
     * @see java.io.Serializable
     */
    private static final long serialVersionUID = 1L;

    /**
     * <p>Obtains a range with the specified minimum and maximum values (both inclusive).</p>
     *
     * <p>The range uses the natural ordering of the elements to determine where
     * values lie in the range.</p>
     *
     * <p>The arguments may be passed in the order (min,max) or (max,min).
     * The getMinimum and getMaximum methods will return the correct values.</p>
     *
     * @param <T>           the type of the elements in this range
     * @param fromInclusive the first value that defines the edge of the range, inclusive
     * @param toInclusive   the second value that defines the edge of the range, inclusive
     * @return the range object, not null
     * @throws IllegalArgumentException if either element is null
     * @throws ClassCastException       if the elements are not {@code Comparable}
     */
    public static <T extends Comparable<T>> Range<T> between(final T fromInclusive, final T toInclusive) {
        return between(fromInclusive, toInclusive, null);
    }

    /**
     * <p>Obtains a range with the specified minimum and maximum values (both inclusive).</p>
     *
     * <p>The range uses the specified {@code Comparator} to determine where
     * values lie in the range.</p>
     *
     * <p>The arguments may be passed in the order (min,max) or (max,min).
     * The getMinimum and getMaximum methods will return the correct values.</p>
     *
     * @param <T>           the type of the elements in this range
     * @param fromInclusive the first value that defines the edge of the range, inclusive
     * @param toInclusive   the second value that defines the edge of the range, inclusive
     * @param comparator    the comparator to be used, null for natural ordering
     * @return the range object, not null
     * @throws IllegalArgumentException if either element is null
     * @throws ClassCastException       if using natural ordering and the elements are not {@code Comparable}
     */
    public static <T> Range<T> between(final T fromInclusive, final T toInclusive, final Comparator<T> comparator) {
        return new Range<>(fromInclusive, toInclusive, comparator);
    }

    /**
     * <p>Obtains a range using the specified element as both the minimum
     * and maximum in this range.</p>
     *
     * <p>The range uses the natural ordering of the elements to determine where
     * values lie in the range.</p>
     *
     * @param <T>     the type of the elements in this range
     * @param element the value to use for this range, not null
     * @return the range object, not null
     * @throws IllegalArgumentException if the element is null
     * @throws ClassCastException       if the element is not {@code Comparable}
     */
    public static <T extends Comparable<T>> Range<T> is(final T element) {
        return between(element, element, null);
    }

    /**
     * <p>Obtains a range using the specified element as both the minimum
     * and maximum in this range.</p>
     *
     * <p>The range uses the specified {@code Comparator} to determine where
     * values lie in the range.</p>
     *
     * @param <T>        the type of the elements in this range
     * @param element    the value to use for this range, must not be {@code null}
     * @param comparator the comparator to be used, null for natural ordering
     * @return the range object, not null
     * @throws IllegalArgumentException if the element is null
     * @throws ClassCastException       if using natural ordering and the elements are not {@code Comparable}
     */
    public static <T> Range<T> is(final T element, final Comparator<T> comparator) {
        return between(element, element, comparator);
    }

    /**
     * The ordering scheme used in this range.
     */
    private final Comparator<T> comparator;

    /**
     * Cached output hashCode (class is immutable).
     */
    private transient int hashCode;

    /**
     * The maximum value in this range (inclusive).
     */
    private final T maximum;

    /**
     * The minimum value in this range (inclusive).
     */
    private final T minimum;

    /**
     * Cached output toString (class is immutable).
     */
    private transient String toString;

    /**
     * Creates an instance.
     *
     * @param element1 the first element, not null
     * @param element2 the second element, not null
     * @param comp     the comparator to be used, null for natural ordering
     */
    @SuppressWarnings("unchecked")
    private Range(final T element1, final T element2, final Comparator<T> comp) {
        if (element1 == null || element2 == null) {
            throw new IllegalArgumentException("Elements in a range must not be null: element1=" +
                    element1 + ", element2=" + element2);
        }
        if (comp == null) {
            this.comparator = ComparableComparator.INSTANCE;
        } else {
            this.comparator = comp;
        }
        if (this.comparator.compare(element1, element2) < 1) {
            this.minimum = element1;
            this.maximum = element2;
        } else {
            this.minimum = element2;
            this.maximum = element1;
        }
    }

    public boolean isEmpty() {
        return comparator.compare(minimum, maximum) > 0;
    }

    /**
     * <p>Checks whether the specified element occurs within this range.</p>
     *
     * @param element the element to check for, null returns false
     * @return true if the specified element occurs within this range
     */
    public boolean contains(final T element) {
        if (element == null) {
            return false;
        }
        return comparator.compare(element, minimum) > -1 && comparator.compare(element, maximum) < 1;
    }

    /**
     * <p>Checks whether this range contains all the elements of the specified range.</p>
     *
     * <p>This method may fail if the ranges have two different comparators or element types.</p>
     *
     * @param otherRange the range to check, null returns false
     * @return true if this range contains the specified range
     * @throws RuntimeException if ranges cannot be compared
     */
    public boolean containsRange(final Range<T> otherRange) {
        if (otherRange == null) {
            return false;
        }
        return contains(otherRange.minimum)
                && contains(otherRange.maximum);
    }

    /**
     * <p>Checks where the specified element occurs relative to this range.</p>
     *
     * <p>The API is reminiscent of the Comparable interface returning {@code -1} if
     * the element is before the range, {@code 0} if contained within the range and
     * {@code 1} if the element is after the range. </p>
     *
     * @param element the element to check for, not null
     * @return -1, 0 or +1 depending on the element's location relative to the range
     */
    public int elementCompareTo(final T element) {
        // Comparable API says throw NPE on null
        Objects.requireNonNull(element);
        if (isAfter(element)) {
            return -1;
        }
        if (isBefore(element)) {
            return 1;
        }
        return 0;
    }

    // Element tests
    //--------------------------------------------------------------------

    /**
     * <p>Compares this range to another object to test if they are equal.</p>.
     *
     * <p>To be equal, the minimum and maximum values must be equal, which
     * ignores any differences in the comparator.</p>
     *
     * @param obj the reference object with which to compare
     * @return true if this object is equal
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        @SuppressWarnings("unchecked") // OK because we checked the class above
        final Range<T> range = (Range<T>) obj;
        return minimum.equals(range.minimum) &&
                maximum.equals(range.maximum);
    }

    /**
     * <p>Gets the comparator being used to determine if objects are within the range.</p>
     *
     * <p>Natural ordering uses an internal comparator implementation, thus this
     * method never returns null. See {@link #isNaturalOrdering()}.</p>
     *
     * @return the comparator being used, not null
     */
    public Comparator<T> getComparator() {
        return comparator;
    }

    /**
     * <p>Gets the maximum value in this range.</p>
     *
     * @return the maximum value in this range, not null
     */
    public T getMaximum() {
        return maximum;
    }

    /**
     * <p>Gets the minimum value in this range.</p>
     *
     * @return the minimum value in this range, not null
     */
    public T getMinimum() {
        return minimum;
    }

    /**
     * <p>Gets a suitable hash code for the range.</p>
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        int result = hashCode;
        if (hashCode == 0) {
            result = 17;
            result = 37 * result + getClass().hashCode();
            result = 37 * result + minimum.hashCode();
            result = 37 * result + maximum.hashCode();
            hashCode = result;
        }
        return result;
    }

    /**
     * Calculate the intersection of {@code this} and an overlapping Range.
     *
     * @param other overlapping Range
     * @return range representing the intersection of {@code this} and {@code other} ({@code this} if equal)
     * @throws IllegalArgumentException if {@code other} does not overlap {@code this}
     * @since 3.0.1
     */
    public Range<T> intersectionWith(final Range<T> other) {
        if (!this.isOverlappedBy(other)) {
            throw new IllegalArgumentException(String.format(
                    "Cannot calculate intersection with non-overlapping range %s", other));
        }
        if (this.equals(other)) {
            return this;
        }
        final T min = getComparator().compare(minimum, other.minimum) < 0 ? other.minimum : minimum;
        final T max = getComparator().compare(maximum, other.maximum) < 0 ? maximum : other.maximum;
        return between(min, max, getComparator());
    }

    /**
     * <p>Checks whether this range is after the specified element.</p>
     *
     * @param element the element to check for, null returns false
     * @return true if this range is entirely after the specified element
     */
    public boolean isAfter(final T element) {
        if (element == null) {
            return false;
        }
        return comparator.compare(element, minimum) < 0;
    }

    /**
     * <p>Checks whether this range is completely after the specified range.</p>
     *
     * <p>This method may fail if the ranges have two different comparators or element types.</p>
     *
     * @param otherRange the range to check, null returns false
     * @return true if this range is completely after the specified range
     * @throws RuntimeException if ranges cannot be compared
     */
    public boolean isAfterRange(final Range<T> otherRange) {
        if (otherRange == null) {
            return false;
        }
        return isAfter(otherRange.maximum);
    }

    /**
     * <p>Checks whether this range is before the specified element.</p>
     *
     * @param element the element to check for, null returns false
     * @return true if this range is entirely before the specified element
     */
    public boolean isBefore(final T element) {
        if (element == null) {
            return false;
        }
        return comparator.compare(element, maximum) > 0;
    }

    /**
     * <p>Checks whether this range is completely before the specified range.</p>
     *
     * <p>This method may fail if the ranges have two different comparators or element types.</p>
     *
     * @param otherRange the range to check, null returns false
     * @return true if this range is completely before the specified range
     * @throws RuntimeException if ranges cannot be compared
     */
    public boolean isBeforeRange(final Range<T> otherRange) {
        if (otherRange == null) {
            return false;
        }
        return isBefore(otherRange.minimum);
    }

    /**
     * <p>Checks whether this range ends with the specified element.</p>
     *
     * @param element the element to check for, null returns false
     * @return true if the specified element occurs within this range
     */
    public boolean isEndedBy(final T element) {
        if (element == null) {
            return false;
        }
        return comparator.compare(element, maximum) == 0;
    }

    /**
     * <p>Whether or not the Range is using the natural ordering of the elements.</p>
     *
     * <p>Natural ordering uses an internal comparator implementation, thus this
     * method is the only way to check if a null comparator was specified.</p>
     *
     * @return true if using natural ordering
     */
    public boolean isNaturalOrdering() {
        return comparator == ComparableComparator.INSTANCE;
    }

    /**
     * <p>Checks whether this range is overlapped by the specified range.</p>
     *
     * <p>Two ranges overlap if there is at least one element in common.</p>
     *
     * <p>This method may fail if the ranges have two different comparators or element types.</p>
     *
     * @param otherRange the range to test, null returns false
     * @return true if the specified range overlaps with this
     * range; otherwise, {@code false}
     * @throws RuntimeException if ranges cannot be compared
     */
    public boolean isOverlappedBy(final Range<T> otherRange) {
        if (otherRange == null) {
            return false;
        }
        return otherRange.contains(minimum)
                || otherRange.contains(maximum)
                || contains(otherRange.minimum);
    }

    /**
     * <p>Checks whether this range starts with the specified element.</p>
     *
     * @param element the element to check for, null returns false
     * @return true if the specified element occurs within this range
     */
    public boolean isStartedBy(final T element) {
        if (element == null) {
            return false;
        }
        return comparator.compare(element, minimum) == 0;
    }

    /**
     * <p>
     * Fits the given element into this range by returning the given element or, if out of bounds, the range minimum if
     * below, or the range maximum if above.
     * </p>
     * <pre>
     * Range&lt;Integer&gt; range = Range.between(16, 64);
     * range.fit(-9) --&gt;  16
     * range.fit(0)  --&gt;  16
     * range.fit(15) --&gt;  16
     * range.fit(16) --&gt;  16
     * range.fit(17) --&gt;  17
     * ...
     * range.fit(63) --&gt;  63
     * range.fit(64) --&gt;  64
     * range.fit(99) --&gt;  64
     * </pre>
     *
     * @param element the element to check for, not null
     * @return the minimum, the element, or the maximum depending on the element's location relative to the range
     * @since 3.10
     */
    public T fit(final T element) {
        Objects.requireNonNull(element);
        if (isAfter(element)) {
            return minimum;
        }
        if (isBefore(element)) {
            return maximum;
        }
        return element;
    }

    /**
     * <p>Gets the range as a {@code String}.</p>
     *
     * <p>The format of the String is '[<i>min</i>..<i>max</i>]'.</p>
     *
     * @return the {@code String} representation of this range
     */
    @Override
    public String toString() {
        if (toString == null) {
            toString = "[" + minimum + ".." + maximum + "]";
        }
        return toString;
    }

    /**
     * <p>Formats the receiver using the given format.</p>
     *
     * <p>This uses {@link java.util.Formattable} to perform the formatting. Three variables may
     * be used to embed the minimum, maximum and comparator.
     * Use {@code %1$s} for the minimum element, {@code %2$s} for the maximum element
     * and {@code %3$s} for the comparator.
     * The default format used by {@code toString()} is {@code [%1$s..%2$s]}.</p>
     *
     * @param format the format string, optionally containing {@code %1$s}, {@code %2$s} and  {@code %3$s}, not null
     * @return the formatted string, not null
     */
    public String toString(final String format) {
        return String.format(format, minimum, maximum, comparator);
    }

}
