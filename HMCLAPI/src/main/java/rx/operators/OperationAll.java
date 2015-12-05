package rx.operators;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.AtomicObservableSubscription;
import rx.util.functions.Func1;

import java.util.concurrent.atomic.AtomicBoolean;

public class OperationAll {

    public static <T> Func1<Observer<Boolean>, Subscription> all(Observable<T> sequence, Func1<T, Boolean> predicate) {
        return new AllObservable<>(sequence, predicate);
    }

    private static class AllObservable<T> implements Func1<Observer<Boolean>, Subscription> {
        private final Observable<T> sequence;
        private final Func1<T, Boolean> predicate;

        private final AtomicBoolean status = new AtomicBoolean(true);
        private final AtomicObservableSubscription subscription = new AtomicObservableSubscription();


        private AllObservable(Observable<T> sequence, Func1<T, Boolean> predicate) {
            this.sequence = sequence;
            this.predicate = predicate;
        }


        @Override
        public Subscription call(final Observer<Boolean> observer) {
            return subscription.wrap(sequence.subscribe(new Observer<T>() {
                @Override
                public void onCompleted() {
                    if (status.get()) {
                        observer.onNext(true);
                        observer.onCompleted();
                    }
                }

                @Override
                public void onError(Exception e) {
                    observer.onError(e);
                }

                @Override
                public void onNext(T args) {
                    boolean result = predicate.call(args);
                    boolean changed = status.compareAndSet(true, result);

                    if (changed && !result) {
                        observer.onNext(false);
                        observer.onCompleted();
                        subscription.unsubscribe();
                    }
                }
            }));
        }
    }
}
