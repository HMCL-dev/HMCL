package org.jackhuang.hmcl.ui.download

import javafx.scene.Node
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider
import org.jackhuang.hmcl.game.HMCLModpackInstallTask
import org.jackhuang.hmcl.game.HMCLModpackManifest
import org.jackhuang.hmcl.game.Version
import org.jackhuang.hmcl.mod.CurseForgeModpackInstallTask
import org.jackhuang.hmcl.mod.CurseForgeModpackManifest
import org.jackhuang.hmcl.mod.Modpack
import org.jackhuang.hmcl.setting.EnumGameDirectory
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.task
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardProvider
import java.io.File

class InstallWizardProvider(val profile: Profile, val gameVersion: String, val version: Version, val forge: String? = null, val liteloader: String? = null, val optifine: String? = null): WizardProvider() {

    override fun start(settings: MutableMap<String, Any>) {
    }

    override fun finish(settings: MutableMap<String, Any>): Any? {
        var ret = task {}

        if (settings.containsKey("forge"))
            ret = ret with profile.dependency.installLibraryAsync(gameVersion, version, "forge", settings["forge"] as String)

        if (settings.containsKey("liteloader"))
            ret = ret with profile.dependency.installLibraryAsync(gameVersion, version, "liteloader", settings["liteloader"] as String)

        if (settings.containsKey("optifine"))
            ret = ret with profile.dependency.installLibraryAsync(gameVersion, version, "optifine", settings["optifine"] as String)

        return ret with task(Scheduler.JAVAFX) { profile.repository.refreshVersions() }
    }

    override fun createPage(controller: WizardController, step: Int, settings: MutableMap<String, Any>): Node {
        return when (step) {
            0 -> AdditionalInstallersPage(this, controller, profile.repository, BMCLAPIDownloadProvider)
            else -> throw IllegalStateException()
        }
    }

    override fun cancel(): Boolean {
        return true
    }

}