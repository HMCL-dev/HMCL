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
package org.jackhuang.hmcl.auth

import org.jackhuang.hmcl.auth.yggdrasil.GameProfile
import org.jackhuang.hmcl.util.Immutable
import org.jackhuang.hmcl.util.UUIDTypeAdapter

@Immutable
data class AuthInfo @JvmOverloads constructor(
        val username: String,
        val userId: String,
        val authToken: String,
        val userType: UserType = UserType.LEGACY,
        val userProperties: String = "{}",
        val userPropertyMap: String = "{}"
) {
    constructor(profile: GameProfile,
                authToken: String,
                userType: UserType = UserType.LEGACY,
                userProperties: String = "{}",
                userPropertyMap: String = "{}")
            : this(profile.name!!, UUIDTypeAdapter.fromUUID(profile.id!!), authToken, userType, userProperties, userPropertyMap) {
    }
}