package org.jackhuang.hmcl.ui.download

import javafx.scene.Node
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider
import org.jackhuang.hmcl.game.Version
import org.jackhuang.hmcl.mod.Modpack
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardProvider
import org.jackhuang.hmcl.util.task
import java.io.File

class InstallWizardProvider(val profile: Profile, val gameVersion: String, val version: Version, val forge: String? = null, val liteloader: String? = null, val optifine: String? = null): WizardProvider() {

    override fun start(settings: MutableMap<String, Any>) {
    }

    override fun finish(settings: MutableMap<String, Any>): Any? {
        var ret = task {}

        if (settings.containsKey("forge"))
            ret = ret.with(profile.dependency.installLibraryAsync(gameVersion, version, "forge", settings["forge"] as String))

        if (settings.containsKey("liteloader"))
            ret = ret.with(profile.dependency.installLibraryAsync(gameVersion, version, "liteloader", settings["liteloader"] as String))

        if (settings.containsKey("optifine"))
            ret = ret.with(profile.dependency.installLibraryAsync(gameVersion, version, "optifine", settings["optifine"] as String))

        return ret.with(task { profile.repository.refreshVersions() })
    }

    override fun createPage(controller: WizardController, step: Int, settings: MutableMap<String, Any>): Node {
        return when (step) {
            0 -> AdditionalInstallersPage(this, controller, profile.repository, BMCLAPIDownloadProvider.INSTANCE)
            else -> throw IllegalStateException()
        }
    }

    override fun cancel(): Boolean {
        return true
    }

}