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
package rx.concurrency;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import rx.Subscription;
import rx.util.functions.Func0;

/**
 * Schedules work on the current thread but does not execute immediately. Work is put in a queue and executed after the current unit of work is completed.
 */
public class CurrentThreadScheduler extends AbstractScheduler {
    private static final CurrentThreadScheduler INSTANCE = new CurrentThreadScheduler();

    public static CurrentThreadScheduler getInstance() {
        return INSTANCE;
    }

    private static final ThreadLocal<Queue<DiscardableAction>> QUEUE = new ThreadLocal<>();

    private CurrentThreadScheduler() {
    }

    @Override
    public Subscription schedule(Func0<Subscription> action) {
        DiscardableAction discardableAction = new DiscardableAction(action);
        enqueue(discardableAction);
        return discardableAction;
    }

    @Override
    public Subscription schedule(Func0<Subscription> action, long dueTime, TimeUnit unit) {
        return schedule(new SleepingAction(action, this, dueTime, unit));
    }

    private void enqueue(DiscardableAction action) {
        Queue<DiscardableAction> queue = QUEUE.get();
        boolean exec = queue == null;

        if (exec) {
            queue = new LinkedList<>();
            QUEUE.set(queue);
        }

        queue.add(action);

        if (exec) {
            while (!queue.isEmpty()) {
                queue.poll().call();
            }

            QUEUE.set(null);
        }
    }
}
