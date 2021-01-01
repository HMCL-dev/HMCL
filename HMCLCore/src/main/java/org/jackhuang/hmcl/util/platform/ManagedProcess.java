/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.platform;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The managed process.
 *
 * @author huangyuhui
 * @see org.jackhuang.hmcl.launch.ExitWaiter
 * @see org.jackhuang.hmcl.launch.StreamPump
 */
public class ManagedProcess {

    private final Process process;
    private final List<String> commands;
    private final Map<String, Object> properties = new HashMap<>();
    private final Queue<String> lines = new ConcurrentLinkedQueue<>();
    private final List<Thread> relatedThreads = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param process the raw system process that this instance manages.
     * @param commands the command line of {@code process}.
     */
    public ManagedProcess(Process process, List<String> commands) {
        this.process = process;
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
    }

    /**
     * The raw system process that this instance manages.
     *
     * @return process
     */
    public Process getProcess() {
        return process;
    }

    /**
     * The command line.
     *
     * @return the list of each part of command line separated by spaces.
     */
    public List<String> getCommands() {
        return commands;
    }

    /**
     * To save some information you need.
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * The (unmodifiable) standard output/error lines.
     * If you want to add lines, use {@link #addLine}
     *
     * @see #addLine
     */
    public Collection<String> getLines() {
        return Collections.unmodifiableCollection(lines);
    }

    public void addLine(String line) {
        lines.add(line);
    }

    /**
     * Add related thread.
     *
     * If a thread is monitoring this raw process,
     * you are required to add the instance by this method.
     */
    public void addRelatedThread(Thread thread) {
        relatedThreads.add(thread);
    }

    /**
     * True if the managed process is running.
     */
    public boolean isRunning() {
        try {
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException e) {
            return false;
        }
    }

    /**
     * The exit code of raw process.
     */
    public int getExitCode() {
        return process.exitValue();
    }

    /**
     * Destroys the raw process and other related threads that are monitoring this raw process.
     */
    public void stop() {
        process.destroy();
        relatedThreads.forEach(Thread::interrupt);
    }

    @Override
    public String toString() {
        return "ManagedProcess[commands=" + commands + ", isRunning=" + isRunning() + "]";
    }

}
