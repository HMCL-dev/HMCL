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
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.util.ManagedProcess;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
public abstract class Launcher {

    protected final GameRepository repository;
    protected final String versionId;
    protected final Version version;
    protected final AuthInfo authInfo;
    protected final LaunchOptions options;
    protected final ProcessListener listener;
    protected final boolean daemon;

    public Launcher(GameRepository repository, String versionId, AuthInfo authInfo, LaunchOptions options) {
        this(repository, versionId, authInfo, options, null);
    }

    public Launcher(GameRepository repository, String versionId, AuthInfo authInfo, LaunchOptions options, ProcessListener listener) {
        this(repository, versionId, authInfo, options, listener, true);
    }

    public Launcher(GameRepository repository, String versionId, AuthInfo authInfo, LaunchOptions options, ProcessListener listener, boolean daemon) {
        this.repository = repository;
        this.versionId = versionId;
        this.authInfo = authInfo;
        this.options = options;
        this.listener = listener;
        this.daemon = daemon;

        version = repository.getVersion(versionId).resolve(repository);
    }

    /**
     * @param file the file path.
     */
    public abstract void makeLaunchScript(File file) throws IOException;

    public abstract ManagedProcess launch() throws IOException, InterruptedException;

}
