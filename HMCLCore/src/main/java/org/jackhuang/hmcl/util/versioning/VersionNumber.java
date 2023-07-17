/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.versioning;

import java.math.BigInteger;
import java.util.*;

/**
 * Copied from org.apache.maven.artifact.versioning.ComparableVersion
 * Apache License 2.0
 *
 * Maybe we can migrate to org.jenkins-ci:version-number:1.7?
 * @see <a href="http://maven.apache.org/pom.html#Version_Order_Specification">Specification</a>
 */
public final class VersionNumber implements Comparable<VersionNumber> {

    public static final Comparator<String> VERSION_COMPARATOR = Comparator.comparing(VersionNumber::asVersion);

    public static VersionNumber asVersion(String version) {
        Objects.requireNonNull(version);
        return new VersionNumber(version);
    }

    public static String normalize(String str) {
        return new VersionNumber(str).getCanonical();
    }

    public static boolean isIntVersionNumber(String version) {
        if (version.isEmpty()) {
            return false;
        }

        int idx = 0;
        boolean cont = true;
        do {
            int dotIndex = version.indexOf('.', idx);
            if (dotIndex == idx || dotIndex == version.length() - 1) {
                return false;
            }

            int endIndex;
            if (dotIndex < 0) {
                cont = false;
                endIndex = version.length();
            } else {
                endIndex = dotIndex;
            }

            if (endIndex - idx > 9)
                // Numbers which are larger than 10^10 cannot be stored as integer
                return false;

            for (int i = idx; i < endIndex; i++) {
                char ch = version.charAt(i);
                if (ch < '0' || ch > '9')
                    return false;
            }

            idx = endIndex + 1;
        } while (cont);

        return true;
    }

    private interface Item {
        int LONG_ITEM = 0;
        int BIGINTEGER_ITEM = 1;
        int STRING_ITEM = 2;
        int LIST_ITEM = 3;

        int compareTo(Item item);

        int getType();

        boolean isNull();

        void appendTo(StringBuilder buffer);
    }

    private static final class LongItem implements Item {
        private final long value;

        public static final LongItem ZERO = new LongItem(0L);

        LongItem(long value) {
            this.value = value;
        }

        public int getType() {
            return LONG_ITEM;
        }

        public boolean isNull() {
            return value == 0L;
        }

        public int compareTo(Item item) {
            if (item == null) {
                return value == 0L ? 0 : 1; // 1.0 == 1, 1.1 > 1
            }

            switch (item.getType()) {
                case LONG_ITEM:
                    long itemValue = ((LongItem) item).value;
                    return Long.compare(value, itemValue);
                case BIGINTEGER_ITEM:
                    return -1;

                case STRING_ITEM:
                    return 1; // 1.1 > 1-sp

                case LIST_ITEM:
                    return 1; // 1.1 > 1-1

                default:
                    throw new AssertionError("invalid item: " + item.getClass());
            }
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            buffer.append(value);
        }

        public String toString() {
            return Long.toString(value);
        }
    }

    /**
     * Represents a numeric item in the version item list.
     */
    private static final class BigIntegerItem implements Item {
        private final BigInteger value;

        BigIntegerItem(String str) {
            this.value = new BigInteger(str);
        }

        public int getType() {
            return BIGINTEGER_ITEM;
        }

        public boolean isNull() {
            // Never be 0
            // return BigInteger.ZERO.equals(value);
            return false;
        }

        public int compareTo(Item item) {
            if (item == null) {
                // return BigInteger.ZERO.equals(value) ? 0 : 1; // 1.0 == 1, 1.1 > 1
                return 1;
            }

            switch (item.getType()) {
                case LONG_ITEM:
                    return 1;
                case BIGINTEGER_ITEM:
                    return value.compareTo(((BigIntegerItem) item).value);

                case STRING_ITEM:
                    return 1; // 1.1 > 1-sp

                case LIST_ITEM:
                    return 1; // 1.1 > 1-1

                default:
                    throw new AssertionError("invalid item: " + item.getClass());
            }
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            buffer.append(value);
        }

        public String toString() {
            return value.toString();
        }
    }

    /**
     * Represents a string in the version item list, usually a qualifier.
     */
    private static final class StringItem implements Item {
        private final String value;

        StringItem(String value) {
            this.value = value;
        }

        public int getType() {
            return STRING_ITEM;
        }

        public boolean isNull() {
            return value.isEmpty();
        }

        public int compareTo(Item item) {
            if (item == null) {
                // 1-string > 1
                return 1;
            }
            switch (item.getType()) {
                case LONG_ITEM:
                case BIGINTEGER_ITEM:
                    return -1; // 1.any < 1.1 ?

                case STRING_ITEM:
                    return value.compareTo(((StringItem) item).value);

                case LIST_ITEM:
                    return -1; // 1.any < 1-1

                default:
                    throw new AssertionError("invalid item: " + item.getClass());
            }
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            buffer.append(value);
        }

        public String toString() {
            return value;
        }
    }

    /**
     * Represents a version list item. This class is used both for the global item list and for sub-lists (which start
     * with '-(number)' in the version specification).
     */
    private static final class ListItem extends ArrayList<Item> implements Item {
        private final Character separator;

        ListItem() {
            this.separator = null;
        }

        ListItem(char separator) {
            this.separator = separator;
        }

