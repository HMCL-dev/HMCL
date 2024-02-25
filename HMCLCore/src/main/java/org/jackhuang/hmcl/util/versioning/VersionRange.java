package org.jackhuang.hmcl.util.versioning;

import java.util.Objects;

/**
 * @author Glavo
 */
@SuppressWarnings("unchecked")
public final class VersionRange<T extends Comparable<T>> {
    private static final VersionRange<?> EMPTY = new VersionRange<>(null, null);
    private static final VersionRange<?> ALL = new VersionRange<>(null, null);

    public static <T extends Comparable<T>> VersionRange<T> empty() {
        return (VersionRange<T>) EMPTY;
    }

    public static <T extends Comparable<T>> VersionRange<T> all() {
        return (VersionRange<T>) ALL;
    }

    public static <T extends Comparable<T>> VersionRange<T> between(T minimum, T maximum) {
        assert minimum.compareTo(maximum) <= 0;
        return new VersionRange<>(minimum, maximum);
    }

    public static <T extends Comparable<T>> VersionRange<T> atLeast(T minimum) {
        assert minimum != null;
        return new VersionRange<>(minimum, null);
    }

    public static <T extends Comparable<T>> VersionRange<T> atMost(T maximum) {
        assert maximum != null;
        return new VersionRange<>(null, maximum);
    }

    private final T minimum;
    private final T maximum;

    private VersionRange(T minimum, T maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public T getMinimum() {
        return minimum;
    }

    public T getMaximum() {
        return maximum;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public boolean isAll() {
        return !isEmpty() && minimum == null && maximum == null;
    }

    public boolean contains(T versionNumber) {
        if (versionNumber == null) return false;
        if (isEmpty()) return false;
        if (isAll()) return true;

        return (minimum == null || minimum.compareTo(versionNumber) <= 0) && (maximum == null || maximum.compareTo(versionNumber) >= 0);
    }

    public boolean isOverlappedBy(final VersionRange<T> that) {
        if (this.isEmpty() || that.isEmpty())
            return false;

        if (this.isAll() || that.isAll())
            return true;

        if (this.minimum == null)
            return that.minimum == null || that.minimum.compareTo(this.maximum) <= 0;

        if (this.maximum == null)
            return that.maximum == null || that.maximum.compareTo(this.minimum) >= 0;

        return that.contains(minimum) || that.contains(maximum) || (that.minimum != null && contains(that.minimum));
    }

    public VersionRange<T> intersectionWith(VersionRange<T> that) {
        if (this.isAll())
            return that;
        if (that.isAll())
            return this;

        if (!isOverlappedBy(that))
            return empty();

        T newMinimum;
        if (this.minimum == null)
            newMinimum = that.minimum;
        else if (that.minimum == null)
            newMinimum = this.minimum;
        else
            newMinimum = this.minimum.compareTo(that.minimum) >= 0 ? this.minimum : that.minimum;

        T newMaximum;
        if (this.maximum == null)
            newMaximum = that.maximum;
        else if (that.maximum == null)
            newMaximum = this.maximum;
        else
            newMaximum = this.maximum.compareTo(that.maximum) <= 0 ? this.maximum : that.maximum;

        return new VersionRange<>(newMinimum, newMaximum);
    }

    @Override
    public int hashCode() {
        if (isEmpty())
            return 1121763849;  // Magic Number
        if (isAll())
            return -475303149;  // Magic Number

        return Objects.hash(minimum) ^ Objects.hash(maximum);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof VersionRange))
            return false;

        VersionRange<T> that = (VersionRange<T>) obj;

        return this.isEmpty() == that.isEmpty() && this.isAll() == that.isAll()
                && Objects.equals(this.minimum, that.minimum)
                && Objects.equals(this.maximum, that.maximum);
    }

    @Override
    public String toString() {
        if (isEmpty())
            return "EMPTY";

        if (isAll())
            return "ALL";

        if (minimum == null)
            return "At most " + maximum;

        if (maximum == null)
            return "At least " + minimum;

        return "[" + minimum + ".." + maximum + "]";
    }
}
