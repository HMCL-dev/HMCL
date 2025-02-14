package org.jackhuang.hmcl.util.platform;

import org.junit.jupiter.api.Test;

import static org.jackhuang.hmcl.java.JavaInfo.parseVersion;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public final class JavaRuntimeTest {
    @Test
    public void testParseVersion() {
        assertEquals(8, parseVersion("1.8.0_302"));
        assertEquals(11, parseVersion("11"));
        assertEquals(11, parseVersion("11.0.12"));
    }
}
