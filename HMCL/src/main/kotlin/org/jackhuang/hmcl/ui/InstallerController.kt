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
package org.jackhuang.hmcl.ui

import javafx.fxml.FXML
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import org.jackhuang.hmcl.download.game.VersionJSONSaveTask
import org.jackhuang.hmcl.game.Version
import org.jackhuang.hmcl.game.minecraftVersion
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.task
import org.jackhuang.hmcl.ui.download.InstallWizardProvider
import java.util.*

class InstallerController {
    private lateinit var profile: Profile
    private lateinit var versionId: String
    private lateinit var version: Version

    @FXML lateinit var scrollPane: ScrollPane
    @FXML lateinit var contentPane: VBox

    private var forge: String? = null
    private var liteloader: String? = null
    private var optifine: String? = null

    fun initialize() {
        scrollPane.smoothScrolling()
    }

    fun loadVersion(profile: Profile, versionId: String) {
        this.profile = profile
        this.versionId = versionId
        this.version = profile.repository.getVersion(versionId).resolve(profile.repository)

        contentPane.children.clear()
        forge = null
        liteloader = null
        optifine = null

        for (library in version.libraries) {
            val removeAction = { _: InstallerItem ->
                val newList = LinkedList(version.libraries)
                newList.remove(library)
                VersionJSONSaveTask(profile.repository, version.copy(libraries = newList))
                        .with(task { profile.repository.refreshVersions() })
                        .with(task(Scheduler.JAVAFX) { loadVersion(this.profile, this.versionId) })
                        .start()
            }
            if (library.groupId.equals("net.minecraftforge", ignoreCase = true) && library.artifactId.equals("forge", ignoreCase = true)) {
                contentPane.children += InstallerItem("Forge", library.version, removeAction)
                forge = library.version
            } else if (library.groupId.equals("com.mumfrey", ignoreCase = true) && library.artifactId.equals("liteloader", ignoreCase = true)) {
                contentPane.children += InstallerItem("LiteLoader", library.version, removeAction)
                liteloader = library.version
            } else if ((library.groupId.equals("net.optifine", ignoreCase = true) || library.groupId.equals("optifine", ignoreCase = true)) && library.artifactId.equals("optifine", ignoreCase = true)) {
                contentPane.children += InstallerItem("OptiFine", library.version, removeAction)
                optifine = library.version
            }
        }
    }

    fun onAdd() {
        // TODO: if minecraftVersion returns null.
        val gameVersion = minecraftVersion(profile.repository.getVersionJar(version)) ?: return

        Controllers.decorator.startWizard(InstallWizardProvider(profile, gameVersion, version, forge, liteloader, optifine))
    }
}