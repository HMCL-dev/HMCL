package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class KeyValuePairUtilsTest {
    @Test
    public void test() throws IOException {
        String content = """
                #test: key0=value0
                \s
                key1=value1
                key2="value2"
                key3="\\" \\n"
                """;

        Map<String, String> properties = KeyValuePairUtils.loadProperties(new BufferedReader(new StringReader(content)));

        assertEquals(Lang.mapOf(
                pair("key1", "value1"),
                pair("key2", "value2"),
                pair("key3", "\" \n")
        ), properties);
    }
}
