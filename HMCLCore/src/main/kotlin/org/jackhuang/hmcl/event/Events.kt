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
package org.jackhuang.hmcl.event

import org.jackhuang.hmcl.util.ManagedProcess
import java.util.*

/**
 * This event gets fired when loading versions in a .minecraft folder.
 * <br></br>
 * This event is fired on the [org.jackhuang.hmcl.api.HMCLApi.EVENT_BUS]
 * @param source [org.jackhuang.hmcl.core.version.MinecraftVersionManager]
 * *
 * @param IMinecraftService .minecraft folder.
 * *
 * @author huangyuhui
 */
class RefreshingVersionsEvent(source: Any) : EventObject(source)

/**
 * This event gets fired when all the versions in .minecraft folder are loaded.
 * <br>
 * This event is fired on the {@link org.jackhuang.hmcl.api.HMCLApi#EVENT_BUS}
 * @param source [org.jackhuang.hmcl.game.GameRepository]
 * @author huangyuhui
 */
class RefreshedVersionsEvent(source: Any) : EventObject(source)

/**
 * This event gets fired when a minecraft version has been loaded.
 * <br></br>
 * This event is fired on the [org.jackhuang.hmcl.api.HMCLApi.EVENT_BUS]
 * @param source [org.jackhuang.hmcl.core.version.MinecraftVersionManager]
 * @param version the version id.
 * @author huangyuhui
 */
class LoadedOneVersionEvent(source: Any, val version: String) : EventObject(source)

/**
 * This event gets fired when a JavaProcess exited abnormally and the exit code is not zero.
 * <br></br>
 * This event is fired on the [org.jackhuang.hmcl.api.HMCLApi.EVENT_BUS]
 * @param source [org.jackhuang.hmcl.util.sys.JavaProcessMonitor]
 * @param JavaProcess The process that exited abnormally.
 * @author huangyuhui
 */
class JavaProcessExitedAbnormallyEvent(source: Any, val value: ManagedProcess) : EventObject(source)

/**
 * This event gets fired when minecraft process exited successfully and the exit code is 0.
 * <br></br>
 * This event is fired on the [org.jackhuang.hmcl.api.HMCLApi.EVENT_BUS]
 * @param source [org.jackhuang.hmcl.util.sys.JavaProcessMonitor]
 * @param JavaProcess minecraft process
 * @author huangyuhui
 */
class JavaProcessStoppedEvent(source: Any, val value: ManagedProcess) : EventObject(source)

/**
 * This event gets fired when we launch the JVM and it got crashed.
 * <br></br>
 * This event is fired on the [org.jackhuang.hmcl.api.HMCLApi.EVENT_BUS]
 * @param source [org.jackhuang.hmcl.util.sys.JavaProcessMonitor]
 * @param JavaProcess the crashed process.
 * @author huangyuhui
 */
class JVMLaunchFailedEvent(source: Any, val value: ManagedProcess) : EventObject(source)
