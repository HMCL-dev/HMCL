package org.jackhuang.hmcl.ui

import com.jfoenix.effects.JFXDepthManager
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane

class InstallerItem(artifact: String, version: String, private val deleteCallback: (InstallerItem) -> Unit) : BorderPane() {
    @FXML lateinit var lblInstallerArtifact: Label
    @FXML lateinit var lblInstallerVersion: Label

    init {
        loadFXML("/assets/fxml/version/installer-item.fxml")

        style = "-fx-background-radius: 2; -fx-background-color: white; -fx-padding: 8;"
        JFXDepthManager.setDepth(this, 1)
        lblInstallerArtifact.text = artifact
        lblInstallerVersion.text = version
    }

    fun onDelete() {
        deleteCallback(this)
    }
}