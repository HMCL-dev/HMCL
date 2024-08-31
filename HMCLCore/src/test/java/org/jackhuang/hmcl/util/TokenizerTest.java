package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class TokenizerTest {

    @Test
    public void textTokenizer() {
        assertEquals(
                Arrays.asList("C:/Program Files/Bellsoft/JDK-11/bin.java.exe", "-version", "a.b.c", "something", "else"),
                StringUtils.tokenize("\"C:/Program Files/Bellsoft/JDK-11/bin.java.exe\" -version \"a.b.c\" something else")
        );
        assertEquals(
                Arrays.asList("AnotherText", "something", "else"),
                StringUtils.tokenize("\"Another\"Text something else")
        );
        assertEquals(
                Arrays.asList("Text", "without", "quote"),
                StringUtils.tokenize("Text without quote")
        );
        assertEquals(
                Arrays.asList("Text", "with", "multiple", "spaces"),
                StringUtils.tokenize("Text  with  multiple  spaces")
        );
        assertEquals(
                Arrays.asList("Text", "with", "empty", "part", ""),
                StringUtils.tokenize("Text with empty part ''")
        );
        assertEquals(
                Arrays.asList("headabc\n`\"$end"),
                StringUtils.tokenize("head\"abc`n```\"\"$end")
        );

        String instName = "1.20.4";
        String instDir = "C:\\Program Files (x86)\\Minecraft\\";

        Map<String, String> env = new HashMap<>();
        env.put("INST_NAME", instName);
        env.put("INST_DIR", instDir);
        env.put("EMPTY", "");

        assertEquals(
                Arrays.asList("cd", instDir),
                StringUtils.tokenize("cd $INST_DIR", env)
        );
        assertEquals(
                Arrays.asList("Text", "with", "empty", "part", ""),
                StringUtils.tokenize("Text with empty part $EMPTY", env)
        );
        assertEquals(
                Arrays.asList("head", "1.20.4", "$UNKNOWN", instDir, "", instDir + instName + "$UNKNOWN" + instDir + "$INST_DIR\n$UNKNOWN $$"),
                StringUtils.tokenize("head $INST_NAME $UNKNOWN $INST_DIR $EMPTY $INST_DIR$INST_NAME$UNKNOWN\"$INST_DIR`$INST_DIR`n$UNKNOWN $EMPTY$\"$", env)
        );
    }
}
