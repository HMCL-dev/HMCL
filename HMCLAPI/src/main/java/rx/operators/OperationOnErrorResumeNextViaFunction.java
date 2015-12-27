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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.AtomicObservableSubscription;
import rx.util.CompositeException;
import rx.util.functions.Func1;

public final class OperationOnErrorResumeNextViaFunction<T> {

    public static <T> Func1<Observer<T>, Subscription> onErrorResumeNextViaFunction(Observable<T> originalSequence, Func1<Exception, Observable<T>> resumeFunction) {
        return new OnErrorResumeNextViaFunction<>(originalSequence, resumeFunction);
    }

    private static class OnErrorResumeNextViaFunction<T> implements Func1<Observer<T>, Subscription> {

        private final Func1<Exception, Observable<T>> resumeFunction;
        private final Observable<T> originalSequence;

        public OnErrorResumeNextViaFunction(Observable<T> originalSequence, Func1<Exception, Observable<T>> resumeFunction) {
            this.resumeFunction = resumeFunction;
            this.originalSequence = originalSequence;
        }

        @Override
        public Subscription call(final Observer<T> observer) {
            // AtomicReference since we'll be accessing/modifying this across threads so we can switch it if needed
            final AtomicReference<AtomicObservableSubscription> subscriptionRef = new AtomicReference<>(new AtomicObservableSubscription());

            // subscribe to the original Observable and remember the subscription
            subscriptionRef.get().wrap(new AtomicObservableSubscription(originalSequence.subscribe(new Observer<T>() {
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
                        try {
                            Observable<T> resumeSequence = resumeFunction.call(ex);
                            /* error occurred, so switch subscription to the 'resumeSequence' */
                            AtomicObservableSubscription innerSubscription = new AtomicObservableSubscription(resumeSequence.subscribe(observer));
                            /* we changed the sequence, so also change the subscription to the one of the 'resumeSequence' instead */
                            if (!subscriptionRef.compareAndSet(currentSubscription, innerSubscription)) {
                                // we failed to set which means 'subscriptionRef' was set to NULL via the unsubscribe below
                                // so we want to immediately unsubscribe from the resumeSequence we just subscribed to
                                innerSubscription.unsubscribe();
                            }
                        } catch (Exception e) {
                            // the resume function failed so we need to call onError
                            // I am using CompositeException so that both exceptions can be seen
                            observer.onError(new CompositeException("OnErrorResume function failed", Arrays.asList(ex, e)));
                        }
                    }
                }

                @Override
                public void onCompleted() {
                    // forward the successful calls
                    observer.onCompleted();
                }
            })));

            return () -> {
                // this will get either the original, or the resumeSequence one and unsubscribe on it
                Subscription s = subscriptionRef.getAndSet(null);
                if (s != null)
                    s.unsubscribe();
            };
        }
    }
}