package rx.subjects;

import java.util.concurrent.ConcurrentHashMap;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.AtomicObservableSubscription;
import rx.util.SynchronizedObserver;
import rx.util.functions.Func1;

public class Subject<T> extends Observable<T> implements Observer<T> {

    public static <T> Subject<T> create() {
        final ConcurrentHashMap<Subscription, Observer<T>> observers = new ConcurrentHashMap<>();

        Func1<Observer<T>, Subscription> onSubscribe = observer -> {
            final AtomicObservableSubscription subscription = new AtomicObservableSubscription();

            subscription.wrap(() -> {
                // on unsubscribe remove it from the map of outbound observers to notify
                observers.remove(subscription);
            });

            // on subscribe add it to the map of outbound observers to notify
            observers.put(subscription, new SynchronizedObserver<>(observer, subscription));
            return subscription;
        };

        return new Subject<>(onSubscribe, observers);
    }

    private final ConcurrentHashMap<Subscription, Observer<T>> observers;

    protected Subject(Func1<Observer<T>, Subscription> onSubscribe, ConcurrentHashMap<Subscription, Observer<T>> observers) {
        super(onSubscribe);
        this.observers = observers;
    }

    @Override
    public void onCompleted() {
        for (Observer<T> observer : observers.values())
            observer.onCompleted();
    }

    @Override
    public void onError(Exception e) {
        for (Observer<T> observer : observers.values())
            observer.onError(e);
    }

    @Override
    public void onNext(T args) {
        for (Observer<T> observer : observers.values())
            observer.onNext(args);
    }

}
