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
package org.jackhuang.hmcl.ui.wizard

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXToolbar
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.ui.Controllers
import org.jackhuang.hmcl.ui.animation.TransitionHandler
import org.jackhuang.hmcl.ui.loadFXML
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

internal class DefaultWizardDisplayer(private val prefix: String, wizardProvider: WizardProvider) : StackPane(), AbstractWizardDisplayer {

    override val wizardController = WizardController(this).apply { provider = wizardProvider }
    override val cancelQueue: Queue<Any> = ConcurrentLinkedQueue<Any>()

    lateinit var transitionHandler: TransitionHandler

    @FXML lateinit var root: StackPane
    @FXML lateinit var backButton: JFXButton
    @FXML lateinit var toolbar: JFXToolbar
    /**
     * Only shown if it is needed in now step.
     */
    @FXML lateinit var refreshButton: JFXButton
    @FXML lateinit var titleLabel: Label

    lateinit var nowPage: Node

    init {
        loadFXML("/assets/fxml/wizard.fxml")
        toolbar.effect = null
    }

    fun initialize() {
        transitionHandler = TransitionHandler(root)

        wizardController.onStart()
    }

    override fun onStart() {
    }

    override fun onEnd() {
    }

    override fun onCancel() {

    }

    fun back() {
        wizardController.onPrev(true)
    }

    fun close() {
        wizardController.onCancel()
        Controllers.navigate(null)
    }

    fun refresh() {
        (nowPage as Refreshable).refresh()
    }

    override fun navigateTo(page: Node, nav: Navigation.NavigationDirection) {
        backButton.isDisable = !wizardController.canPrev()
        transitionHandler.setContent(page, nav.animation.animationProducer)
        val title = if (prefix.isEmpty()) "" else "$prefix - "
        if (page is WizardPage)
            titleLabel.text = title + page.title
        refreshButton.isVisible = page is Refreshable
        nowPage = page
    }
}