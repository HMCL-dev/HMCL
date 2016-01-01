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

import java.util.concurrent.TimeUnit;

import rx.Subscription;
import rx.util.functions.Func0;

/**
 * Executes work immediately on the current thread.
 */
public final class ImmediateScheduler extends AbstractScheduler {

    private static final ImmediateScheduler INSTANCE = new ImmediateScheduler();

    public static ImmediateScheduler getInstance() {
        return INSTANCE;
    }

    private ImmediateScheduler() {
    }

    @Override
    public Subscription schedule(Func0<Subscription> action) {
        return action.call();
    }

    @Override
    public Subscription schedule(Func0<Subscription> action, long dueTime, TimeUnit unit) {
        return schedule(new SleepingAction(action, this, dueTime, unit));
    }

}
