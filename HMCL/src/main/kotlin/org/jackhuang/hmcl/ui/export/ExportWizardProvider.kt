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
package org.jackhuang.hmcl.ui.export

import javafx.scene.Node
import org.jackhuang.hmcl.game.HMCLModpackExportTask
import org.jackhuang.hmcl.game.MODPACK_PREDICATE
import org.jackhuang.hmcl.mod.Modpack
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardProvider
import java.io.File

class ExportWizardProvider(private val profile: Profile, private val version: String) : WizardProvider() {
    override fun start(settings: MutableMap<String, Any>) {
    }

    override fun finish(settings: MutableMap<String, Any>): Any? {
        @Suppress("UNCHECKED_CAST")
        return HMCLModpackExportTask(profile.repository, version, settings[ModpackFileSelectionPage.MODPACK_FILE_SELECTION] as List<String>,
                Modpack(
                        name = settings[ModpackInfoPage.MODPACK_NAME] as String,
                        author = settings[ModpackInfoPage.MODPACK_AUTHOR] as String,
                        version = settings[ModpackInfoPage.MODPACK_VERSION] as String,
                        description = settings[ModpackInfoPage.MODPACK_DESCRIPTION] as String
                ), settings[ModpackInfoPage.MODPACK_FILE] as File)
    }

    override fun createPage(controller: WizardController, step: Int, settings: MutableMap<String, Any>): Node {
        return when(step) {
            0 -> ModpackInfoPage(controller, version)
            1 -> ModpackFileSelectionPage(controller, profile, version, MODPACK_PREDICATE)
            else -> throw IllegalArgumentException("step")
        }
    }

    override fun cancel(): Boolean {
        return true
    }

}