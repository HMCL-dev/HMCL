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
package org.jackhuang.hmcl.auth.yggdrasil

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import org.jackhuang.hmcl.auth.*
import org.jackhuang.hmcl.util.*
import org.jackhuang.hmcl.util.doGet
import org.jackhuang.hmcl.util.doPost
import org.jackhuang.hmcl.util.toURL
import java.io.IOException
import java.net.Proxy
import java.net.URL
import java.util.*

class YggdrasilAccount private constructor(override val username: String): Account() {
    private var password: String? = null
    private var userId: String? = null
    private var accessToken: String? = null
    private var clientToken: String = randomToken()
    private var isOnline: Boolean = false
    private var userProperties = PropertyMap()
    var selectedProfile: GameProfile? = null
        private set
    private var profiles: Array<GameProfile>? = null
    private var userType: UserType = UserType.LEGACY

    init {
        if (username.isBlank())
            throw IllegalArgumentException("Username cannot be blank")
        if (!username.contains("@"))
            throw IllegalArgumentException("Yggdrasil account user name must be email")
    }

    val isLoggedIn: Boolean
        get() = isNotBlank(accessToken)

    val canPlayOnline: Boolean
        get() = isLoggedIn && selectedProfile != null && isOnline

    val canLogIn: Boolean
        get() = !canPlayOnline && username.isNotBlank() && (isNotBlank(password) || isNotBlank((accessToken)))

    override fun logIn(proxy: Proxy): AuthInfo {
        if (canPlayOnline)
            return AuthInfo(
                    username = selectedProfile!!.name!!,
                    userId = UUIDTypeAdapter.fromUUID(selectedProfile!!.id!!),
                    authToken = accessToken!!,
                    userType = userType,
                    userProperties = GSON.toJson(userProperties)
            )
        else {
            logIn0(proxy)
            if (!isLoggedIn)
                throw AuthenticationException("Wrong password for account $username")
            if (selectedProfile == null) {
                // TODO: multi-available-profiles support
                throw UnsupportedOperationException("Do not support multi-available-profiles account yet.")
            } else {
                return AuthInfo(
                        username = selectedProfile!!.name!!,
                        userId = UUIDTypeAdapter.fromUUID(selectedProfile!!.id!!),
                        authToken = accessToken!!,
                        userType = userType,
                        userProperties = GSON.toJson(userProperties)
                )
            }
        }
    }

    private fun logIn0(proxy: Proxy) {
        if (isNotBlank(accessToken)) {
            if (isBlank(userId))
                if (isNotBlank(username))
                    userId = username
                else
                    throw AuthenticationException("Invalid uuid and username")
            if (checkTokenValidity(proxy)) {
                isOnline = true
                return
            }
            logIn1(ROUTE_REFRESH, RefreshRequest(clientToken = clientToken, accessToken = accessToken!!, selectedProfile = selectedProfile), proxy)
        } else if (isNotBlank(password)) {
            logIn1(ROUTE_AUTHENTICATE, AuthenticationRequest(username, password!!, clientToken), proxy)
        } else
            throw AuthenticationException("Password cannot be blank")
    }

    private fun logIn1(url: URL, input: Any, proxy: Proxy) {
        val response = makeRequest(url, input, proxy)

        if (clientToken != response?.clientToken)
            throw AuthenticationException("Client token changed")

        if (response.selectedProfile != null)
            userType = UserType.fromLegacy(response.selectedProfile.legacy)
        else if (response.availableProfiles?.getOrNull(0) != null)
            userType = UserType.fromLegacy(response.availableProfiles[0].legacy)

        val user = response.user
        userId = user?.id ?: username
        isOnline = true
        profiles = response.availableProfiles
        selectedProfile = response.selectedProfile
        userProperties.clear()
        accessToken = response.accessToken

        if (user?.properties != null)
            userProperties.putAll(user.properties)
    }

    override fun logOut() {
        password = null
        userId = null
        accessToken = null
        isOnline = false
        userProperties.clear()
        profiles = null
        selectedProfile = null
    }

    override fun toStorage(): MutableMap<Any, Any> {
        val result = HashMap<Any, Any>()

        result[STORAGE_KEY_USER_NAME] = username
        result[STORAGE_KEY_CLIENT_TOKEN] = clientToken
        if (userId != null)
            result[STORAGE_KEY_USER_ID] = userId!!
        if (!userProperties.isEmpty())
            result[STORAGE_KEY_USER_PROPERTIES] = userProperties.toList()
        val profile = selectedProfile
        if (profile?.name != null && profile.id != null) {
            result[STORAGE_KEY_PROFILE_NAME] = profile.name
            result[STORAGE_KEY_PROFILE_ID] = UUIDTypeAdapter.fromUUID(profile.id)
            if (!profile.properties.isEmpty())
                result[STORAGE_KEY_PROFILE_PROPERTIES] = profile.properties.toList()
        }
        if (accessToken != null && accessToken!!.isNotBlank())
            result[STORAGE_KEY_ACCESS_TOKEN] = accessToken!!

        return result
    }

