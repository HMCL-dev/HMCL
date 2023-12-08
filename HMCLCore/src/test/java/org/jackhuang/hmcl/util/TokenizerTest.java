package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class TokenizerTest {
    private void test(String source, String... expected) {
        Assertions.assertEquals(Arrays.asList(expected), StringUtils.tokenize(source));
    }

    @Test
    public void textTokenizer() {
        test(
                "\"C:/Program Files/Bellsoft/JDK-11/bin.java.exe\" -version \"a.b.c\" something else",
                "C:/Program Files/Bellsoft/JDK-11/bin.java.exe", "-version", "a.b.c", "something", "else"
        );
        test(
                "\"Another\"Text something else",
                "AnotherText", "something", "else"
        );
        test(
                "Text without quote",
                "Text", "without", "quote"
        );
        test(
                "Text  with  multiple  spaces",
                "Text", "with", "multiple", "spaces"
        );
        test(
                "Text with empty part ''",
                "Text", "with", "empty", "part", ""
        );
        test(
                "head\"abc\\n\\\\\\\"\"end",
                "headabc\n\\\"end"
        );
    }
}
