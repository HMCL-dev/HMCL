/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rx.concurrency;

import java.awt.EventQueue;
import java.util.concurrent.TimeUnit;
import rx.Subscription;
import rx.util.functions.Func0;

/**
 *
 * @author huangyuhui
 */
public class EventQueueScheduler extends AbstractScheduler {
    private static final EventQueueScheduler INSTANCE = new EventQueueScheduler();

    public static EventQueueScheduler getInstance() {
        return INSTANCE;
    }

    @Override
    public Subscription schedule(Func0<Subscription> action) {
        final DiscardableAction discardableAction = new DiscardableAction(action);

        EventQueue.invokeLater(discardableAction::call);

        return discardableAction;
    }

    @Override
    public Subscription schedule(Func0<Subscription> action, long dueTime, TimeUnit unit) {
        return schedule(new SleepingAction(action, this, dueTime, unit));
    }

}