    private fun makeRequest(url: URL, input: Any?, proxy: Proxy): Response? {
        try {
            val jsonResult =
                    if (input == null) url.doGet(proxy)
                    else url.doPost(GSON.toJson(input), "application/json", proxy)

            val response = GSON.fromJson<Response>(jsonResult) ?: return null

            if (response.error?.isNotBlank() ?: false) {
                LOG.severe("Failed to log in, the auth server returned an error: " + response.error + ", message: " + response.errorMessage + ", cause: " + response.cause)
                if (response.errorMessage != null)
                    if (response.errorMessage.contains("Invalid credentials"))
                        throw InvalidCredentialsException(this)
                    else if (response.errorMessage.contains("Invalid token"))
                        throw InvalidTokenException(this)
                throw AuthenticationException("Request error: ${response.errorMessage}")
            }

            return response
        } catch (e: IOException) {
            throw AuthenticationException("Unable to connect to authentication server", e)
        } catch (e: JsonParseException) {
            throw AuthenticationException("Unable to parse server response", e)
        }
    }

    private fun checkTokenValidity(proxy: Proxy): Boolean {
        val access = accessToken ?: return false
        try {
            makeRequest(ROUTE_VALIDATE, ValidateRequest(clientToken, access), proxy)
            return true
        } catch (e: AuthenticationException) {
            return false
        }
    }

    override fun toString() = "YggdrasilAccount[username=$username]"

    companion object YggdrasilAccountFactory : AccountFactory<YggdrasilAccount> {
        private val GSON = GsonBuilder()
                .registerTypeAdapter(GameProfile::class.java, GameProfile)
                .registerTypeAdapter(PropertyMap::class.java, PropertyMap)
                .registerTypeAdapter(UUID::class.java, UUIDTypeAdapter)
                .create()

        private val BASE_URL = "https://authserver.mojang.com/"
        private val ROUTE_AUTHENTICATE = (BASE_URL + "authenticate").toURL()
        private val ROUTE_REFRESH = (BASE_URL + "refresh").toURL()
        private val ROUTE_VALIDATE = (BASE_URL + "validate").toURL()

        private val STORAGE_KEY_ACCESS_TOKEN = "accessToken"
        private val STORAGE_KEY_PROFILE_NAME = "displayName"
        private val STORAGE_KEY_PROFILE_ID = "uuid"
        private val STORAGE_KEY_PROFILE_PROPERTIES = "profileProperties"
        private val STORAGE_KEY_USER_NAME = "username"
        private val STORAGE_KEY_USER_ID = "userid"
        private val STORAGE_KEY_USER_PROPERTIES = "userProperties"
        private val STORAGE_KEY_CLIENT_TOKEN = "clientToken"

        fun randomToken() = UUIDTypeAdapter.fromUUID(UUID.randomUUID())

        override fun fromUsername(username: String, password: String): YggdrasilAccount {
            val account = YggdrasilAccount(username)
            account.password = password
            return account
        }

        override fun fromStorage(storage: Map<Any, Any>): YggdrasilAccount {
            val username = storage[STORAGE_KEY_USER_NAME] as? String? ?: throw IllegalArgumentException("storage does not have key $STORAGE_KEY_USER_NAME")
            val account = YggdrasilAccount(username)
            account.userId = storage[STORAGE_KEY_USER_ID] as? String ?: username
            account.accessToken = storage[STORAGE_KEY_ACCESS_TOKEN] as? String
            account.clientToken = storage[STORAGE_KEY_CLIENT_TOKEN] as? String? ?: throw IllegalArgumentException("storage does not have key $STORAGE_KEY_CLIENT_TOKEN")
            val userProperties = storage[STORAGE_KEY_USER_PROPERTIES] as? List<*>
            if (userProperties != null)
                account.userProperties.fromList(userProperties)
            val profileId = storage[STORAGE_KEY_PROFILE_ID] as? String
            val profileName = storage[STORAGE_KEY_PROFILE_NAME] as? String
            val profile: GameProfile?
            if (profileId != null && profileName != null) {
                profile = GameProfile(UUIDTypeAdapter.fromString(profileId), profileName)
                val profileProperties = storage[STORAGE_KEY_PROFILE_PROPERTIES] as? List<*>
                if (profileProperties != null)
                    profile.properties.fromList(profileProperties)
            } else
                profile = null
            account.selectedProfile = profile

            return account
        }

    }
}