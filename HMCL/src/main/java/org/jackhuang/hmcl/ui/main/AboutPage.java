/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public final class AboutPage extends SpinnerPane {

    private final WeakListenerHolder holder = new WeakListenerHolder();

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

        ComponentList environment = new ComponentList();
        {
            var system = new LinePane();
            system.setLargeTitle(true);
            system.setLeading(SVG.LUCIDE_APP_WINDOW.createIcon(32));
            system.setTitle(i18n("about.environment.system"));
            system.setSubtitle((OperatingSystem.OS_RELEASE_PRETTY_NAME == null
                    ? "%s %s (%s)".formatted(OperatingSystem.SYSTEM_NAME, OperatingSystem.SYSTEM_VERSION.getVersion(), Architecture.SYSTEM_ARCH.getDisplayName())
                    : "%s (%s %s, %s)".formatted(OperatingSystem.OS_RELEASE_PRETTY_NAME, OperatingSystem.SYSTEM_NAME, OperatingSystem.SYSTEM_VERSION.getVersion(), Architecture.SYSTEM_ARCH.getDisplayName())));
            environment.getContent().add(system);

            if (SystemInfo.getTotalMemorySize() > 0) {
                var memory = new LinePane();
                memory.setLargeTitle(true);
                memory.setLeading(SVG.LUCIDE_MEMORY_STICK.createIcon(32));
                memory.setTitle(i18n("about.environment.memory"));
                memory.setSubtitle("%.1f GiB".formatted(DataSizeUnit.GIGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize())));
                environment.getContent().add(memory);
            }

            @Nullable CentralProcessor cpu = SystemInfo.getCentralProcessor();
            if (cpu != null) {
                int cpuCount;
                int logicalPerPackage;
                int physicalPerPackage;

                CentralProcessor.Cores cores = cpu.getCores();
                if (cores != null && cores.packages() > 1) {
                    cpuCount = cores.packages();

                    // TODO: Usually, all CPUs on a multi-socket platform have the same core count, but environments
                    // like Linux allow some cores to be offline, in which case cores and threads cannot be evenly
                    // distributed. In the future, we may consider detecting the CPU topology in more detail to
                    // handle this case correctly.
                    logicalPerPackage = cores.logical() / cores.packages();
                    physicalPerPackage = cores.physical() / cores.packages();
                } else {
                    cpuCount = 1;
                    if (cores != null) {
                        logicalPerPackage = cores.logical();
                        physicalPerPackage = cores.physical();
                    } else {
                        logicalPerPackage = Runtime.getRuntime().availableProcessors();
                        physicalPerPackage = logicalPerPackage;
                    }
                }

                String cpuName;
                if (logicalPerPackage > 0 && logicalPerPackage != physicalPerPackage)
                    cpuName = i18n("about.environment.cpu.name.cores.hyper_threading", cpu.getName(), physicalPerPackage, logicalPerPackage);
                else
                    cpuName = i18n("about.environment.cpu.name.cores", cpu.getName(), physicalPerPackage);

                for (int i = 1; i <= cpuCount; i++) {
                    var button = new LinePane();
                    button.setLargeTitle(true);
                    button.setTitle(i == 1 && cpuCount == 1
                            ? i18n("about.environment.cpu")
                            : i18n("about.environment.cpu.n", i));
                    button.setSubtitle(cpuName);
                    button.setLeading(SVG.LUCIDE_CPU.createIcon(32));
                    environment.getContent().add(button);
                }
            }

            @Nullable List<GraphicsCard> graphicsCards = SystemInfo.getGraphicsCards();
            if (graphicsCards != null) {
                @Unmodifiable List<GraphicsCard> visibleGraphicsCards = graphicsCards.stream()
                        .filter(card -> !card.isVirtualDisplayAdapter())
                        .toList();
                int index = 1;
                for (GraphicsCard graphicsCard : visibleGraphicsCards) {
                    String name = index == 1 && visibleGraphicsCards.size() == 1
                            ? i18n("about.environment.gpu")
                            : i18n("about.environment.gpu.n", index);
                    index++;
                    var button = new LinePane();
                    button.setLargeTitle(true);
                    button.setTitle(name);
                    button.setSubtitle(graphicsCard.getName());

                    button.setLeading(SVG.LUCIDE_GPU.createIcon(32));

                    environment.getContent().add(button);
                }
            }

            var java = new LinePane();
            java.setLargeTitle(true);
            java.setLeading(SVG.LOCAL_CAFE.createIcon(32));
            java.setTitle("Java");
            java.setSubtitle("%s, %s, %s".formatted(
                    Runtime.version().toString(),
                    System.getProperty("java.vm.name"),
                    System.getProperty("java.vendor")));
            environment.getContent().add(java);

            var javaFX = new LinePane();
            javaFX.setLargeTitle(true);
            javaFX.setLeading(SVG.LOCAL_CAFE.createIcon(32));
            javaFX.setTitle("JavaFX");
            javaFX.setSubtitle(System.getProperty("javafx.runtime.version"));
            environment.getContent().add(javaFX);
        }

        ComponentList deps = loadIconedTwoLineList("/assets/about/deps.json");

        ComponentList legal = new ComponentList();
        {
            var copyright = new LineButton();
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
                ComponentList.createComponentListTitle(i18n("about.environment")),
                environment,
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
