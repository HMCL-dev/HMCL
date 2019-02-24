/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.wizard;

import javafx.scene.Node;
import org.jackhuang.hmcl.task.Task;

import java.util.*;

public class WizardController implements Navigation {
    private final WizardDisplayer displayer;
    private WizardProvider provider = null;
    private final Map<String, Object> settings = new HashMap<>();
    private final Stack<Node> pages = new Stack<>();

    public WizardController(WizardDisplayer displayer) {
        this.displayer = displayer;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public WizardDisplayer getDisplayer() {
        return displayer;
    }

    public void setProvider(WizardProvider provider) {
        this.provider = provider;
    }

    public List<Node> getPages() {
        return Collections.unmodifiableList(pages);
    }

    @Override
    public void onStart() {
        Objects.requireNonNull(provider);

        settings.clear();
        provider.start(settings);

        pages.clear();
        Node page = navigatingTo(0);
        pages.push(page);

        if (page instanceof WizardPage)
            ((WizardPage) page).onNavigate(settings);

        displayer.onStart();
        displayer.navigateTo(page, NavigationDirection.START);
    }

    @Override
    public void onNext() {
        onNext(navigatingTo(pages.size()));
    }

    public void onNext(Node page) {
        pages.push(page);

        if (page instanceof WizardPage)
            ((WizardPage) page).onNavigate(settings);

        displayer.navigateTo(page, NavigationDirection.NEXT);
    }

    @Override
    public void onPrev(boolean cleanUp) {
        if (!canPrev()) {
            throw new IllegalStateException("Cannot go backward since this is the back page. Pages: " + pages);
        }

        Node page = pages.pop();
        if (cleanUp && page instanceof WizardPage)
            ((WizardPage) page).cleanup(settings);

        Node prevPage = pages.peek();
        if (prevPage instanceof WizardPage)
            ((WizardPage) prevPage).onNavigate(settings);

        displayer.navigateTo(prevPage, NavigationDirection.PREVIOUS);
    }

    @Override
    public boolean canPrev() {
        return pages.size() > 1;
    }

    @Override
    public void onFinish() {
        Object result = provider.finish(settings);
        if (result instanceof Summary) displayer.navigateTo(((Summary) result).getComponent(), NavigationDirection.NEXT);
        else if (result instanceof Task<?>) displayer.handleTask(settings, ((Task<?>) result));
        else if (result != null) throw new IllegalStateException("Unrecognized wizard result: " + result);
    }

    @Override
    public void onEnd() {
        settings.clear();
        pages.clear();
        displayer.onEnd();
    }

    @Override
    public void onCancel() {
        displayer.onCancel();
        onEnd();
    }

    protected Node navigatingTo(int step) {
        return provider.createPage(this, step, settings);
    }
}
