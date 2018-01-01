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
package org.jackhuang.hmcl.game

import com.google.gson.annotations.SerializedName
import org.jackhuang.hmcl.util.Immutable
import org.jackhuang.hmcl.util.OS
import org.jackhuang.hmcl.util.ignoreThrowable
import java.util.regex.Pattern

@Immutable
data class CompatibilityRule @JvmOverloads constructor(
        val action: Action = CompatibilityRule.Action.ALLOW,
        val os: OSRestriction? = null,
        val features: Map<String, Boolean>? = null
) {

    fun getAppliedAction(supportedFeatures: Map<String, Boolean>): Action? {
        if (os != null && !os.allow()) return null
        if (features != null) {
            features.entries.forEach { (key, value) ->
                if (supportedFeatures[key] != value)
                    return null
            }
        }
        return action
    }

    companion object {
        fun appliesToCurrentEnvironment(rules: Collection<CompatibilityRule>?, features: Map<String, Boolean> = emptyMap()): Boolean {
            if (rules == null)
                return true
            var action = CompatibilityRule.Action.DISALLOW
            for (rule in rules) {
                val thisAction = rule.getAppliedAction(features)
                if (thisAction != null) action = thisAction
            }
            return action == CompatibilityRule.Action.ALLOW
        }
    }

    enum class Action {
        @SerializedName("allow")
        ALLOW,
        @SerializedName("disallow")
        DISALLOW
    }

    @Immutable
    data class OSRestriction(
            val name: OS = OS.UNKNOWN,
            val version: String? = null,
            val arch: String? = null
    ) {
        fun allow(): Boolean {
            if (name != OS.UNKNOWN && name != OS.CURRENT_OS)
                return false
            if (version != null) {
                ignoreThrowable {
                    if (!Pattern.compile(version).matcher(OS.SYSTEM_VERSION).matches())
                        return false
                }
            }
            if (arch != null)
                ignoreThrowable {
                    if (!Pattern.compile(arch).matcher(OS.SYSTEM_ARCH).matches())
                        return false
                }
            return true
        }
    }
}