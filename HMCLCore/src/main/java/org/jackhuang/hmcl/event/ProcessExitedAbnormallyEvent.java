/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.event;

import org.jackhuang.hmcl.util.ManagedProcess;

/**
 * This event gets fired when a JavaProcess exited abnormally and the exit code is not zero.
 * <br></br>
 * This event is fired on the {@link org.jackhuang.hmcl.event.EventBus#EVENT_BUS}
 *
 * @author huangyuhui
 */
public final class ProcessExitedAbnormallyEvent extends Event {

    private final ManagedProcess process;

    /**
     * Constructor.
     *
     * @param source {@link org.jackhuang.hmcl.launch.ExitWaiter}
     * @param process The process that exited abnormally.
     */
    public ProcessExitedAbnormallyEvent(Object source, ManagedProcess process) {
        super(source);
        this.process = process;
    }

    public ManagedProcess getProcess() {
        return process;
    }
}