        public int getType() {
            return LIST_ITEM;
        }

        public boolean isNull() {
            return size() == 0;
        }

        void normalize() {
            for (int i = size() - 1; i >= 0; i--) {
                Item lastItem = get(i);

                if (lastItem.isNull()) {
                    // remove null trailing items: 0, "", empty list
                    remove(i);
                } else if (!(lastItem instanceof ListItem)) {
                    break;
                }
            }
        }

        public int compareTo(Item item) {
            if (item == null) {
                if (size() == 0) {
                    return 0; // 1-0 = 1- (normalize) = 1
                }
                Item first = get(0);
                return first.compareTo(null);
            }
            switch (item.getType()) {
                case LONG_ITEM:
                case BIGINTEGER_ITEM:
                    return -1; // 1-1 < 1.0.x

                case STRING_ITEM:
                    return 1; // 1-1 > 1-sp

                case LIST_ITEM:
                    Iterator<Item> left = iterator();
                    Iterator<Item> right = ((ListItem) item).iterator();

                    while (left.hasNext() || right.hasNext()) {
                        Item l = left.hasNext() ? left.next() : null;
                        Item r = right.hasNext() ? right.next() : null;

                        // if this is shorter, then invert the compare and mul with -1
                        int result = l == null ? (r == null ? 0 : -1 * r.compareTo(l)) : l.compareTo(r);

                        if (result != 0) {
                            return result;
                        }
                    }

                    return 0;

                default:
                    throw new AssertionError("invalid item: " + item.getClass());
            }
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            if (separator != null) {
                buffer.append((char) separator);
            }

            final int initLength = buffer.length();

            for (Item item : this) {
                if (buffer.length() > initLength) {
                    if (!(item instanceof ListItem))
                        buffer.append('.');
                }
                item.appendTo(buffer);
            }
        }

        public String toString() {
            StringBuilder buffer = new StringBuilder();
            appendTo(buffer);
            return buffer.toString();
        }
    }

    private static final int MAX_LONGITEM_LENGTH = 18;

    private final String value;
    public final ListItem items;
    private final String canonical;

    private VersionNumber(String version) {
        this.value = version;

        ListItem list = this.items = new ListItem();

        Deque<Item> stack = new ArrayDeque<>();
        stack.push(list);

        boolean isDigit = false;

        int startIndex = 0;

        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);

            if (c == '.') {
                if (i == startIndex) {
                    list.add(LongItem.ZERO);
                } else {
                    list.add(parseItem(version.substring(startIndex, i)));
                }
                startIndex = i + 1;
            } else if ("!\"#$%&'()*+,-/:;<=>?@[\\]^_`{|}~".indexOf(c) != -1) {
                if (i == startIndex) {
                    list.add(LongItem.ZERO);
                } else {
                    list.add(parseItem(version.substring(startIndex, i)));
                }
                startIndex = i + 1;

                list.add(list = new ListItem(c));
                stack.push(list);
            } else if (c >= '0' && c <= '9') {
                if (!isDigit && i > startIndex) {
                    list.add(parseItem(version.substring(startIndex, i)));
                    startIndex = i;

                    list.add(list = new ListItem());
                    stack.push(list);
                }

                isDigit = true;
            } else {
                if (isDigit && i > startIndex) {
                    list.add(parseItem(version.substring(startIndex, i)));
                    startIndex = i;

                    list.add(list = new ListItem());
                    stack.push(list);
                }

                isDigit = false;
            }
        }

        if (version.length() > startIndex) {
            list.add(parseItem(version.substring(startIndex)));
        }

        while (!stack.isEmpty()) {
            list = (ListItem) stack.pop();
            list.normalize();
        }

        this.canonical = items.toString();
    }

    // For simple version
    private VersionNumber(String version, ListItem items) {
        this.value = version;
        this.items = items;
        this.canonical = version;
    }

    private static Item parseItem(String buf) {
        int numberLength = 0;
        boolean leadingZero = true;
        for (int i = 0; i < buf.length(); i++) {
            char ch = buf.charAt(i);
            if (ch >= '0' && ch <= '9') {
                if (ch != '0') {
                    leadingZero = false;
                }

                if (!leadingZero) {
                    numberLength++;
                }
            } else {
                return new StringItem(buf);
            }
        }

        if (numberLength == 0) {
            return LongItem.ZERO;
        } else if (numberLength <= MAX_LONGITEM_LENGTH) {
            // Numbers which are larger than 10^19 cannot be stored as long
            return new LongItem(Long.parseLong(buf));
        } else {
            return new BigIntegerItem(buf);
        }
    }

    public int compareTo(String o) {
        return compareTo(VersionNumber.asVersion(o));
    }

    @Override
    public int compareTo(VersionNumber o) {
        return items.compareTo(o.items);
    }

    @Override
    public String toString() {
        return value;
    }

    public String getCanonical() {
        return canonical;
    }

    public VersionNumber min(VersionNumber that) {
        return this.compareTo(that) <= 0 ? this : that;
    }

    public VersionNumber max(VersionNumber that) {
        return this.compareTo(that) >= 0 ? this : that;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VersionNumber && canonical.equals(((VersionNumber) o).canonical);
    }

    @Override
    public int hashCode() {
        return canonical.hashCode();
    }
}
