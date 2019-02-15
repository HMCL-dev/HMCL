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
package org.jackhuang.hmcl.ui;

import javafx.beans.binding.ObjectBinding;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;

public final class SVG {
    private SVG() {
    }

    private static Node createSVGPath(String d, ObjectBinding<? extends Paint> fill, double width, double height) {
        SVGPath path = new SVGPath();
        path.getStyleClass().add("svg");
        path.setContent(d);
        path.fillProperty().bind(fill);

        if (width < 0 || height < 0) {
            StackPane pane = new StackPane(path);
            pane.setAlignment(Pos.CENTER);
            return pane;
        }

        Group svg = new Group(path);
        double scale = Math.min(width / svg.getBoundsInParent().getWidth(), height / svg.getBoundsInParent().getHeight());
        svg.setScaleX(scale);
        svg.setScaleY(scale);

        return svg;
    }

    // default fill: white, width: 20, height 20

    public static Node gear(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M12,15.5A3.5,3.5 0 0,1 8.5,12A3.5,3.5 0 0,1 12,8.5A3.5,3.5 0 0,1 15.5,12A3.5,3.5 0 0,1 12,15.5M19.43,12.97C19.47,12.65 19.5,12.33 19.5,12C19.5,11.67 19.47,11.34 19.43,11L21.54,9.37C21.73,9.22 21.78,8.95 21.66,8.73L19.66,5.27C19.54,5.05 19.27,4.96 19.05,5.05L16.56,6.05C16.04,5.66 15.5,5.32 14.87,5.07L14.5,2.42C14.46,2.18 14.25,2 14,2H10C9.75,2 9.54,2.18 9.5,2.42L9.13,5.07C8.5,5.32 7.96,5.66 7.44,6.05L4.95,5.05C4.73,4.96 4.46,5.05 4.34,5.27L2.34,8.73C2.21,8.95 2.27,9.22 2.46,9.37L4.57,11C4.53,11.34 4.5,11.67 4.5,12C4.5,12.33 4.53,12.65 4.57,12.97L2.46,14.63C2.27,14.78 2.21,15.05 2.34,15.27L4.34,18.73C4.46,18.95 4.73,19.03 4.95,18.95L7.44,17.94C7.96,18.34 8.5,18.68 9.13,18.93L9.5,21.58C9.54,21.82 9.75,22 10,22H14C14.25,22 14.46,21.82 14.5,21.58L14.87,18.93C15.5,18.67 16.04,18.34 16.56,17.94L19.05,18.95C19.27,19.03 19.54,18.95 19.66,18.73L21.66,15.27C21.78,15.05 21.73,14.78 21.54,14.63L19.43,12.97Z", fill, width, height);
    }

    public static Node back(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M20,11V13H8L13.5,18.5L12.08,19.92L4.16,12L12.08,4.08L13.5,5.5L8,11H20Z", fill, width, height);
    }

    public static Node close(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M19,6.41L17.59,5L12,10.59L6.41,5L5,6.41L10.59,12L5,17.59L6.41,19L12,13.41L17.59,19L19,17.59L13.41,12L19,6.41Z", fill, width, height);
    }

    public static Node dotsVertical(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M12,16A2,2 0 0,1 14,18A2,2 0 0,1 12,20A2,2 0 0,1 10,18A2,2 0 0,1 12,16M12,10A2,2 0 0,1 14,12A2,2 0 0,1 12,14A2,2 0 0,1 10,12A2,2 0 0,1 12,10M12,4A2,2 0 0,1 14,6A2,2 0 0,1 12,8A2,2 0 0,1 10,6A2,2 0 0,1 12,4Z", fill, width, height);
    }

    public static Node delete(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M19,4H15.5L14.5,3H9.5L8.5,4H5V6H19M6,19A2,2 0 0,0 8,21H16A2,2 0 0,0 18,19V7H6V19Z", fill, width, height);
    }

