package org.jackhuang.hmcl.util.versioning;

import java.util.Objects;

public final class VersionRange {
    private static final VersionRange EMPTY = new VersionRange(null, null);
    private static final VersionRange ALL = new VersionRange(null, null);

    public static VersionRange empty() {
        return EMPTY;
    }

    public static VersionRange all() {
        return ALL;
    }

    public static VersionRange between(String minimum, String maximum) {
        return between(VersionNumber.asVersion(minimum), VersionNumber.asVersion(maximum));
    }

    public static VersionRange between(VersionNumber minimum, VersionNumber maximum) {
        assert minimum.compareTo(maximum) <= 0;
        return new VersionRange(minimum, maximum);
    }

    public static VersionRange atLeast(String minimum) {
        return atLeast(VersionNumber.asVersion(minimum));
    }

    public static VersionRange atLeast(VersionNumber minimum) {
        assert minimum != null;
        return new VersionRange(minimum, null);
    }

    public static VersionRange atMost(String maximum) {
        return atMost(VersionNumber.asVersion(maximum));
    }

    public static VersionRange atMost(VersionNumber maximum) {
        assert maximum != null;
        return new VersionRange(null, maximum);
    }

    private final VersionNumber minimum;
    private final VersionNumber maximum;

    private VersionRange(VersionNumber minimum, VersionNumber maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public VersionNumber getMinimum() {
        return minimum;
    }

    public VersionNumber getMaximum() {
        return maximum;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public boolean isAll() {
        return !isEmpty() && minimum == null && maximum == null;
    }

    public boolean contains(String versionNumber) {
        return contains(VersionNumber.asVersion(versionNumber));
    }

    public boolean contains(VersionNumber versionNumber) {
        if (isEmpty()) return false;
        if (isAll()) return true;

        return (minimum == null || minimum.compareTo(versionNumber) <= 0) && (maximum == null || maximum.compareTo(versionNumber) >= 0);
    }

    public boolean isOverlappedBy(final VersionRange that) {
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

    public VersionRange intersectionWith(VersionRange that) {
        if (this.isAll())
            return that;
        if (that.isAll())
            return this;

        if (!isOverlappedBy(that))
            return EMPTY;

        VersionNumber newMinimum;
        if (this.minimum == null)
            newMinimum = that.minimum;
        else if (that.minimum == null)
            newMinimum = this.minimum;
        else
            newMinimum = this.minimum.max(that.minimum);

        VersionNumber newMaximum;
        if (this.maximum == null)
            newMaximum = that.maximum;
        else if (that.maximum == null)
            newMaximum = this.maximum;
        else
            newMaximum = this.maximum.min(that.maximum);

        return new VersionRange(newMinimum, newMaximum);
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

        VersionRange that = (VersionRange) obj;

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
