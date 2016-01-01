/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
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
