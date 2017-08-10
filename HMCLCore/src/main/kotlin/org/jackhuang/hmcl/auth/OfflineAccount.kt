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

import org.jackhuang.hmcl.util.DigestUtils
import java.net.Proxy

class OfflineAccount private constructor(val uuid: String, override val username: String): Account() {

    init {
        if (username.isBlank())
            throw IllegalArgumentException("Username cannot be blank")
    }

    override fun logIn(proxy: Proxy): AuthInfo {
        if (username.isBlank() || uuid.isBlank())
            throw AuthenticationException("Username cannot be empty")
        return AuthInfo(
                username = username,
                userId = uuid,
                authToken = uuid
        )
    }

    override fun logOut() {
        // Offline account need not log out.
    }

    override fun toStorage(): MutableMap<Any, Any> {
        return mutableMapOf(
                "uuid" to uuid,
                "username" to username
        )
    }

    override fun toString() = "OfflineAccount[username=$username,uuid=$uuid]"

    companion object OfflineAccountFactory : AccountFactory<OfflineAccount> {

        override fun fromUsername(username: String, password: String): OfflineAccount {
            return OfflineAccount(
                    username = username,
                    uuid = getUUIDFromUserName(username)
            )
        }

        override fun fromStorage(storage: Map<Any, Any>): OfflineAccount {
            val username = storage["username"] as? String
                    ?: throw IllegalStateException("Configuration is malformed.")
            val obj = storage["uuid"]
            return OfflineAccount(
                    username = username,
                    uuid = obj as? String ?: getUUIDFromUserName(username)
            )
        }

        private fun getUUIDFromUserName(username: String) = DigestUtils.md5Hex(username)
    }
}