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
package org.jackhuang.hmcl.util.logger;

import org.jackhuang.hmcl.util.logging.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoggerTest {
    @Test
    public void checkTokenFence() throws IOException {
        for (String s : new String[]{
                "a_token", "the_token", "another_token"
        }) {
            Logger.registerAccessToken(s);
        }

        test("a_token001122334455the_token667788another_token");

        {
            char[] data = new char[1050];
            "the_token".getChars(0, "the_token".length(), data, 1020);
            "another_token".getChars(0, "another_token".length(), data, 1035);
            test(data);
        }
    }

    private void test(char[] data) throws IOException {
        test(new String(data));
    }

    private void test(String data) throws IOException {
        try (StringWriter writer = new StringWriter()) {
            Logger.filterForbiddenToken(new StringReader(data), writer);

            assertEquals(Logger.filterForbiddenToken(data), writer.toString());
        }
    }
}
