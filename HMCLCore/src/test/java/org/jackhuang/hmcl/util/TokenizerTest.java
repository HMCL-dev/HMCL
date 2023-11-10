package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class TokenizerTest {
    private void test(String source, List<String> expected) {
        List<String> result = StringUtils.tokenize(source);
        if (result.size() != expected.size()) {
            fail(result, expected);
        }
        int size = result.size();
        for (int i = 0; i < size; i++) {
            if (!result.get(i).equals(expected.get(i))) {
                fail(result, expected);
            }
        }
    }

    private static void fail(List<String> result, List<String> expected) {
        Assertions.fail(String.format(
                "Unexpected tokenized result. Expected: [%s], Result: [%s].",
                String.join(", ", expected),
                String.join(", ", result)
        ));
    }

    @Test
    public void textTokenizer() {
        test(
                "\"C:/Program Files/Bellsoft/JDK-11/bin.java.exe\" -version \"a.b.c\" something else",
                Arrays.asList("C:/Program Files/Bellsoft/JDK-11/bin.java.exe", "-version", "a.b.c", "something", "else")
        );
        test(
                "\"Another\"Text something else",
                Arrays.asList("AnotherText", "something", "else")
        );
        test(
                "Text without quote",
                Arrays.asList("Text", "without", "quote")
        );
    }
}
