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

import javafx.scene.Node
import java.util.*

class WizardController(protected val displayer: WizardDisplayer, protected val provider: WizardProvider) : Navigation {
    val settings = mutableMapOf<String, Any>()
    val pages = Stack<Node>()

    override fun onStart() {
        val page = navigatingTo(0)
        pages.push(page)
        displayer.navigateTo(page, Navigation.NavigationDirection.START)
    }

    override fun onNext() {
        onNext(navigatingTo(pages.size))
    }

    fun onNext(page: Node) {
        pages.push(page)

        if (page is WizardPage)
            page.onNavigate(settings)

        displayer.navigateTo(page, Navigation.NavigationDirection.NEXT)
    }

    override fun onPrev(cleanUp: Boolean) {
        val page = pages.pop()
        if (cleanUp && page is WizardPage)
            page.cleanup(settings)

        val prevPage = pages.peek()
        if (prevPage is WizardPage)
            prevPage.onNavigate(settings)

        displayer.navigateTo(prevPage, Navigation.NavigationDirection.PREVIOUS)
    }

    override fun canPrev() = pages.size > 1

    override fun onFinish() {
        val result = provider.finish(settings)
        when (result) {
            is DeferredWizardResult -> displayer.handleDeferredWizardResult(settings, result)
            is Summary -> displayer.navigateTo(result.component, Navigation.NavigationDirection.NEXT)
        }
    }

    override fun onCancel() {

    }

    fun navigatingTo(step: Int): Node {
        return provider.createPage(this, step, settings)
    }

}