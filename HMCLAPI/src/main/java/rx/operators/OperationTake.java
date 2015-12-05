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

import java.util.concurrent.atomic.AtomicInteger;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.AtomicObservableSubscription;
import rx.util.functions.Func1;

/**
 * Returns a specified number of contiguous values from the start of an observable sequence.
 */
public final class OperationTake {

    /**
     * Returns a specified number of contiguous values from the start of an observable sequence.
     * 
     * @param items
     * @param num
     * @return
     */
    public static <T> Func1<Observer<T>, Subscription> take(final Observable<T> items, final int num) {
        // wrap in a Func so that if a chain is built up, then asynchronously subscribed to twice we will have 2 instances of Take<T> rather than 1 handing both, which is not thread-safe.
        return new Take<>(items, num)::call;
    }

    /**
     * This class is NOT thread-safe if invoked and referenced multiple times. In other words, don't subscribe to it multiple times from different threads.
     * <p>
     * It IS thread-safe from within it while receiving onNext events from multiple threads.
     * <p>
     * This should all be fine as long as it's kept as a private class and a new instance created from static factory method above.
     * <p>
     * Note how the take() factory method above protects us from a single instance being exposed with the Observable wrapper handling the subscribe flow.
     * 
     * @param <T>
     */
    private static class Take<T> implements Func1<Observer<T>, Subscription> {
        private final AtomicInteger counter = new AtomicInteger();
        private final Observable<T> items;
        private final int num;
        private final AtomicObservableSubscription subscription = new AtomicObservableSubscription();

        private Take(Observable<T> items, int num) {
            this.items = items;
            this.num = num;
        }

        @Override
        public Subscription call(Observer<T> observer) {
            if (num < 1) {
                items.subscribe(new Observer<T>()
                {
                    @Override
                    public void onCompleted()
                    {
                    }

                    @Override
                    public void onError(Exception e)
                    {
                    }

                    @Override
                    public void onNext(T args)
                    {
                    }
                }).unsubscribe();
                observer.onCompleted();
                return Subscriptions.empty();
            }

            return subscription.wrap(items.subscribe(new ItemObserver(observer)));
        }

        private class ItemObserver implements Observer<T> {
            private final Observer<T> observer;

            public ItemObserver(Observer<T> observer) {
                this.observer = observer;
            }

            @Override
            public void onCompleted() {
                if (counter.getAndSet(num) < num) {
                    observer.onCompleted();
                }
            }

            @Override
            public void onError(Exception e) {
                if (counter.getAndSet(num) < num) {
                    observer.onError(e);
                }
            }

            @Override
            public void onNext(T args) {
                final int count = counter.incrementAndGet();
                if (count <= num) {
                    observer.onNext(args);
                    if (count == num) {
                        observer.onCompleted();
                    }
                }
                if (count >= num) {
                    // this will work if the sequence is asynchronous, it will have no effect on a synchronous observable
                    subscription.unsubscribe();
                }
            }

        }

    }
}
