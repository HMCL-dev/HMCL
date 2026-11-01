// SPDX-FileCopyrightText: Copyright (C) 2022 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import com.google.gson.*;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.DataSizeUnit;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Displays launcher, system, device, runtime, and project information.
@NotNullByDefault
public final class AboutPage extends SpinnerPane {

    /// Keeps theme listeners alive while the page is visible.
    private final WeakListenerHolder holder = new WeakListenerHolder();

    /// Builds the page from cached hardware information and bundled project lists.
    public AboutPage() {
        VBox content = new VBox();
        content.getStyleClass().add("spinner-pane-content");
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setContent(scrollPane);

        ComponentList about = new ComponentList();
        {
            var launcher = LineButton.createExternalLinkButton(Metadata.REPOSITORY_URL);
            launcher.setLargeTitle(true);
            launcher.setLeading(FXUtils.newBuiltinImage("/assets/img/icon.png"));
            launcher.setTitle("Hello Minecraft! Launcher");
            launcher.setSubtitle(Metadata.VERSION);

            about.getContent().setAll(launcher);
        }

        ComponentList systemInfo = new ComponentList();
        {
            var system = new LinePane();
            system.setLargeTitle(true);
            system.setLeading(SVG.LUCIDE_APP_WINDOW.createIcon(32));
            system.setTitle(i18n("about.system.os"));
            system.setSubtitle((OperatingSystem.OS_RELEASE_PRETTY_NAME == null
                    ? "%s %s (%s)".formatted(OperatingSystem.SYSTEM_NAME, OperatingSystem.SYSTEM_VERSION.getVersion(), Architecture.SYSTEM_ARCH.getDisplayName())
                    : "%s (%s %s, %s)".formatted(OperatingSystem.OS_RELEASE_PRETTY_NAME, OperatingSystem.SYSTEM_NAME, OperatingSystem.SYSTEM_VERSION.getVersion(), Architecture.SYSTEM_ARCH.getDisplayName())));
            systemInfo.getContent().add(system);

            if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
                @Nullable String desktopName = System.getenv("XDG_CURRENT_DESKTOP");
                if (desktopName == null || desktopName.isBlank())
                    desktopName = System.getenv("XDG_SESSION_DESKTOP");
                if (desktopName != null && !desktopName.isBlank()) {
                    var desktop = new LinePane();
                    desktop.setLargeTitle(true);
                    desktop.setLeading(SVG.LUCIDE_APP_WINDOW.createIcon(32));
                    desktop.setTitle(i18n("about.system.desktop"));
                    desktop.setSubtitle(desktopName.strip());
                    systemInfo.getContent().add(desktop);
                }
            }
        }

        ComponentList deviceInfo = new ComponentList();
        {
            @Unmodifiable List<CentralProcessor> processors = SystemInfo.getCentralProcessors();
            for (int i = 0; i < processors.size(); i++) {
                String title = processors.size() == 1
                        ? i18n("about.device.cpu")
                        : i18n("about.device.cpu.n", i + 1);
                CentralProcessor processor = processors.get(i);
                String name = processor.getName();
                @Nullable CentralProcessor.Cores cores = processor.getCores();
                String subtitle;
                if (cores == null)
                    subtitle = i18n("about.device.cpu.name", name);
                else if (cores.efficiency() > 0) {
                    subtitle = cores.logical() == cores.physical()
                            ? i18n("about.device.cpu.name.cores.hybrid", name, cores.performance(), cores.efficiency())
                            : i18n("about.device.cpu.name.cores.hybrid.threads", name, cores.performance(), cores.efficiency(), cores.logical());
                } else {
                    subtitle = cores.logical() == cores.physical()
                            ? i18n("about.device.cpu.name.cores", name, cores.physical())
                            : i18n("about.device.cpu.name.cores.threads", name, cores.physical(), cores.logical());
                }

                var pane = new LinePane();
                pane.setLargeTitle(true);
                pane.setTitle(title);
                pane.setSubtitle(subtitle);
                pane.setLeading(SVG.LUCIDE_CPU.createIcon(32));
                deviceInfo.getContent().add(pane);
            }

            if (SystemInfo.getTotalMemorySize() > 0) {
                var memory = new LinePane();
                memory.setLargeTitle(true);
                memory.setLeading(SVG.LUCIDE_MEMORY_STICK.createIcon(32));
                memory.setTitle(i18n("about.device.memory"));
                memory.setSubtitle("%.1f GiB".formatted(DataSizeUnit.GIGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize())));
                deviceInfo.getContent().add(memory);
            }

            @Unmodifiable List<GraphicsCard> visibleGraphicsCards = SystemInfo.getGraphicsCards().stream()
                    .filter(card -> card.getType() != GraphicsCard.Type.DISPLAY_ONLY && !"Unknown".equals(card.getName()))
                    .sorted(Comparator.comparing(GraphicsCard::getType))
                    .toList();