    public static Node accountEdit(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M21.7,13.35L20.7,14.35L18.65,12.3L19.65,11.3C19.86,11.09 20.21,11.09 20.42,11.3L21.7,12.58C21.91,12.79 21.91,13.14 21.7,13.35M12,18.94L18.06,12.88L20.11,14.93L14.06,21H12V18.94M12,14C7.58,14 4,15.79 4,18V20H10V18.11L14,14.11C13.34,14.03 12.67,14 12,14M12,4A4,4 0 0,0 8,8A4,4 0 0,0 12,12A4,4 0 0,0 16,8A4,4 0 0,0 12,4Z", fill, width, height);
    }

    public static Node expand(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z", fill, width, height);
    }

    public static Node collapse(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z", fill, width, height);
    }

    public static Node navigate(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M14,3V5H17.59L7.76,14.83L9.17,16.24L19,6.41V10H21V3M19,19H5V5H12V3H5C3.89,3 3,3.89 3,5V19A2,2 0 0,0 5,21H19A2,2 0 0,0 21,19V12H19V19Z", fill, width, height);
    }

    public static Node launch(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M1008 6.286q18.857 13.714 15.429 36.571l-146.286 877.714q-2.857 16.571-18.286 25.714-8 4.571-17.714 4.571-6.286 0-13.714-2.857l-258.857-105.714-138.286 168.571q-10.286 13.143-28 13.143-7.429 0-12.571-2.286-10.857-4-17.429-13.429t-6.571-20.857v-199.429l493.714-605.143-610.857 528.571-225.714-92.571q-21.143-8-22.857-31.429-1.143-22.857 18.286-33.714l950.857-548.571q8.571-5.143 18.286-5.14311.429 0 20.571 6.286z", fill, width, height);
    }

    public static Node launch2(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M14,3V5H17.59L7.76,14.83L9.17,16.24L19,6.41V10H21V3M19,19H5V5H12V3H5C3.89,3 3,3.89 3,5V19A2,2 0 0,0 5,21H19A2,2 0 0,0 21,19V12H19V19Z", fill, width, height);
    }

    public static Node script(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M14,20A2,2 0 0,0 16,18V5H9A1,1 0 0,0 8,6V16H5V5A3,3 0 0,1 8,2H19A3,3 0 0,1 22,5V6H18V18L18,19A3,3 0 0,1 15,22H5A3,3 0 0,1 2,19V18H12A2,2 0 0,0 14,20Z", fill, width, height);
    }

    public static Node pencil(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M20.71,4.04C21.1,3.65 21.1,3 20.71,2.63L18.37,0.29C18,-0.1 17.35,-0.1 16.96,0.29L15,2.25L18.75,6M17.75,7L14,3.25L4,13.25V17H7.75L17.75,7Z", fill, width, height);
    }

    public static Node refresh(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M17.65,6.35C16.2,4.9 14.21,4 12,4A8,8 0 0,0 4,12A8,8 0 0,0 12,20C15.73,20 18.84,17.45 19.73,14H17.65C16.83,16.33 14.61,18 12,18A6,6 0 0,1 6,12A6,6 0 0,1 12,6C13.66,6 15.14,6.69 16.22,7.78L13,11H20V4L17.65,6.35Z", fill, width, height);
    }

    public static Node folderOpen(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M19,20H4C2.89,20 2,19.1 2,18V6C2,4.89 2.89,4 4,4H10L12,6H19A2,2 0 0,1 21,8H21L4,8V18L6.14,10H23.21L20.93,18.5C20.7,19.37 19.92,20 19,20Z", fill, width, height);
    }

    public static Node update(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M21,10.12H14.22L16.96,7.3C14.23,4.6 9.81,4.5 7.08,7.2C4.35,9.91 4.35,14.28 7.08,17C9.81,19.7 14.23,19.7 16.96,17C18.32,15.65 19,14.08 19,12.1H21C21,14.08 20.12,16.65 18.36,18.39C14.85,21.87 9.15,21.87 5.64,18.39C2.14,14.92 2.11,9.28 5.62,5.81C9.13,2.34 14.76,2.34 18.27,5.81L21,3V10.12M12.5,8V12.25L16,14.33L15.28,15.54L11,13V8H12.5Z", fill, width, height);
    }

