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
package org.jackhuang.hmcl

import org.jackhuang.hmcl.setting.Profile
import java.util.*

/**
 * This event gets fired when the selected profile changed.
 * <br></br>
 * This event is fired on the [org.jackhuang.hmcl.event.EVENT_BUS]
 * @param source [org.jackhuang.hmcl.setting.Settings]
 * *
 * @param Profile the new profile.
 * *
 * @author huangyuhui
 */
class ProfileChangedEvent(source: Any, val value: Profile) : EventObject(source)

/**
 * This event gets fired when loading profiles.
 * <br></br>
 * This event is fired on the [org.jackhuang.hmcl.event.EVENT_BUS]
 * @param source [org.jackhuang.hmcl.setting.Settings]
 * *
 * @author huangyuhui
 */
class ProfileLoadingEvent(source: Any) : EventObject(source)
