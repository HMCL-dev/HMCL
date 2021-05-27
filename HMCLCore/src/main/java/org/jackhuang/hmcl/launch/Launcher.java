/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.util.platform.ManagedProcess;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author huangyuhui
 */
public abstract class Launcher {

    protected final GameRepository repository;
    protected final Version version;
    protected final AuthInfo authInfo;
    protected final LaunchOptions options;
    protected final ProcessListener listener;
    protected final boolean daemon;
    protected final boolean customized_natives;
    protected final String customized_natives_path;

    public Launcher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options) {
        this(repository, version, authInfo, options, null);
    }

    public Launcher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options, ProcessListener listener) {
        this(repository, version, authInfo, options, listener, false, null, true);
    }

    public Launcher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options, ProcessListener listener, boolean customized_natives, String customized_natives_path) {
        this(repository, version, authInfo, options, listener, customized_natives, customized_natives_path, true);
    }

    public Launcher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options, ProcessListener listener, boolean customized_natives, String customized_natives_path, boolean daemon) {
        this.repository = repository;
        this.version = version;
        this.authInfo = authInfo;
        this.options = options;
        this.listener = listener;
        this.daemon = daemon;
        this.customized_natives = customized_natives;
        this.customized_natives_path = customized_natives_path;
    }

    /**
     * @param file the file path.
     */
    public abstract void makeLaunchScript(File file) throws IOException;

    public abstract ManagedProcess launch() throws IOException, InterruptedException;

}
