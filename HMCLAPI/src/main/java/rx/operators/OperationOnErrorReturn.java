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

/**
 * When an onError occurs the resumeFunction will be executed and it's response passed to onNext instead of calling onError.
 */
public final class OperationOnErrorReturn<T> {

    public static <T> Func1<Observer<T>, Subscription> onErrorReturn(Observable<T> originalSequence, Func1<Exception, T> resumeFunction) {
        return new OnErrorReturn<>(originalSequence, resumeFunction);
    }

    private static class OnErrorReturn<T> implements Func1<Observer<T>, Subscription> {
        private final Func1<Exception, T> resumeFunction;
        private final Observable<T> originalSequence;

        public OnErrorReturn(Observable<T> originalSequence, Func1<Exception, T> resumeFunction) {
            this.resumeFunction = resumeFunction;
            this.originalSequence = originalSequence;
        }

        @Override
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
                        try {
                            /* error occurred, so execute the function, give it the exception and call onNext with the response */
                            onNext(resumeFunction.call(ex));
                            /*
                             * we are not handling an exception thrown from this function ... should we do something?
                             * error handling within an error handler is a weird one to determine what we should do
                             * right now I'm going to just let it throw whatever exceptions occur (such as NPE)
                             * but I'm considering calling the original Observer.onError to act as if this OnErrorReturn operator didn't happen
                             */

                            /* we are now completed */
                            onCompleted();

                            /* unsubscribe since it blew up */
                            currentSubscription.unsubscribe();
                        } catch (Exception e) {
                            // the return function failed so we need to call onError
                            // I am using CompositeException so that both exceptions can be seen
                            observer.onError(new CompositeException("OnErrorReturn function failed", Arrays.asList(ex, e)));
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