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
import javafx.scene.layout.Pane
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardProvider

class DownloadWizardProvider(): WizardProvider() {

    override fun finish(settings: Map<String, Any>): Any? {
        val builder = Settings.selectedProfile.dependency.gameBuilder()

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

    override fun createPage(controller: WizardController, step: Int, settings: Map<String, Any>): Node {
        return when (step) {
            0 -> InstallTypePage(controller)
            1 -> when (settings[InstallTypePage.INSTALL_TYPE]) {
                0 -> VersionsPage(controller, "", BMCLAPIDownloadProvider, "game", { controller.onNext(InstallersPage(controller, BMCLAPIDownloadProvider)) })
                else -> Pane()
            }
            else -> throw IllegalStateException()
        }
    }

    override fun cancel(): Boolean {
        return true
    }

}