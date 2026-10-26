// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.event.Event;

/**
 *
 * @author huang
 */
public class TaskEvent extends Event {

    private final Task<?> task;
    private final boolean failed;

    public TaskEvent(Object source, Task<?> task, boolean failed) {
        super(source);
        this.task = task;
        this.failed = failed;
    }

    public Task<?> getTask() {
        return task;
    }

    public boolean isFailed() {
        return failed;
    }

}
