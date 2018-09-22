/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.util.Logging;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Pump the given input stream.
 *
 * @author huangyuhui
 */
final class StreamPump implements Runnable {

    private final InputStream inputStream;
    private final Consumer<String> callback;

    public StreamPump(InputStream inputStream) {
        this(inputStream, s -> {
        });
    }

    public StreamPump(InputStream inputStream, Consumer<String> callback) {
        this.inputStream = inputStream;
        this.callback = callback;
    }

    @Override
    public void run() {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()))) {
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
