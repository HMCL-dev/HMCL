package org.jackhuang.hmcl.util.versioning;

import org.junit.jupiter.api.Test;

import static org.jackhuang.hmcl.util.versioning.VersionRange.*;
import static org.junit.jupiter.api.Assertions.*;

public class VersionRangeTest {

    @Test
    public void testContains() {
        assertTrue(between("10", "20").contains("10"));
        assertTrue(between("10", "20").contains("15"));
        assertTrue(between("10", "20").contains("20"));
        assertFalse(between("10", "20").contains("5"));
        assertFalse(between("10", "20").contains("25"));

        assertTrue(between("10", "10").contains("10"));
        assertFalse(between("10", "10").contains("5"));
        assertFalse(between("10", "10").contains("15"));

        assertTrue(atLeast("10").contains("10"));
        assertTrue(atLeast("10").contains("20"));
        assertFalse(atLeast("10").contains("5"));

        assertTrue(atMost("10").contains("10"));
        assertTrue(atMost("10").contains("5"));
        assertFalse(atMost("10").contains("20"));

        assertFalse(empty().contains("0"));
        assertFalse(empty().contains("10"));

        assertTrue(all().contains("0"));
        assertTrue(all().contains("10"));

        assertFalse(all().contains((String) null));
        assertFalse(empty().contains((String) null));
        assertFalse(between("0", "10").contains((String) null));
        assertFalse(atLeast("10").contains((String) null));
        assertFalse(atMost("10").contains((String) null));
        assertFalse(all().contains((VersionNumber) null));
        assertFalse(empty().contains((VersionNumber) null));
        assertFalse(between("0", "10").contains((VersionNumber) null));
        assertFalse(atLeast("10").contains((VersionNumber) null));
        assertFalse(atMost("10").contains((VersionNumber) null));
    }

    private static void assertIsOverlappedBy(boolean value, VersionRange range1, VersionRange range2) {
        assertEquals(value, range1.isOverlappedBy(range2));
        assertEquals(value, range2.isOverlappedBy(range1));
    }

    @Test
    public void testIsOverlappedBy() {
        assertIsOverlappedBy(true, all(), all());
        assertIsOverlappedBy(false, all(), empty());
        assertIsOverlappedBy(false, empty(), empty());

        assertIsOverlappedBy(true, all(), between("10", "20"));
        assertIsOverlappedBy(true, all(), atLeast("10"));
        assertIsOverlappedBy(true, all(), atMost("10"));

        assertIsOverlappedBy(false, empty(), between("10", "20"));
        assertIsOverlappedBy(false, empty(), atLeast("10"));
        assertIsOverlappedBy(false, empty(), atMost("10"));

        assertIsOverlappedBy(true, between("10", "20"), between("10", "20"));
        assertIsOverlappedBy(true, between("10", "20"), between("5", "20"));
        assertIsOverlappedBy(true, between("10", "20"), between("5", "15"));
        assertIsOverlappedBy(true, between("10", "20"), between("5", "10"));
        assertIsOverlappedBy(false, between("10", "20"), between("5", "5"));
        assertIsOverlappedBy(true, between("10", "20"), between("10", "30"));
        assertIsOverlappedBy(true, between("10", "20"), between("15", "30"));
        assertIsOverlappedBy(true, between("10", "20"), between("20", "30"));
        assertIsOverlappedBy(false, between("10", "20"), between("21", "30"));
        assertIsOverlappedBy(true, between("10", "20"), atLeast("5"));
        assertIsOverlappedBy(true, between("10", "20"), atLeast("10"));
        assertIsOverlappedBy(true, between("10", "20"), atLeast("15"));
        assertIsOverlappedBy(true, between("10", "20"), atLeast("20"));
        assertIsOverlappedBy(false, between("10", "20"), atLeast("25"));

        assertIsOverlappedBy(true, atLeast("10"), atLeast("10"));
        assertIsOverlappedBy(true, atLeast("10"), atLeast("20"));
        assertIsOverlappedBy(true, atLeast("10"), atLeast("5"));
        assertIsOverlappedBy(true, atLeast("10"), atMost("10"));
        assertIsOverlappedBy(true, atLeast("10"), atMost("20"));
        assertIsOverlappedBy(false, atLeast("10"), atMost("5"));
    }

    private static void assertIntersectionWith(VersionRange range1, VersionRange range2, VersionRange result) {
        assertEquals(result, range1.intersectionWith(range2));
        assertEquals(result, range2.intersectionWith(range1));
    }

    @Test
    public void testIntersectionWith() {
        assertIntersectionWith(all(), all(), all());
        assertIntersectionWith(all(), empty(), empty());
        assertIntersectionWith(all(), between("10", "20"), between("10", "20"));
        assertIntersectionWith(all(), atLeast("10"), atLeast("10"));
        assertIntersectionWith(all(), atMost("10"), atMost("10"));

        assertIntersectionWith(empty(), empty(), empty());
        assertIntersectionWith(empty(), between("10", "20"), empty());
        assertIntersectionWith(empty(), atLeast("10"), empty());
        assertIntersectionWith(empty(), atMost("10"), empty());

        assertIntersectionWith(between("10", "20"), between("10", "20"), between("10", "20"));
        assertIntersectionWith(between("10", "20"), between("5", "20"), between("10", "20"));
        assertIntersectionWith(between("10", "20"), between("10", "25"), between("10", "20"));
        assertIntersectionWith(between("10", "20"), between("5", "25"), between("10", "20"));
        assertIntersectionWith(between("10", "20"), between("15", "20"), between("15", "20"));
        assertIntersectionWith(between("10", "20"), between("10", "15"), between("10", "15"));
        assertIntersectionWith(between("10", "20"), between("14", "16"), between("14", "16"));
        assertIntersectionWith(between("10", "20"), atLeast("5"), between("10", "20"));
        assertIntersectionWith(between("10", "20"), atLeast("10"), between("10", "20"));
        assertIntersectionWith(between("10", "20"), atLeast("15"), between("15", "20"));
        assertIntersectionWith(between("10", "20"), atLeast("20"), between("20", "20"));
        assertIntersectionWith(between("10", "20"), atLeast("25"), empty());
        assertIntersectionWith(between("10", "20"), atMost("25"), between("10", "20"));
        assertIntersectionWith(between("10", "20"), atMost("20"), between("10", "20"));
        assertIntersectionWith(between("10", "20"), atMost("15"), between("10", "15"));
        assertIntersectionWith(between("10", "20"), atMost("10"), between("10", "10"));
        assertIntersectionWith(between("10", "20"), atMost("5"), empty());

        assertIntersectionWith(atLeast("10"), atMost("10"), between("10", "10"));
        assertIntersectionWith(atLeast("10"), atMost("20"), between("10", "20"));
        assertIntersectionWith(atLeast("10"), atMost("5"), empty());
    }
}
