/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.ui.SVG;

/// @author Glavo
/// @see LineComponentContainer
public interface LineComponent extends NoPaddingComponent {
    String DEFAULT_STYLE_CLASS = "line-component";

    PseudoClass PSEUDO_LARGER_TITLE = PseudoClass.getPseudoClass("large-title");

    Insets LEADING_ICON_MARGIN = new Insets(0, 12, 0, 0);
    Insets TRAILING_NODE_MARGIN = new Insets(0, 0, 0, 12);

    private Node self() {
        return (Node) this;
    }

    LineComponentContainer getRoot();

    default StringProperty titleProperty() {
        return getRoot().titleProperty();
    }

    default String getTitle() {
        return titleProperty().get();
    }

    default void setTitle(String title) {
        titleProperty().set(title);
    }

    default StringProperty subtitleProperty() {
        return getRoot().subtitleProperty();
    }

    default String getSubtitle() {
        return subtitleProperty().get();
    }

    default void setSubtitle(String subtitle) {
        subtitleProperty().set(subtitle);
    }

    default void setLeadingIcon(Image icon) {
        setLeadingIcon(icon, -1.0);
    }

    default void setLeadingIcon(Image icon, double size) {
        getRoot().setLeadingIcon(icon, size);
    }

    default void setLeadingIcon(SVG svg) {
        setLeadingIcon(svg, 20);
    }

    default void setLeadingIcon(SVG svg, double size) {
        getRoot().setLeadingIcon(svg, size);
    }

    default void setLargeTitle(boolean largeTitle) {
        self().pseudoClassStateChanged(PSEUDO_LARGER_TITLE, largeTitle);
    }

}
