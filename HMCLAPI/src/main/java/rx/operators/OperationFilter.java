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
import rx.util.functions.Func1;

public final class OperationFilter<T> {

    public static <T> Func1<Observer<T>, Subscription> filter(Observable<T> that, Func1<T, Boolean> predicate) {
        return new Filter<>(that, predicate);
    }

    private static class Filter<T> implements Func1<Observer<T>, Subscription> {

        private final Observable<T> that;
        private final Func1<T, Boolean> predicate;
        private final AtomicObservableSubscription subscription = new AtomicObservableSubscription();

        public Filter(Observable<T> that, Func1<T, Boolean> predicate) {
            this.that = that;
            this.predicate = predicate;
        }

        @Override
        public Subscription call(final Observer<T> observer) {
            return subscription.wrap(that.subscribe(new Observer<T>() {
                @Override
                public void onNext(T value) {
                    try {
                        if (predicate.call(value)) {
                            observer.onNext(value);
                        }
                    } catch (Exception ex) {
                        observer.onError(ex);
                        // this will work if the sequence is asynchronous, it will have no effect on a synchronous observable
                        subscription.unsubscribe();
                    }
                }
                
                @Override
                public void onError(Exception ex) {
                    observer.onError(ex);
                }

                @Override
                public void onCompleted() {
                    observer.onCompleted();
                }
            }));
        }

    }
}