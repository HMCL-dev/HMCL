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

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class LoggerTest {
    private static final String TOKEN = "the_token";

    @Test
    public void checkTokenFence() throws IOException {
        test("001122334455the_token667788", TOKEN);

        {
            char[] data = new char[1050];
            TOKEN.getChars(0, TOKEN.length(), data, 1020);
            test(data, TOKEN);
        }
    }

    private void test(String data, String token) throws IOException {
        test0(new StringReader(data), token);
    }

    private void test(char[] data, String token) throws IOException {
        test0(new CharArrayReader(data), token);
    }

    private void test0(Reader reader, String token) throws IOException {
        Logger.registerAccessToken(token);

        try (StringWriter writer = new StringWriter()) {
            Logger.filterForbiddenToken(reader, writer);

            assertEquals(writer.getBuffer().indexOf(token), -1);
            assertNotEquals(writer.getBuffer().indexOf("<access token>"), -1);
        }
    }
}
