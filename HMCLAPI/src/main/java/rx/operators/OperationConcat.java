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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.AtomicObservableSubscription;
import rx.util.functions.Action1;
import rx.util.functions.Func1;

public final class OperationConcat {

    /**
     * Combine the observable sequences from the list of Observables into one
     * observable sequence without any transformation.
     *
     * @param sequences An observable sequence of elements to project.
     *
     * @return An observable sequence whose elements are the result of combining
     *         the output from the list of Observables.
     */
    public static <T> Func1<Observer<T>, Subscription> concat(final Observable<T>... sequences) {
        return new Func1<Observer<T>, Subscription>() {

            @Override
            public Subscription call(Observer<T> observer) {
                return new Concat<T>(sequences).call(observer);
            }
        };
    }

    public static <T> Func1<Observer<T>, Subscription> concat(final List<Observable<T>> sequences) {
        @SuppressWarnings("unchecked")
        Observable<T>[] o = sequences.toArray((Observable<T>[]) Array.newInstance(Observable.class, sequences.size()));
        return concat(o);
    }

    public static <T> Func1<Observer<T>, Subscription> concat(final Observable<Observable<T>> sequences) {
        final List<Observable<T>> list = new ArrayList<Observable<T>>();
        sequences.toList().subscribe(new Action1<List<Observable<T>>>() {
            @Override
            public void call(List<Observable<T>> t1) {
                list.addAll(t1);
            }

        });

        return concat(list);
    }

    private static class Concat<T> implements Func1<Observer<T>, Subscription> {

        private final Observable<T>[] sequences;
        private int num = 0;
        private int count = 0;
        private Subscription s;

        Concat(final Observable<T>... sequences) {
            this.sequences = sequences;
            this.num = sequences.length - 1;
        }

        private final AtomicObservableSubscription Subscription = new AtomicObservableSubscription();

        private final Subscription actualSubscription = new Subscription() {

            @Override
            public void unsubscribe() {
                if (null != s)
                    s.unsubscribe();
            }
        };

        public Subscription call(Observer<T> observer) {
            s = sequences[count].subscribe(new ConcatObserver(observer));

            return Subscription.wrap(actualSubscription);
        }

        private class ConcatObserver implements Observer<T> {

            private final Observer<T> observer;

            ConcatObserver(Observer<T> observer) {
                this.observer = observer;
            }

            @Override
            public void onCompleted() {
                if (num == count)
                    observer.onCompleted();
                else {
                    count++;
                    s = sequences[count].subscribe(this);
                }
            }

            @Override
            public void onError(Exception e) {
                observer.onError(e);

            }

            @Override
            public void onNext(T args) {
                observer.onNext(args);

            }
        }
    }
}
