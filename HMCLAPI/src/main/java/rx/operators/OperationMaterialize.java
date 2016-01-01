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

import rx.Notification;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.functions.Func1;

/**
 * Materializes the implicit notifications of an observable sequence as explicit
 * notification values.
 * <p>
 * In other words, converts a sequence of OnNext, OnError and OnCompleted events
 * into a sequence of ObservableNotifications containing the OnNext, OnError and
 * OnCompleted values.
 * <p>
 * See http://msdn.microsoft.com/en-us/library/hh229453(v=VS.103).aspx for the
 * Microsoft Rx equivalent.
 */
public final class OperationMaterialize {

    /**
     * Materializes the implicit notifications of an observable sequence as
     * explicit notification values.
     *
     * @param sequence An observable sequence of elements to project.
     *
     * @return An observable sequence whose elements are the result of
     *         materializing the notifications of the given sequence.
     *
     * @see http://msdn.microsoft.com/en-us/library/hh229453(v=VS.103).aspx
     */
    public static <T> Func1<Observer<Notification<T>>, Subscription> materialize(final Observable<T> sequence) {
        return new MaterializeObservable<>(sequence);
    }

    private static class MaterializeObservable<T> implements Func1<Observer<Notification<T>>, Subscription> {

        private final Observable<T> sequence;

        public MaterializeObservable(Observable<T> sequence) {
            this.sequence = sequence;
        }

        @Override
        public Subscription call(final Observer<Notification<T>> observer) {
            return sequence.subscribe(new Observer<T>() {

                @Override
                public void onCompleted() {
                    observer.onNext(new Notification<>());
                    observer.onCompleted();
                }

                @Override
                public void onError(Exception e) {
                    observer.onNext(new Notification<>(e));
                    observer.onCompleted();
                }

                @Override
                public void onNext(T value) {
                    observer.onNext(new Notification<>(value));
                }

            });
        }

    }
}
