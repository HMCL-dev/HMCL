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
package org.jackhuang.hmcl.ui.download

import javafx.scene.Node
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider
import org.jackhuang.hmcl.game.HMCLModpackInstallTask
import org.jackhuang.hmcl.game.HMCLModpackManifest
import org.jackhuang.hmcl.game.MMCInstallVersionSettingTask
import org.jackhuang.hmcl.mod.*
import org.jackhuang.hmcl.setting.EnumGameDirectory
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.task
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardProvider
import java.io.File

class DownloadWizardProvider(): WizardProvider() {
    lateinit var profile: Profile

    override fun start(settings: MutableMap<String, Any>) {
        profile = Settings.selectedProfile
        settings[PROFILE] = profile
    }

    private fun finishVersionDownloading(settings: MutableMap<String, Any>): Task {

        val builder = profile.dependency.gameBuilder()

        builder.name(settings["name"] as String)
        builder.gameVersion(settings["game"] as String)

        if (settings.containsKey("forge"))
            builder.version("forge", settings["forge"] as String)

        if (settings.containsKey("liteloader"))
            builder.version("liteloader", settings["liteloader"] as String)

        if (settings.containsKey("optifine"))
            builder.version("optifine", settings["optifine"] as String)

        return builder.buildAsync()
    }

    private fun finishModpackInstalling(settings: MutableMap<String, Any>): Task? {
        if (!settings.containsKey(ModpackPage.MODPACK_FILE))
            return null

        val selectedFile = settings[ModpackPage.MODPACK_FILE] as? File? ?: return null
        val modpack = settings[ModpackPage.MODPACK_CURSEFORGE_MANIFEST] as? Modpack? ?: return null
        val name = settings[ModpackPage.MODPACK_NAME] as? String? ?: return null

        profile.repository.markVersionAsModpack(name)

        val finalizeTask = task {
            profile.repository.refreshVersions()
            val vs = profile.specializeVersionSetting(name)
            profile.repository.undoMark(name)
            if (vs != null) {
                vs.gameDirType = EnumGameDirectory.VERSION_FOLDER
            }
        }

        return when (modpack.manifest) {
            is CurseForgeModpackManifest -> CurseForgeModpackInstallTask(profile.dependency, selectedFile, modpack.manifest as CurseForgeModpackManifest, name)
            is HMCLModpackManifest -> HMCLModpackInstallTask(profile, selectedFile, modpack, name)
            is InstanceConfiguration -> MMCModpackInstallTask(profile.dependency, selectedFile, modpack.manifest as InstanceConfiguration, name) with MMCInstallVersionSettingTask(profile, modpack.manifest as InstanceConfiguration, name)
            else -> throw Error()
        } with finalizeTask
    }

    override fun finish(settings: MutableMap<String, Any>): Any? {
        return when (settings[InstallTypePage.INSTALL_TYPE]) {
            0 -> finishVersionDownloading(settings)
            1 -> finishModpackInstalling(settings)
            else -> null
        }
    }

    override fun createPage(controller: WizardController, step: Int, settings: MutableMap<String, Any>): Node {
        val provider = profile.dependency.downloadProvider
        return when (step) {
            0 -> InstallTypePage(controller)
            1 -> when (settings[InstallTypePage.INSTALL_TYPE]) {
                0 -> VersionsPage(controller, "", provider, "game") { controller.onNext(InstallersPage(controller, profile.repository, provider)) }
                1 -> ModpackPage(controller)
                else -> throw Error()
            }
            else -> throw IllegalStateException()
        }
    }

    override fun cancel(): Boolean {
        return true
    }

    companion object {
        const val PROFILE = "PROFILE"
    }

}