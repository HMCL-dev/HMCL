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

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.AtomicObservableSubscription;
import rx.util.SynchronizedObserver;
import rx.util.functions.Func1;

/**
 * An observable that wraps an observable of the same type and then enforces the
 * semantics expected of a well-behaved observable.
 * <p>
 * An observable that ensures onNext, onCompleted, or onError calls on its
 * subscribers are not interleaved, onCompleted and onError are only called once
 * respectively, and no onNext calls follow onCompleted and onError calls.
 * <p>
 * NOTE: {@link Observable#create} already wraps Observables so this is
 * generally redundant.
 *
 * @param <T> The type of the observable sequence.
 */
public final class OperationSynchronize<T> {

    /**
     * Accepts an observable and wraps it in another observable which ensures
     * that the resulting observable is well-behaved.
     *
     * A well-behaved observable ensures onNext, onCompleted, or onError calls
     * to its subscribers are not interleaved, onCompleted and onError are only
     * called once respectively, and no onNext calls follow onCompleted and
     * onError calls.
     *
     * @param observable
     * @param <T>
     *
     * @return
     */
    public static <T> Func1<Observer<T>, Subscription> synchronize(Observable<T> observable) {
        return new Synchronize<>(observable);
    }

    private static class Synchronize<T> implements Func1<Observer<T>, Subscription> {

        public Synchronize(Observable<T> innerObservable) {
            this.innerObservable = innerObservable;
        }

        private final Observable<T> innerObservable;
        private SynchronizedObserver<T> atomicObserver;

        @Override
        public Subscription call(Observer<T> observer) {
            AtomicObservableSubscription subscription = new AtomicObservableSubscription();
            atomicObserver = new SynchronizedObserver<>(observer, subscription);
            return subscription.wrap(innerObservable.subscribe(atomicObserver));
        }

    }
}
