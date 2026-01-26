/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class HTMLRenderer {

    private static URI resolveLink(Node linkNode) {
        String href = linkNode.absUrl("href");
        if (href.isEmpty())
            return null;

        try {
            return new URI(href);
        } catch (Throwable e) {
            return null;
        }
    }

    public static HTMLRenderer openHyperlinkInBrowser() {
        return new HTMLRenderer(uri -> {
            var dialog =
                    new MessageDialogPane.Builder(
                            i18n("web.open_in_browser", uri),
                            i18n("message.confirm"),
                            MessageDialogPane.MessageType.QUESTION
                    )
                            .addAction(i18n("button.copy"), () -> FXUtils.copyText(uri.toString()))
                            .yesOrNo(() -> FXUtils.openLink(uri.toString()), null)
                            .build();
            Controllers.dialog(dialog);
        });
    }

    private final List<javafx.scene.Node> children = new ArrayList<>();
    private final List<Node> stack = new ArrayList<>();

    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strike;
    private boolean highlight;
    private boolean code;
    private String headerLevel;
    private Node hyperlink;
    private String fxStyle;

    private final Consumer<URI> onClickHyperlink;

    public HTMLRenderer(Consumer<URI> onClickHyperlink) {
        this.onClickHyperlink = onClickHyperlink;
    }

    private void updateStyle() {
        bold = false;
        italic = false;
        underline = false;
        strike = false;
        highlight = false;
        code = false;
        headerLevel = null;
        hyperlink = null;
        fxStyle = null;

        for (Node node : stack) {
            String nodeName = node.nodeName();
            switch (nodeName) {
                case "b", "strong" -> bold = true;
                case "i", "em" -> italic = true;
                case "ins" -> underline = true;
                case "del" -> strike = true;
                case "mark" -> highlight = true;
                case "code" -> code = true;
                case "a" -> hyperlink = node;
                case "h1", "h2", "h3", "h4", "h5", "h6" -> headerLevel = nodeName;
            }

            String style = node.attr("style");
            if (StringUtils.isNotBlank(style)) {
                fxStyle = style
                        .replace("color:", "-fx-fill:")
                        .replace("font-size:", "-fx-font-size:"); // And more
            }
        }
    }

    private void pushNode(Node node) {
        stack.add(node);
        updateStyle();
    }

    private void popNode() {
        stack.remove(stack.size() - 1);
        updateStyle();
    }

    private void applyStyle(Text text) {
        if (hyperlink != null) {
            URI target = resolveLink(hyperlink);
            if (target != null) {
                FXUtils.onClicked(text, () -> onClickHyperlink.accept(target));
                text.setCursor(Cursor.HAND);
            }
            text.getStyleClass().add("html-hyperlink");
        }

        if (hyperlink != null || underline)
            text.setUnderline(true);

        if (strike)
            text.setStrikethrough(true);

        if (bold || highlight)
            text.getStyleClass().add("html-bold");

        if (italic)
            text.getStyleClass().add("html-italic");

        if (code) {
            text.getStyleClass().add("html-code");
            text.setStyle("-fx-font-family: \"%s\";".formatted(Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT)));
        }

        if (headerLevel != null)
            text.getStyleClass().add("html-" + headerLevel);

        if (fxStyle != null)
            text.setStyle(fxStyle);
    }

    private void appendText(String text) {
        Text textNode = new Text(text);
        applyStyle(textNode);
        if (code) {
            var block = new VBox(textNode);
            block.setAlignment(Pos.CENTER);
            block.getStyleClass().add("html-code-block");
            children.add(block);
        } else {
            children.add(textNode);
        }
    }

    private void appendAutoLineBreak(String text) {
        AutoLineBreak textNode = new AutoLineBreak(text);
        applyStyle(textNode);
        children.add(textNode);
    }

    private void appendImage(Node node) {
        String src = node.absUrl("src");
        String alt = node.attr("alt");

        if (StringUtils.isNotBlank(src)) {
            String widthAttr = node.attr("width");
            String heightAttr = node.attr("height");

            int width = 0;
            int height = 0;

            if (!widthAttr.isEmpty() && !heightAttr.isEmpty()) {
                try {
                    width = (int) Double.parseDouble(widthAttr);
                    height = (int) Double.parseDouble(heightAttr);
                } catch (NumberFormatException ignored) {
                }

                if (width <= 0 || height <= 0) {
                    width = 0;
                    height = 0;
                }
            }

            try {
                Image image = FXUtils.getRemoteImageTask(src, width, height, true, true)
                        .run();
                if (image == null)
                    throw new AssertionError("Image loading task returned null");

                ImageView imageView = new ImageView(image);
                if (hyperlink != null) {
                    URI target = resolveLink(hyperlink);
                    if (target != null) {
                        FXUtils.onClicked(imageView, () -> onClickHyperlink.accept(target));
                        imageView.setCursor(Cursor.HAND);
                    }
                }
                imageView.setPreserveRatio(true);
                children.add(imageView);
                return;
            } catch (Throwable e) {
                LOG.warning("Failed to load image: " + src, e);
            }
        }

        if (!alt.isEmpty())
            appendText(alt);
    }

    private void appendTable(Node table) {
        var childNodes = table.childNodes();

        var headOptional = childNodes.stream().filter(n -> n.nameIs("thead")).findFirst();
        if (headOptional.isEmpty()) return;
        var head = (Element) headOptional.get();
        String[] headRow = head.getAllElements().stream()
                .filter(n -> n.nameIs("th"))
                .map(Element::text)
                .toArray(String[]::new);
        if (headRow.length == 0) return;

        var bodyOptional = childNodes.stream().filter(n -> n.nameIs("tbody")).findFirst();
        String[][] bodyRows;
        if (bodyOptional.isEmpty()) {
            bodyRows = new String[0][headRow.length];
        } else {
            var body = (Element) bodyOptional.get();
            var r = body.getAllElements().stream()
                    .filter(n -> n.nameIs("tr"))
                    .map(n -> n.getAllElements().stream()
                            .filter(e -> e.nameIs("td"))
                            .map(Element::text)
                            .toArray(String[]::new))
                    .toList();
            bodyRows = new String[r.size()][headRow.length];
            for (int i = 0; i < r.size(); i++) {
                bodyRows[i] = r.get(i);
            }
        }

        TableView<String[]> tableView = new TableView<>(FXCollections.observableList(Arrays.asList(bodyRows)));
        for (int i = 0; i < headRow.length; i++) {
            int finalI = i;
            TableColumn<String[], String> c = new TableColumn<>(headRow[i]);
            c.setCellValueFactory(param -> new SimpleStringProperty(param.getValue()[finalI]));
            tableView.getColumns().add(c);
        }

        children.add(tableView);
    }

    public void appendNode(Node node) {
        if (node instanceof TextNode) {
            appendText(StringUtils.removeEmptyLinesAtBeginningAndEnd(((TextNode) node).getWholeText()));
        }

        String name = node.nodeName();
        switch (name) {
            case "img" -> {
                if (!children.isEmpty())
                    appendAutoLineBreak("\n");
                appendImage(node);
                appendAutoLineBreak("\n");
            }
            case "li" -> {
                int i = 0;
                var n = node;
                while (true) {
                    n = n.parent();
                    if (n == null) break;
                    if (n.nameIs("li")) i++;
                }
                appendText("\n " + "  ".repeat(Math.max(0, i)) + "\u2022 ");
            }
            case "dt" -> appendText(" ");
            case "p" -> {
                var n = node.parent();
                if (!children.isEmpty() && (n == null || !n.nameIs("li")))
                    appendAutoLineBreak("\n\n");
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                if (!children.isEmpty())
                    appendAutoLineBreak("\n\n");
            }
        }

        if (node.childNodeSize() > 0) {
            pushNode(node);
            if ("table".equals(name)) {
                appendTable(node);
            } else {
                for (Node childNode : node.childNodes()) {
                    appendNode(childNode);
                }
            }
            popNode();
        }

        switch (name) {
            case "br", "dd", "h1", "h2", "h3", "h4", "h5", "h6" -> appendAutoLineBreak("\n");
            case "p" -> {
                var n = node.parent();
                if (n == null || !n.nameIs("li"))
                    appendAutoLineBreak("\n");
            }
        }
    }

    private static boolean isSpacing(String text) {
        if (text == null)
            return true;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != ' ' && ch != '\t')
                return false;
        }
        return true;
    }

    public void mergeLineBreaks() {
        for (int i = 0; i < this.children.size(); i++) {
            javafx.scene.Node child = this.children.get(i);
            if (child instanceof AutoLineBreak) {
                int lastAutoLineBreak = -1;

                for (int j = i + 1; j < this.children.size(); j++) {
                    javafx.scene.Node otherChild = this.children.get(j);

                    if (otherChild instanceof AutoLineBreak) {
                        lastAutoLineBreak = j;
                    } else if (otherChild instanceof Text && isSpacing(((Text) otherChild).getText())) {
                        // do nothing
                    } else {
                        break;
                    }
                }

                if (lastAutoLineBreak > 0) {
                    this.children.subList(i + 1, lastAutoLineBreak + 1).clear();

                    if (((Text) child).getText().length() == 1) {
                        ((Text) child).setText("\n\n");
                    }
                }
            }
        }
    }

    public TextFlow render() {
        TextFlow textFlow = new TextFlow();
        textFlow.getStyleClass().add("html");
        textFlow.getChildren().setAll(children);
        for (javafx.scene.Node node : children) {
            if (node instanceof ImageView img) {
                double width = img.getImage().getWidth();
                img.fitWidthProperty().bind(textFlow.widthProperty().map(d -> Math.min((double) d - 20D, width)));
            } else if (node instanceof TableView<?> table) {
                table.prefWidthProperty().bind(textFlow.widthProperty().add(-20D));
            }
        }
        return textFlow;
    }

    private static final class AutoLineBreak extends Text {
        public AutoLineBreak(String text) {
            super(text);
        }
    }
}
