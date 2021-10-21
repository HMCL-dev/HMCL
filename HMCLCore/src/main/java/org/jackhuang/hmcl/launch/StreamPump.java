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
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.util.Logging;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Pump the given input stream.
 *
 * @author huangyuhui
 */
public final class StreamPump implements Runnable {

    private final InputStream inputStream;
    private final Consumer<String> callback;
    private final Charset charset;

    public StreamPump(InputStream inputStream) {
        this(inputStream, s -> {});
    }

    public StreamPump(InputStream inputStream, Consumer<String> callback) {
        this.inputStream = inputStream;
        this.callback = callback;
        this.charset = StandardCharsets.UTF_8;
    }

    public StreamPump(InputStream inputStream, Consumer<String> callback, Charset charset) {
        this.inputStream = inputStream;
        this.callback = callback;
        this.charset = charset;
    }

    @Override
    public void run() {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }

                callback.accept(line);
            }
        } catch (IOException e) {
            Logging.LOG.log(Level.SEVERE, "An error occurred when reading stream", e);
        }
    }

}
