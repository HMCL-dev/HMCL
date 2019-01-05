/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.AutoTypingMap;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;

/**
 *
 * @author huangyuhui
 */
class SimpleTask extends Task {

    private final ExceptionalConsumer<AutoTypingMap<String>, ?> consumer;
    private final Scheduler scheduler;

    public SimpleTask(String name, ExceptionalConsumer<AutoTypingMap<String>, ?> consumer, Scheduler scheduler) {
        this.consumer = consumer;
        this.scheduler = scheduler;

        if (name == null) {
            setSignificance(TaskSignificance.MINOR);
            setName(consumer.toString());
        } else {
            setName(name);
        }
    }

    @Override
    public Scheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void execute() throws Exception {
        consumer.accept(getVariables());
    }
}
