/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
