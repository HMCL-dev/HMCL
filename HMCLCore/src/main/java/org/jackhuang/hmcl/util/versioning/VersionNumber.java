/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.StringUtils;

import java.math.BigInteger;
import java.util.*;

/**
 * Copied from org.apache.maven.artifact.versioning.ComparableVersion
 * Apache License 2.0
 */
public class VersionNumber implements Comparable<VersionNumber> {

    public static VersionNumber asVersion(String version) {
        Objects.requireNonNull(version);
        return new VersionNumber(version);
    }

    public static String normalize(String str) {
        return new VersionNumber(str).getCanonical();
    }

    public static boolean isIntVersionNumber(String version) {
        if (version.chars().noneMatch(ch -> ch != '.' && (ch < '0' || ch > '9'))
                && !version.contains("..") && StringUtils.isNotBlank(version)) {
            String[] arr = version.split("\\.");
            for (String str : arr)
                if (str.length() > 9)
                    // Numbers which are larger than 1e9 cannot be stored as integer.
                    return false;
            return true;
        } else {
            return false;
        }
    }

    private String value;
    private String canonical;
    private ListItem items;

    private interface Item {
        int INTEGER_ITEM = 0;
        int STRING_ITEM = 1;
        int LIST_ITEM = 2;

        int compareTo(Item item);

        int getType();

        boolean isNull();
    }

    /**
     * Represents a numeric item in the version item list.
     */
    private static class IntegerItem
            implements Item {
        private final BigInteger value;

        public static final IntegerItem ZERO = new IntegerItem();

        private IntegerItem() {
            this.value = BigInteger.ZERO;
        }

        IntegerItem(String str) {
            this.value = new BigInteger(str);
        }

        public int getType() {
            return INTEGER_ITEM;
        }

        public boolean isNull() {
            return BigInteger.ZERO.equals(value);
        }

        public int compareTo(Item item) {
            if (item == null) {
                return BigInteger.ZERO.equals(value) ? 0 : 1; // 1.0 == 1, 1.1 > 1
            }

            switch (item.getType()) {
                case INTEGER_ITEM:
                    return value.compareTo(((IntegerItem) item).value);

                case STRING_ITEM:
                    return 1; // 1.1 > 1-sp

                case LIST_ITEM:
                    return 1; // 1.1 > 1-1

                default:
                    throw new RuntimeException("invalid item: " + item.getClass());
            }
        }

        public String toString() {
            return value.toString();
        }
    }

    /**
     * Represents a string in the version item list, usually a qualifier.
     */
    private static class StringItem
            implements Item {
        private String value;

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
                case INTEGER_ITEM:
                    return -1; // 1.any < 1.1 ?

                case STRING_ITEM:
                    return value.compareTo(((StringItem) item).value);

                case LIST_ITEM:
                    return -1; // 1.any < 1-1

                default:
                    throw new RuntimeException("invalid item: " + item.getClass());
            }
        }

        public String toString() {
            return value;
        }
    }

    /**
     * Represents a version list item. This class is used both for the global item list and for sub-lists (which start
     * with '-(number)' in the version specification).
     */
    private static class ListItem
            extends ArrayList<Item>
            implements Item {
        Character separator;

        public ListItem() {}

        public ListItem(char separator) {
            this.separator = separator;
        }

        public int getType() {
            return LIST_ITEM;
        }

        public boolean isNull() {
            return (size() == 0);
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
                case INTEGER_ITEM:
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
                    throw new RuntimeException("invalid item: " + item.getClass());
            }
        }

        public String toString() {
            StringBuilder buffer = new StringBuilder();
            for (Item item : this) {
                if (buffer.length() > 0) {
                    if (!(item instanceof ListItem))
                        buffer.append('.');
                }
                buffer.append(item);
            }
            if (separator != null)
                return separator + buffer.toString();
            else
                return buffer.toString();
        }
    }

    public VersionNumber(String version) {
        parseVersion(version);
    }

    private void parseVersion(String version) {
        this.value = version;

        ListItem list = items = new ListItem();

        Stack<Item> stack = new Stack<>();
        stack.push(list);

        boolean isDigit = false;

        int startIndex = 0;

        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);

            if (c == '.') {
                if (i == startIndex) {
                    list.add(IntegerItem.ZERO);
                } else {
                    list.add(parseItem(version.substring(startIndex, i)));
                }
                startIndex = i + 1;
            } else if ("!\"#$%&'()*+,-/:;<=>?@[\\]^_`{|}~".indexOf(c) != -1) {
                if (i == startIndex) {
                    list.add(IntegerItem.ZERO);
                } else {
                    list.add(parseItem(version.substring(startIndex, i)));
                }
                startIndex = i + 1;

                list.add(list = new ListItem(c));
                stack.push(list);
            } else if (Character.isDigit(c)) {
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

        canonical = items.toString();
    }

    private static Item parseItem(String buf) {
        return buf.chars().allMatch(Character::isDigit) ? new IntegerItem(buf) : new StringItem(buf);
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

    @Override
    public boolean equals(Object o) {
        return o instanceof VersionNumber && canonical.equals(((VersionNumber) o).canonical);
    }

    @Override
    public int hashCode() {
        return canonical.hashCode();
    }

    public static Comparator<String> VERSION_COMPARATOR = Comparator.comparing(VersionNumber::asVersion);
}
