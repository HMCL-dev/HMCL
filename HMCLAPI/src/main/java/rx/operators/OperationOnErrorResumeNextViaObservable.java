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

import java.util.concurrent.atomic.AtomicReference;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.AtomicObservableSubscription;
import rx.util.functions.Func1;

public final class OperationOnErrorResumeNextViaObservable<T> {

    public static <T> Func1<Observer<T>, Subscription> onErrorResumeNextViaObservable(Observable<T> originalSequence, Observable<T> resumeSequence) {
        return new OnErrorResumeNextViaObservable<>(originalSequence, resumeSequence);
    }

    private static class OnErrorResumeNextViaObservable<T> implements Func1<Observer<T>, Subscription> {

        private final Observable<T> resumeSequence;
        private final Observable<T> originalSequence;

        public OnErrorResumeNextViaObservable(Observable<T> originalSequence, Observable<T> resumeSequence) {
            this.resumeSequence = resumeSequence;
            this.originalSequence = originalSequence;
        }

        public Subscription call(final Observer<T> observer) {
            final AtomicObservableSubscription subscription = new AtomicObservableSubscription();

            // AtomicReference since we'll be accessing/modifying this across threads so we can switch it if needed
            final AtomicReference<AtomicObservableSubscription> subscriptionRef = new AtomicReference<>(subscription);

            // subscribe to the original Observable and remember the subscription
            subscription.wrap(originalSequence.subscribe(new Observer<T>() {
                @Override
                public void onNext(T value) {
                    // forward the successful calls
                    observer.onNext(value);
                }

                /**
                 * Instead of passing the onError forward, we intercept and "resume" with the resumeSequence.
                 */
                @Override
                public void onError(Exception ex) {
                    /* remember what the current subscription is so we can determine if someone unsubscribes concurrently */
                    AtomicObservableSubscription currentSubscription = subscriptionRef.get();
                    // check that we have not been unsubscribed before we can process the error
                    if (currentSubscription != null) {
                        /* error occurred, so switch subscription to the 'resumeSequence' */
                        AtomicObservableSubscription innerSubscription = new AtomicObservableSubscription(resumeSequence.subscribe(observer));
                        /* we changed the sequence, so also change the subscription to the one of the 'resumeSequence' instead */
                        if (!subscriptionRef.compareAndSet(currentSubscription, innerSubscription)) {
                            // we failed to set which means 'subscriptionRef' was set to NULL via the unsubscribe below
                            // so we want to immediately unsubscribe from the resumeSequence we just subscribed to
                            innerSubscription.unsubscribe();
                        }
                    }
                }

                @Override
                public void onCompleted() {
                    // forward the successful calls
                    observer.onCompleted();
                }
            }));

            return () -> {
                // this will get either the original, or the resumeSequence one and unsubscribe on it
                Subscription s = subscriptionRef.getAndSet(null);
                if (s != null)
                    s.unsubscribe();
            };
        }
    }
}