package rx.subscriptions;

import rx.Subscription;
import rx.util.functions.Action0;
import rx.util.functions.FuncN;
import rx.util.functions.Functions;

public class Subscriptions {
    /**
     * A {@link Subscription} that does nothing.
     * 
     * @return {@link Subscription}
     */
    public static Subscription empty() {
        return EMPTY;
    }

    /**
     * A {@link Subscription} implemented via a Func
     * 
     * @return {@link Subscription}
     */
    public static Subscription create(final Action0 unsubscribe) {
        return unsubscribe::call;
    }

    /**
     * A {@link Subscription} implemented via an anonymous function (such as closures from other languages).
     * 
     * @return {@link Subscription}
     */
    public static Subscription create(final Object unsubscribe) {
        final FuncN<?> f = Functions.from(unsubscribe);
        return f::call;
    }

    /**
     * A {@link Subscription} that does nothing when its unsubscribe method is called.
     */
    private static final Subscription EMPTY = () -> { };
}
