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

import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.AtomicObservableSubscription;
import rx.util.functions.Func1;

/**
 * Returns a specified number of contiguous elements from the end of an
 * observable sequence.
 */
public final class OperationTakeLast {

    public static <T> Func1<Observer<T>, Subscription> takeLast(final Observable<T> items, final int count) {
        return new TakeLast<>(items, count)::call;
    }

    private static class TakeLast<T> implements Func1<Observer<T>, Subscription> {

        private final int count;
        private final Observable<T> items;
        private final AtomicObservableSubscription subscription = new AtomicObservableSubscription();

        TakeLast(final Observable<T> items, final int count) {
            this.count = count;
            this.items = items;
        }

        @Override
        public Subscription call(Observer<T> observer) {
            return subscription.wrap(items.subscribe(new ItemObserver(observer)));
        }

        private class ItemObserver implements Observer<T> {

            private LinkedBlockingDeque<T> deque = new LinkedBlockingDeque<>(count);
            private final Observer<T> observer;

            public ItemObserver(Observer<T> observer) {
                this.observer = observer;
            }

            @Override
            public void onCompleted() {
                Iterator<T> reverse = deque.descendingIterator();
                while (reverse.hasNext())
                    observer.onNext(reverse.next());
                observer.onCompleted();
            }

            @Override
            public void onError(Exception e) {
                observer.onError(e);
            }

            @Override
            public void onNext(T args) {
                while (!deque.offerFirst(args))
                    deque.removeLast();
            }

        }

    }
}
