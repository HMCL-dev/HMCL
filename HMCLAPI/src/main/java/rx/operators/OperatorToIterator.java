package rx.operators;

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import rx.Notification;
import rx.Observable;
import rx.Observer;
import rx.util.Exceptions;

/**
 * @see https://github.com/Netflix/RxJava/issues/50
 */
public class OperatorToIterator {

    /**
     * Returns an iterator that iterates all values of the observable.
     *
     * @param that an observable sequence to get an iterator for.
     * @param <T>  the type of source.
     *
     * @return the iterator that could be used to iterate over the elements of
     *         the observable.
     */
    public static <T> Iterator<T> toIterator(Observable<T> that) {
        final BlockingQueue<Notification<T>> notifications = new LinkedBlockingQueue<Notification<T>>();

        Observable.materialize(that).subscribe(new Observer<Notification<T>>() {
            @Override
            public void onCompleted() {
                // ignore
            }

            @Override
            public void onError(Exception e) {
                // ignore
            }

            @Override
            public void onNext(Notification<T> args) {
                notifications.offer(args);
            }
        });

        return new Iterator<T>() {
            private Notification<T> buf;

            @Override
            public boolean hasNext() {
                if (buf == null)
                    buf = take();
                return !buf.isOnCompleted();
            }

            @Override
            public T next() {
                if (buf == null)
                    buf = take();
                if (buf.isOnError())
                    throw Exceptions.propagate(buf.getException());

                T result = buf.getValue();
                buf = null;
                return result;
            }

            private Notification<T> take() {
                try {
                    return notifications.take();
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Read-only iterator");
            }
        };
    }
}
