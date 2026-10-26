// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.launch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

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
            LOG.error("An error occurred when reading stream", e);
        }
    }

}
