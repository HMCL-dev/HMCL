/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.utils;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.function.Function;


/**
 * <h1>JavaFX FX Thread utilities</h1>
 * JFXUtilities allow sync mechanism to the FX thread
 * <p>
 *
 * @author pmoufarrej
 * @version 1.0
 * @since 2016-03-09
 */

public class JFXUtilities {

    /**
     * This method is used to run a specified Runnable in the FX Application thread,
     * it returns before the task finished execution
     *
     * @param doRun This is the sepcifed task to be excuted by the FX Application thread
     * @return Nothing
     */
    public static void runInFX(Runnable doRun) {
        if (Platform.isFxApplicationThread()) {
            doRun.run();
            return;
        }
        Platform.runLater(doRun);
    }

    /**
     * This method is used to run a specified Runnable in the FX Application thread,
     * it waits for the task to finish before returning to the main thread.
     *
     * @param doRun This is the sepcifed task to be excuted by the FX Application thread
     * @return Nothing
     */
    public static void runInFXAndWait(Runnable doRun) {
        if (Platform.isFxApplicationThread()) {
            doRun.run();
            return;
        }
        final CountDownLatch doneLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                doRun.run();
            } finally {
                doneLatch.countDown();
            }
        });
        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static <T> T[] concat(T[] a, T[] b, Function<Integer, T[]> supplier) {
        final int aLen = a.length;
        final int bLen = b.length;
        T[] array = supplier.apply(aLen + bLen);
        System.arraycopy(a, 0, array, 0, aLen);
        System.arraycopy(b, 0, array, aLen, bLen);
        return array;
    }
}
