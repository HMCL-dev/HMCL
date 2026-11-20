// SPDX-FileCopyrightText: Copyright (C) 2022 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import com.google.gson.*;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3ListItem;
import org.glavo.m3fx.controls.M3ListPane;
import org.glavo.m3fx.controls.M3ListSectionHeader;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ScrollUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.DataSizeUnit;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.DeviceInfo;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.PageFileConfiguration;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.getTranslator;
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
        ScrollUtils.installSmoothScrolling(scrollPane);
        setContent(scrollPane);

        M3ListPane about = FXUtils.newSegmentedList();
        {
            var launcher = FXUtils.newExternalLinkItem(Metadata.REPOSITORY_URL);
            launcher.setLeading(new ImageContainer(FXUtils.newBuiltinImage("/assets/img/icon.png"), 40, 40));
            launcher.setHeadlineText("Hello Minecraft! Launcher");
            launcher.setSupportingText(Metadata.VERSION);

            about.getItems().setAll(launcher);
        }

        M3ListPane systemInfo = FXUtils.newSegmentedList();
        {
            var system = newInfoItem();
            system.setLeading(SVG.LUCIDE_APP_WINDOW.createIcon(32));
            system.setHeadlineText(i18n("about.system.os"));
            system.setSupportingText(SystemInfo.getOperatingSystemDescription());
            systemInfo.getItems().add(system);

            if (SystemInfo.isXdgPreferred() && SystemInfo.getDesktopEnvironment() != null) {
                var desktop = newInfoItem();
                desktop.setLeading(SVG.LUCIDE_PANELS_TOP_LEFT.createIcon(32));
                desktop.setHeadlineText(i18n("about.system.desktop"));
                desktop.setSupportingText(SystemInfo.getDesktopEnvironment());
                systemInfo.getItems().add(desktop);
            }
        }

        M3ListPane deviceInfo = FXUtils.newSegmentedList();
        {
            @Nullable DeviceInfo deviceIdentity = SystemInfo.getDeviceInfo();
            if (deviceIdentity != null
                    && !"Virtual Machine".equals(deviceIdentity.name())
                    && SystemInfo.CURRENT_OS != OperatingSystem.WINDOWS
                    && (SystemInfo.CURRENT_OS == OperatingSystem.MACOS || !SystemInfo.SYSTEM_ARCH.isX86())) {
                var device = newInfoItem();
                device.setLeading(SVG.HOST.createIcon(32));
                device.setHeadlineText(i18n("about.device.model"));
                device.setSupportingText(getTranslator().formatDeviceName(deviceIdentity));
                deviceInfo.getItems().add(device);
            }

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

                var pane = newInfoItem();
                pane.setHeadlineText(title);
                pane.setSupportingText(subtitle);
                pane.setLeading(SVG.LUCIDE_CPU.createIcon(32));
                pane.setTrailingSupportingText(SystemInfo.SYSTEM_ARCH.getDisplayName());
                deviceInfo.getItems().add(pane);
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

                var button = newInfoItem();
                button.setLeading(SVG.LUCIDE_GPU.createIcon(32));
                button.setHeadlineText(title);

                StringBuilder subtitle = new StringBuilder(graphicsCard.getName());

                if (graphicsCard.getDriver() != null || graphicsCard.getDriverVersion() != null) {
                    subtitle.append(" (");
                    if (graphicsCard.getDriver() != null) {
                        subtitle.append(graphicsCard.getDriver());
                        if (graphicsCard.getDriverVersion() != null) {
                            subtitle.append(' ');
                        }
                    }
                    if (graphicsCard.getDriverVersion() != null) {
                        subtitle.append(graphicsCard.getDriverVersion());
                    }
                    subtitle.append(')');
                }

                button.setSupportingText(subtitle.toString());

                switch (graphicsCard.getType()) {
                    case INTEGRATED -> button.setTrailingSupportingText(i18n("about.device.gpu.type.integrated"));
                    case DISCRETE -> button.setTrailingSupportingText(i18n("about.device.gpu.type.discrete"));
                    case VIRTUAL -> button.setTrailingSupportingText(i18n("about.device.gpu.type.virtual"));
                }

                deviceInfo.getItems().add(button);
            }

            if (SystemInfo.getTotalMemorySize() > 0) {
                String subtitle = i18n("about.device.memory.system",
                        "%.1f GiB".formatted(DataSizeUnit.GIGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize())));
                if (SystemInfo.CURRENT_OS == OperatingSystem.WINDOWS) {
                    long pageFileSize = SystemInfo.getTotalPageFileSize();
                    if (pageFileSize < 0) {
                        subtitle = i18n("about.device.memory.page_files.unknown", subtitle);
                    } else {
                        String pageFileCapacity = "%.1f GiB".formatted(DataSizeUnit.GIGABYTES.convertFromBytes(pageFileSize));
                        PageFileConfiguration configuration = SystemInfo.getPageFileConfiguration();
                        subtitle = switch (configuration.mode()) {
                            case MANUAL -> i18n("about.device.memory.page_files.maximum",
                                    subtitle,
                                    pageFileCapacity,
                                    "%.1f GiB".formatted(DataSizeUnit.GIGABYTES.convertFromBytes(configuration.maximumSize())));
                            case SYSTEM_MANAGED, MIXED, UNKNOWN -> i18n("about.device.memory.page_files", subtitle, pageFileCapacity);
                        };
                    }
                }

                var memory = newInfoItem();
                memory.setLeading(SVG.LUCIDE_MEMORY_STICK.createIcon(32));
                memory.setHeadlineText(i18n("about.device.memory"));
                memory.setSupportingText(subtitle);
                deviceInfo.getItems().add(memory);
            }
        }

        M3ListPane runtimeInfo = FXUtils.newSegmentedList();
        {
            var java = newInfoItem();
            java.setLeading(SVG.LOCAL_CAFE.createIcon(32));
            java.setHeadlineText("Java");
            java.setSupportingText("%s, %s, %s".formatted(
                    Runtime.version().toString(),
                    System.getProperty("java.vm.name"),
                    System.getProperty("java.vendor")));
            runtimeInfo.getItems().add(java);

            var javaFX = newInfoItem();
            javaFX.setLeading(SVG.LOCAL_CAFE.createIcon(32));
            javaFX.setHeadlineText("JavaFX");
            javaFX.setSupportingText(System.getProperty("javafx.runtime.version"));
            runtimeInfo.getItems().add(javaFX);
        }

        M3ListPane deps = loadIconedTwoLineList("/assets/about/deps.json");

        M3ListPane legal = FXUtils.newSegmentedList();
        {
            var copyright = newInfoItem();
            copyright.setHeadlineText(i18n("about.copyright"));
            copyright.setSupportingText(i18n("about.copyright.statement"));

            var claim = FXUtils.newExternalLinkItem(Metadata.EULA_URL);
            claim.setHeadlineText(i18n("about.claim"));
            claim.setSupportingText(i18n("about.claim.statement"));

            var openSource = FXUtils.newExternalLinkItem("https://github.com/HMCL-dev/HMCL");
            openSource.setHeadlineText(i18n("about.open_source"));
            openSource.setSupportingText(i18n("about.open_source.statement"));

            legal.getItems().setAll(copyright, claim, openSource);
        }

        content.getChildren().setAll(
                new M3ListSectionHeader(i18n("about")),
                about,
                new M3ListSectionHeader(i18n("about.system")),
                systemInfo,
                new M3ListSectionHeader(i18n("about.device")),
                deviceInfo,
                new M3ListSectionHeader(i18n("about.runtime")),
                runtimeInfo,
                new M3ListSectionHeader(i18n("about.dependency")),
                deps,
                new M3ListSectionHeader(i18n("about.legal")),
                legal
        );
    }

    /// Creates a passive row for read-only system and legal information.
    private static M3ListItem newInfoItem() {
        var item = new M3ListItem();
        item.setFocusTraversable(false);
        item.setMouseTransparent(true);
        return item;
    }

    private static Image loadImage(String url) {
        return url.startsWith("/")
                ? FXUtils.newBuiltinImage(url)
                : new Image(url);
    }

    /// Builds the dependency list from its bundled descriptions, links, and themed icons.
    private M3ListPane loadIconedTwoLineList(String path) {
        M3ListPane componentList = FXUtils.newSegmentedList();

        InputStream input = FXUtils.class.getResourceAsStream(path);
        if (input == null) {
            LOG.warning("Resources not found: " + path);
            return componentList;
        }

        try {
            JsonArray array = JsonUtils.fromJsonFully(input, JsonArray.class);

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();

                var button = obj.get("externalLink") instanceof JsonPrimitive externalLink
                        ? FXUtils.newExternalLinkItem(externalLink.getAsString())
                        : newInfoItem();

                if (obj.has("image")) {
                    JsonElement image = obj.get("image");
                    if (image.isJsonPrimitive()) {
                        var imageView = new ImageContainer(32, 32);
                        imageView.setImage(loadImage(image.getAsString()));
                        imageView.setMouseTransparent(true);

                        button.setLeading(imageView);
                    } else if (image.isJsonObject()) {
                        var imageView = new ImageContainer(32);
                        button.setLeading(imageView);
                        holder.add(FXUtils.onWeakChangeAndOperate(Themes.darkModeProperty(), darkMode ->
                                imageView.setImage(loadImage(image.getAsJsonObject().get(darkMode ? "dark" : "light").getAsString()))));
                    }
                }

                if (obj.get("title") instanceof JsonPrimitive title)
                    button.setHeadlineText(title.getAsString());
                else if (obj.get("titleLocalized") instanceof JsonPrimitive titleLocalized)
                    button.setHeadlineText(i18n(titleLocalized.getAsString()));

                if (obj.get("subtitle") instanceof JsonPrimitive subtitle)
                    button.setSupportingText(subtitle.getAsString());
                else if (obj.get("subtitleLocalized") instanceof JsonPrimitive subtitleLocalized)
                    button.setSupportingText(i18n(subtitleLocalized.getAsString()));

                componentList.getItems().add(button);
            }
        } catch (IOException | JsonParseException e) {
            LOG.warning("Failed to load list: " + path, e);
        }

        return componentList;
    }

}
