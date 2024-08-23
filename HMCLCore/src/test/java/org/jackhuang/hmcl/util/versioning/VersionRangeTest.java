package org.jackhuang.hmcl.util.versioning;

import org.junit.jupiter.api.Test;

import static org.jackhuang.hmcl.util.versioning.VersionRange.*;
import static org.junit.jupiter.api.Assertions.*;

public class VersionRangeTest {

    @Test
    public void testContains() {
        VersionRange<VersionNumber> empty = VersionRange.empty();
        VersionRange<VersionNumber> all = all();

        assertTrue(VersionNumber.between("10", "20").contains(VersionNumber.asVersion("10")));
        assertTrue(VersionNumber.between("10", "20").contains(VersionNumber.asVersion("15")));
        assertTrue(VersionNumber.between("10", "20").contains(VersionNumber.asVersion("20")));
        assertFalse(VersionNumber.between("10", "20").contains(VersionNumber.asVersion("5")));
        assertFalse(VersionNumber.between("10", "20").contains(VersionNumber.asVersion("25")));

        assertTrue(VersionNumber.between("10", "10").contains(VersionNumber.asVersion("10")));
        assertFalse(VersionNumber.between("10", "10").contains(VersionNumber.asVersion("5")));
        assertFalse(VersionNumber.between("10", "10").contains(VersionNumber.asVersion("15")));

        assertTrue(VersionNumber.atLeast("10").contains(VersionNumber.asVersion("10")));
        assertTrue(VersionNumber.atLeast("10").contains(VersionNumber.asVersion("20")));
        assertFalse(VersionNumber.atLeast("10").contains(VersionNumber.asVersion("5")));

        assertTrue(VersionNumber.atMost("10").contains(VersionNumber.asVersion("10")));
        assertTrue(VersionNumber.atMost("10").contains(VersionNumber.asVersion("5")));
        assertFalse(VersionNumber.atMost("10").contains(VersionNumber.asVersion("20")));

        assertFalse(empty.contains(VersionNumber.asVersion("0")));
        assertFalse(empty.contains(VersionNumber.asVersion("10")));

        assertTrue(all.contains(VersionNumber.asVersion("0")));
        assertTrue(all.contains(VersionNumber.asVersion("10")));

        assertFalse(all.contains(null));
        assertFalse(empty.contains( null));
        assertFalse(VersionNumber.between("0", "10").contains(null));
        assertFalse(VersionNumber.atLeast("10").contains(null));
        assertFalse(VersionNumber.atMost("10").contains(null));
        assertFalse(all.contains(null));
        assertFalse(empty.contains(null));
        assertFalse(VersionNumber.between("0", "10").contains(null));
        assertFalse(VersionNumber.atLeast("10").contains(null));
        assertFalse(VersionNumber.atMost("10").contains(null));
    }

    private static void assertIsOverlappedBy(boolean value, VersionRange<VersionNumber> range1, VersionRange<VersionNumber> range2) {
        assertEquals(value, range1.isOverlappedBy(range2));
        assertEquals(value, range2.isOverlappedBy(range1));
    }

    @Test
    public void testIsOverlappedBy() {
        assertIsOverlappedBy(true, all(), all());
        assertIsOverlappedBy(false, all(), empty());
        assertIsOverlappedBy(false, empty(), empty());

        assertIsOverlappedBy(true, all(), VersionNumber.between("10", "20"));
        assertIsOverlappedBy(true, all(), VersionNumber.atLeast("10"));
        assertIsOverlappedBy(true, all(), VersionNumber.atMost("10"));

        assertIsOverlappedBy(false, empty(), VersionNumber.between("10", "20"));
        assertIsOverlappedBy(false, empty(), VersionNumber.atLeast("10"));
        assertIsOverlappedBy(false, empty(), VersionNumber.atMost("10"));

        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.between("10", "20"));
        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.between("5", "20"));
        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.between("5", "15"));
        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.between("5", "10"));
        assertIsOverlappedBy(false, VersionNumber.between("10", "20"), VersionNumber.between("5", "5"));
        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.between("10", "30"));
        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.between("15", "30"));
        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.between("20", "30"));
        assertIsOverlappedBy(false, VersionNumber.between("10", "20"), VersionNumber.between("21", "30"));
        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.atLeast("5"));
        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.atLeast("10"));
        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.atLeast("15"));
        assertIsOverlappedBy(true, VersionNumber.between("10", "20"), VersionNumber.atLeast("20"));
        assertIsOverlappedBy(false, VersionNumber.between("10", "20"), VersionNumber.atLeast("25"));

        assertIsOverlappedBy(true, VersionNumber.atLeast("10"), VersionNumber.atLeast("10"));
        assertIsOverlappedBy(true, VersionNumber.atLeast("10"), VersionNumber.atLeast("20"));
        assertIsOverlappedBy(true, VersionNumber.atLeast("10"), VersionNumber.atLeast("5"));
        assertIsOverlappedBy(true, VersionNumber.atLeast("10"), VersionNumber.atMost("10"));
        assertIsOverlappedBy(true, VersionNumber.atLeast("10"), VersionNumber.atMost("20"));
        assertIsOverlappedBy(false, VersionNumber.atLeast("10"), VersionNumber.atMost("5"));
    }

    private static void assertIntersectionWith(VersionRange<VersionNumber> range1, VersionRange<VersionNumber> range2, VersionRange<VersionNumber> result) {
        assertEquals(result, range1.intersectionWith(range2));
        assertEquals(result, range2.intersectionWith(range1));
    }

    @Test
    public void testIntersectionWith() {
        assertIntersectionWith(all(), all(), all());
        assertIntersectionWith(all(), empty(), empty());
        assertIntersectionWith(all(), VersionNumber.between("10", "20"), VersionNumber.between("10", "20"));
        assertIntersectionWith(all(), VersionNumber.atLeast("10"), VersionNumber.atLeast("10"));
        assertIntersectionWith(all(), VersionNumber.atMost("10"), VersionNumber.atMost("10"));

        assertIntersectionWith(empty(), empty(), empty());
        assertIntersectionWith(empty(), VersionNumber.between("10", "20"), empty());
        assertIntersectionWith(empty(), VersionNumber.atLeast("10"), empty());
        assertIntersectionWith(empty(), VersionNumber.atMost("10"), empty());

        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.between("10", "20"), VersionNumber.between("10", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.between("5", "20"), VersionNumber.between("10", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.between("10", "25"), VersionNumber.between("10", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.between("5", "25"), VersionNumber.between("10", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.between("15", "20"), VersionNumber.between("15", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.between("10", "15"), VersionNumber.between("10", "15"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.between("14", "16"), VersionNumber.between("14", "16"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.atLeast("5"), VersionNumber.between("10", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.atLeast("10"), VersionNumber.between("10", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.atLeast("15"), VersionNumber.between("15", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.atLeast("20"), VersionNumber.between("20", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.atLeast("25"), empty());
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.atMost("25"), VersionNumber.between("10", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.atMost("20"), VersionNumber.between("10", "20"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.atMost("15"), VersionNumber.between("10", "15"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.atMost("10"), VersionNumber.between("10", "10"));
        assertIntersectionWith(VersionNumber.between("10", "20"), VersionNumber.atMost("5"), empty());

        assertIntersectionWith(VersionNumber.atLeast("10"), VersionNumber.atMost("10"), VersionNumber.between("10", "10"));
        assertIntersectionWith(VersionNumber.atLeast("10"), VersionNumber.atMost("20"), VersionNumber.between("10", "20"));
        assertIntersectionWith(VersionNumber.atLeast("10"), VersionNumber.atMost("5"), empty());
    }
}