            for (int i = 0; i < visibleGraphicsCards.size(); i++) {
                GraphicsCard graphicsCard = visibleGraphicsCards.get(i);

                String title = visibleGraphicsCards.size() == 1
                        ? i18n("about.device.gpu")
                        : i18n("about.device.gpu.n", i + 1);

                var button = new LinePane();
                button.setLargeTitle(true);
                button.setTitle(title);
                button.setSubtitle(graphicsCard.toString());

                button.setLeading(SVG.LUCIDE_GPU.createIcon(32));

                deviceInfo.getContent().add(button);
            }
        }

        ComponentList runtimeInfo = new ComponentList();
        {
            var java = new LinePane();
            java.setLargeTitle(true);
            java.setLeading(SVG.LOCAL_CAFE.createIcon(32));
            java.setTitle(i18n("about.runtime.java"));
            java.setSubtitle("%s, %s, %s".formatted(
                    Runtime.version().toString(),
                    System.getProperty("java.vm.name"),
                    System.getProperty("java.vendor")));
            runtimeInfo.getContent().add(java);

            var javaFX = new LinePane();
            javaFX.setLargeTitle(true);
            javaFX.setLeading(SVG.LOCAL_CAFE.createIcon(32));
            javaFX.setTitle("JavaFX");
            javaFX.setSubtitle(System.getProperty("javafx.runtime.version"));
            runtimeInfo.getContent().add(javaFX);
        }

        ComponentList deps = loadIconedTwoLineList("/assets/about/deps.json");

        ComponentList legal = new ComponentList();
        {
            var copyright = new LinePane();
            copyright.setLargeTitle(true);
            copyright.setTitle(i18n("about.copyright"));
            copyright.setSubtitle(i18n("about.copyright.statement"));

            var claim = LineButton.createExternalLinkButton(Metadata.EULA_URL);
            claim.setLargeTitle(true);
            claim.setTitle(i18n("about.claim"));
            claim.setSubtitle(i18n("about.claim.statement"));

            var openSource = LineButton.createExternalLinkButton("https://github.com/HMCL-dev/HMCL");
            openSource.setLargeTitle(true);
            openSource.setTitle(i18n("about.open_source"));
            openSource.setSubtitle(i18n("about.open_source.statement"));

            legal.getContent().setAll(copyright, claim, openSource);
        }

        content.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("about")),
                about,
                ComponentList.createComponentListTitle(i18n("about.system")),
                systemInfo,
                ComponentList.createComponentListTitle(i18n("about.device")),
                deviceInfo,
                ComponentList.createComponentListTitle(i18n("about.runtime")),
                runtimeInfo,
                ComponentList.createComponentListTitle(i18n("about.dependency")),
                deps,
                ComponentList.createComponentListTitle(i18n("about.legal")),
                legal
        );
    }

    private static Image loadImage(String url) {
        return url.startsWith("/")
                ? FXUtils.newBuiltinImage(url)
                : new Image(url);
    }

    private ComponentList loadIconedTwoLineList(String path) {
        ComponentList componentList = new ComponentList();

        InputStream input = FXUtils.class.getResourceAsStream(path);
        if (input == null) {
            LOG.warning("Resources not found: " + path);
            return componentList;
        }

        try {
            JsonArray array = JsonUtils.fromJsonFully(input, JsonArray.class);

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();

                var button = new LineButton();
                button.setLargeTitle(true);

                if (obj.get("externalLink") instanceof JsonPrimitive externalLink) {
                    button.setTrailingIcon(SVG.OPEN_IN_NEW);

                    String link = externalLink.getAsString();
                    button.setOnAction(event -> FXUtils.openLink(link));
                }

                if (obj.has("image")) {
                    JsonElement image = obj.get("image");
                    if (image.isJsonPrimitive()) {
                        var imageView = new ImageContainer(32, 32);
                        imageView.setImage(loadImage(image.getAsString()));
                        imageView.setMouseTransparent(true);

                        button.setLeading(imageView);
                    } else if (image.isJsonObject()) {
                        holder.add(FXUtils.onWeakChangeAndOperate(Themes.darkModeProperty(), darkMode -> button.setLeading(darkMode
                                ? loadImage(image.getAsJsonObject().get("dark").getAsString())
                                : loadImage(image.getAsJsonObject().get("light").getAsString())
                        )));
                    }
                }

                if (obj.get("title") instanceof JsonPrimitive title)
                    button.setTitle(title.getAsString());
                else if (obj.get("titleLocalized") instanceof JsonPrimitive titleLocalized)
                    button.setTitle(i18n(titleLocalized.getAsString()));

                if (obj.get("subtitle") instanceof JsonPrimitive subtitle)
                    button.setSubtitle(subtitle.getAsString());
                else if (obj.get("subtitleLocalized") instanceof JsonPrimitive subtitleLocalized)
                    button.setSubtitle(i18n(subtitleLocalized.getAsString()));

                componentList.getContent().add(button);
            }
        } catch (IOException | JsonParseException e) {
            LOG.warning("Failed to load list: " + path, e);
        }

        return componentList;
    }

}
