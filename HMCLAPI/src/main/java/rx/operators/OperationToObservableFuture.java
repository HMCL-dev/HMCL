/**
 * Copyright 2013 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.operators;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func1;

public class OperationToObservableFuture {
    private static class ToObservableFuture<T> implements Func1<Observer<T>, Subscription> {
        private final Future<T> that;
        private final Long time;
        private final TimeUnit unit;

        public ToObservableFuture(Future<T> that) {
            this.that = that;
            this.time = null;
            this.unit = null;
        }

        public ToObservableFuture(Future<T> that, long time, TimeUnit unit) {
            this.that = that;
            this.time = time;
            this.unit = unit;
        }

        @Override
        public Subscription call(Observer<T> observer) {
            try {
                T value = (time == null) ? that.get() : that.get(time, unit);

                if (!that.isCancelled()) {
                    observer.onNext(value);
                }
                observer.onCompleted();
            } catch (Exception e) {
                observer.onError(e);
            }

            // the get() has already completed so there is no point in
            // giving the user a way to cancel.
            return Subscriptions.empty();
        }
    }

    public static <T> Func1<Observer<T>, Subscription> toObservableFuture(final Future<T> that) {
        return new ToObservableFuture<>(that);
    }

    public static <T> Func1<Observer<T>, Subscription> toObservableFuture(final Future<T> that, long time, TimeUnit unit) {
        return new ToObservableFuture<>(that, time, unit);
    }
}