    public static Node closeCircle(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M12,2C17.53,2 22,6.47 22,12C22,17.53 17.53,22 12,22C6.47,22 2,17.53 2,12C2,6.47 6.47,2 12,2M15.59,7L12,10.59L8.41,7L7,8.41L10.59,12L7,15.59L8.41,17L12,13.41L15.59,17L17,15.59L13.41,12L17,8.41L15.59,7Z", fill, width, height);
    }

    public static Node checkCircle(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M12,2A10,10 0 0,1 22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 12,2M11,16.5L18,9.5L16.59,8.09L11,13.67L7.91,10.59L6.5,12L11,16.5Z", fill, width, height);
    }

    public static Node infoCircle(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M13,9H11V7H13M13,17H11V11H13M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z", fill, width, height);
    }

    public static Node helpCircle(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M15.07,11.25L14.17,12.17C13.45,12.89 13,13.5 13,15H11V14.5C11,13.39 11.45,12.39 12.17,11.67L13.41,10.41C13.78,10.05 14,9.55 14,9C14,7.89 13.1,7 12,7A2,2 0 0,0 10,9H8A4,4 0 0,1 12,5A4,4 0 0,1 16,9C16,9.88 15.64,10.67 15.07,11.25M13,19H11V17H13M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12C22,6.47 17.5,2 12,2Z", fill, width, height);
    }

    public static Node alert(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M13,14H11V10H13M13,18H11V16H13M1,21H23L12,2L1,21Z", fill, width, height);
    }

    public static Node plus(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M19,13H13V19H11V13H5V11H11V5H13V11H19V13Z", fill, width, height);
    }

    public static Node importIcon(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M14,12L10,8V11H2V13H10V16M20,18V6C20,4.89 19.1,4 18,4H6A2,2 0 0,0 4,6V9H6V6H18V18H6V15H4V18A2,2 0 0,0 6,20H18A2,2 0 0,0 20,18Z", fill, width, height);
    }

    public static Node export(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M23,12L19,8V11H10V13H19V16M1,18V6C1,4.89 1.9,4 3,4H15A2,2 0 0,1 17,6V9H15V6H3V18H15V15H17V18A2,2 0 0,1 15,20H3A2,2 0 0,1 1,18Z", fill, width, height);
    }

    public static Node openInNew(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M14,3V5H17.59L7.76,14.83L9.17,16.24L19,6.41V10H21V3M19,19H5V5H12V3H5C3.89,3 3,3.9 3,5V19A2,2 0 0,0 5,21H19A2,2 0 0,0 21,19V12H19V19Z", fill, width, height);
    }

    public static Node triangle(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M1,21H23L12,2", fill, width, height);
    }

    public static Node home(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M10,20V14H14V20H19V12H22L12,3L2,12H5V20H10Z", fill, width, height);
    }

    public static Node viewList(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M7,5H21V7H7V5M7,13V11H21V13H7M4,4.5A1.5,1.5 0 0,1 5.5,6A1.5,1.5 0 0,1 4,7.5A1.5,1.5 0 0,1 2.5,6A1.5,1.5 0 0,1 4,4.5M4,10.5A1.5,1.5 0 0,1 5.5,12A1.5,1.5 0 0,1 4,13.5A1.5,1.5 0 0,1 2.5,12A1.5,1.5 0 0,1 4,10.5M7,19V17H21V19H7M4,16.5A1.5,1.5 0 0,1 5.5,18A1.5,1.5 0 0,1 4,19.5A1.5,1.5 0 0,1 2.5,18A1.5,1.5 0 0,1 4,16.5Z", fill, width, height);
    }

    public static Node check(ObjectBinding<? extends Paint> fill, double width, double height) {
        return createSVGPath("M21,7L9,19L3.5,13.5L4.91,12.09L9,16.17L19.59,5.59L21,7Z", fill, width, height);
    }
}
