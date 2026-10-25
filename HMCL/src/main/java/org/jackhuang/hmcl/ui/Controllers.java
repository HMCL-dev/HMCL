/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.account.AccountListPage;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.decorator.Decorator;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.RootPage;
import org.jackhuang.hmcl.ui.terracotta.TerracottaPage;
import org.jackhuang.hmcl.ui.instances.GameListPage;
import org.jackhuang.hmcl.ui.instances.GameInstancePage;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.setting.SettingsManager.getAuthlibInjectorServers;
import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.setting.SettingsManager.userState;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Provides shared launcher pages, main-window lifecycle operations, and dialogs.
@NotNullByDefault
public final class Controllers {
    /// The shown-tip key for the minimum supported Java version.
    public static final String JAVA_VERSION_TIP = "javaVersion";
    /// The shown-tip key for the disabled-JIT warning.
    public static final String JAVA_INTERPRETED_MODE_TIP = "javaInterpretedMode";
    /// The shown-tip key for the software-rendering warning.
    public static final String SOFTWARE_RENDERING = "softwareRendering";
    /// The shown-tip key storing the year of the April Fools prompt.
    public static final String APRIL_FOOLS = "aprilFools";

    /// The coordinator for the main window's scene graph and navigation stack.
    private static @Nullable Decorator decorator;

    /// Prevents instantiation of this utility class.
    private Controllers() {
    }

    /// The shared launcher-title property.
    private static @Nullable StringProperty launcherTitle;

    /// Returns the shared launcher-title property.
    ///
    /// For official builds, first access checks integrity, asynchronously if no result is cached.
    /// Failed verification changes the title to indicate a modified build; asynchronous results
    /// are applied on the JavaFX application thread.
    public static ReadOnlyStringProperty launcherTitleProperty() {
        if (launcherTitle == null) {
            launcherTitle = new SimpleStringProperty(Controllers.class, "launcherTitle", Metadata.OFFICIAL_BUILD
                    ? i18n("launcher.title", Metadata.VERSION)
                    : i18n("launcher.title.unofficial", Metadata.VERSION));
            if (Metadata.OFFICIAL_BUILD) {
                @Nullable Boolean selfVerified = IntegrityChecker.getSelfVerified();
                if (selfVerified != null) {
                    if (!selfVerified) {
                        launcherTitle.set(i18n("launcher.title.modified", Metadata.VERSION));
                    }
                } else {
                    Schedulers.io().execute(() -> {
                        if (!IntegrityChecker.isSelfVerified()) {
                            FXUtils.runInFX(() -> {
                                //noinspection DataFlowIssue
                                launcherTitle.set(i18n("launcher.title.modified", Metadata.VERSION));
                            });
                        }
                    });
                }
            }
        }

        return launcherTitle;
    }

    /// Returns the stage currently attached to the main-window decorator.
    ///
    /// @return the current stage, or `null` before initialization or after detachment
    public static @Nullable Stage getStage() {
        @Nullable Decorator currentDecorator = decorator;
        return currentDecorator == null ? null : currentDecorator.getStage();
    }

    /// The cached game-instance page.
    private static @Nullable GameInstancePage gameInstancePage;

    /// Creates and attaches the game-instance page for a layout pulse unless already cached.
    ///
    /// @throws NullPointerException if the page is absent and the main-window decorator is unavailable
    @FXThread
    public static void prepareGameInstancePage() {
        if (gameInstancePage == null) {
            gameInstancePage = getDecorator().preparePage(new GameInstancePage());
        }
    }

    /// Returns the cached game-instance page, creating it if necessary.
    @FXThread
    public static GameInstancePage getGameInstancePage() {
        if (gameInstancePage == null) {
            gameInstancePage = new GameInstancePage();
        }
        return gameInstancePage;
    }

    /// The cached game-list page.
    private static @Nullable GameListPage gameListPage;

    /// Returns the cached game-list page, creating it if necessary.
    @FXThread
    public static GameListPage getGameListPage() {
        if (gameListPage == null) {
            gameListPage = new GameListPage();
        }
        return gameListPage;
    }

    /// The cached root page.
    private static @Nullable RootPage rootPage;

    /// Returns the cached root page, creating it if necessary.
    @FXThread
    public static RootPage getRootPage() {
        if (rootPage == null) {
            rootPage = new RootPage();
        }
        return rootPage;
    }

    /// The cached settings page.
    private static @Nullable LauncherSettingsPage settingsPage;

    /// Creates and attaches the settings page for a layout pulse unless already cached.
    ///
    /// @throws NullPointerException if the page is absent and the main-window decorator is unavailable
    @FXThread
    public static void prepareSettingsPage() {
        if (settingsPage == null) {
            settingsPage = getDecorator().preparePage(new LauncherSettingsPage());
        }
    }

    /// Returns the cached settings page, creating it if necessary.
    @FXThread
    public static LauncherSettingsPage getSettingsPage() {
        if (settingsPage == null) {
            settingsPage = new LauncherSettingsPage();
        }
        return settingsPage;
    }

    /// The cached account page with shared account bindings.
    private static @Nullable AccountListPage accountListPage;

    /// Returns the cached account page, creating it if necessary.
    @FXThread
    public static AccountListPage getAccountListPage() {
        if (accountListPage == null) {
            accountListPage = new AccountListPage();
            accountListPage.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty());
            accountListPage.accountsProperty().bindContent(Accounts.getAccounts());
            accountListPage.authServersProperty().bindContentBidirectional(getAuthlibInjectorServers());
        }
        return accountListPage;
    }

    /// The cached download page.
    private static @Nullable DownloadPage downloadPage;

    /// Creates and attaches the download page for a layout pulse unless already cached.
    ///
    /// @throws NullPointerException if the page is absent and the main-window decorator is unavailable
    @FXThread
    public static void prepareDownloadPage() {
        if (downloadPage == null) {
            downloadPage = getDecorator().preparePage(new DownloadPage());
        }
    }

    /// Returns the cached download page, creating it if necessary.
    @FXThread
    public static DownloadPage getDownloadPage() {
        if (downloadPage == null) {
            downloadPage = new DownloadPage();
        }
        return downloadPage;
    }

    /// The cached Terracotta page.
    private static @Nullable TerracottaPage terracottaPage;

    /// Returns the cached Terracotta page, creating it if necessary.
    @FXThread
    public static Node getTerracottaPage() {
        if (terracottaPage == null) {
            terracottaPage = new TerracottaPage();
        }
        return terracottaPage;
    }

    /// Returns the initialized main-window decorator.
    ///
    /// @throws NullPointerException if the decorator is unavailable before initialization or after shutdown
    @FXThread
    public static Decorator getDecorator() {
        return Objects.requireNonNull(decorator, "Main window is not initialized");
    }

    /// Detaches the main stage and releases its stage-specific listeners.
    ///
    /// Retains the decorator and pages, leaving [#isStopped()] unchanged. Does nothing if no decorator exists.
    ///
    /// @throws IllegalStateException if a decorator exists and the caller is not on the JavaFX application thread
    public static void onApplicationStop() {
        if (decorator != null) {
            decorator.detachStage();
        }
    }

    /// Configures the main stage's application identity, shutdown action, and taskbar relaunch information.
    ///
    /// @param stage the unshown main stage
    private static void configureMainStage(Stage stage) {
        stage.setOnCloseRequest(event -> Launcher.stopApplication());
        FXUtils.setIcon(stage);
        stage.titleProperty().bind(launcherTitleProperty());
        WindowsNativeUtils.installWindowsAppUserModelRelaunchProperties(stage);
    }

    /// Initializes the main application stage, scene graph, and background services.
    ///
    /// @param stage the primary application stage, which must not have been shown
    public static void initialize(Stage stage) {
        LOG.info("Start initializing application");

        LOG.info("April Fools: " + AprilFools.isEnabled());

        if (System.getProperty("prism.lcdtext") == null) {
            @Nullable String fontAntiAliasing = SettingsManager.userSettings().fontAntiAliasingProperty().get();
            if ("lcd".equalsIgnoreCase(fontAntiAliasing)) {
                LOG.info("Enable sub-pixel antialiasing");
                System.getProperties().put("prism.lcdtext", "true");
            } else if ("gray".equalsIgnoreCase(fontAntiAliasing)
                    || OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && Screen.getPrimary().getOutputScaleX() > 1) {
                LOG.info("Disable sub-pixel antialiasing");
                System.getProperties().put("prism.lcdtext", "false");
            }
        }

        configureMainStage(stage);

        decorator = new Decorator(getRootPage(), Controllers::configureMainStage);
        Scene mainScene = decorator.attachStage(stage);
        getRootPage().getMainPage().showUpdateProperty().bind(UpdateChecker.checkingUpdateProperty().not().and(UpdateChecker.outdatedProperty()));
        getRootPage().getMainPage().showUpdateDialogProperty().bind(
                decorator.backableProperty().not()
                        .and(getRootPage().getMainPage().showUpdateProperty())
                        .and(settings().disableAutoShowUpdateDialogProperty().not())
        );

        if (settings().commonDirectoryTypeProperty().get() == EnumCommonDirectory.CUSTOM &&
                !FileUtils.canCreateDirectory(settings().getResolvedCommonDirectory())) {
            settings().commonDirectoryTypeProperty().set(EnumCommonDirectory.DEFAULT);
            dialog(i18n("launcher.cache_directory.invalid"));
        }

        Lang.thread(JavaManager::initialize, "Search Java", true);

        StyleSheets.init(mainScene);

        if (!Architecture.SYSTEM_ARCH.isX86() && SettingsManager.userState().platformPromptVersionProperty().get() < 1) {
            Runnable continueAction = () -> {
                UserState userState = userState();
                userState.platformPromptVersionProperty().set(1);
            };

            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS && Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                continueAction.run();
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                Controllers.dialog(i18n("fatal.unsupported_platform.windows_arm64"), null, MessageType.INFO, continueAction);
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX &&
                    (Architecture.SYSTEM_ARCH == Architecture.LOONGARCH64
                            || Architecture.SYSTEM_ARCH == Architecture.LOONGARCH64_OW
                            || Architecture.SYSTEM_ARCH == Architecture.MIPS64EL)) {
                Controllers.dialog(i18n("fatal.unsupported_platform.loongarch"), null, MessageType.INFO, continueAction);
            } else {
                Controllers.dialog(i18n("fatal.unsupported_platform"), null, MessageType.WARNING, continueAction);
            }
        }

        if (JavaRuntime.CURRENT_VERSION < Metadata.MINIMUM_SUPPORTED_JAVA_VERSION) {
            @Nullable Number shownTipVersion = null;
            try {
                shownTipVersion = (Number) state().getShownTips().get(JAVA_VERSION_TIP);
            } catch (ClassCastException e) {
                LOG.warning("Invalid type for shown tips key: " + JAVA_VERSION_TIP, e);
            }
            if (shownTipVersion == null || shownTipVersion.intValue() < Metadata.MINIMUM_SUPPORTED_JAVA_VERSION) {
                MessageDialogPane.Builder builder = new MessageDialogPane.Builder(i18n("fatal.deprecated_java_version"), null, MessageType.WARNING);
                @Nullable String downloadLink = Metadata.getSuggestedJavaDownloadLink();
                if (downloadLink != null)
                    builder.addHyperLink(
                            i18n("fatal.deprecated_java_version.download_link", Metadata.RECOMMENDED_JAVA_VERSION),
                            downloadLink
                    );
                Controllers.dialog(builder
                        .ok(() -> state().getShownTips().put(JAVA_VERSION_TIP, Metadata.MINIMUM_SUPPORTED_JAVA_VERSION))
                        .build());
            }
        }

        // Check whether JIT is enabled in the current environment
        if (!JavaRuntime.CURRENT_JIT_ENABLED && !Boolean.TRUE.equals(state().getShownTips().get(JAVA_INTERPRETED_MODE_TIP))) {
            Controllers.dialog(new MessageDialogPane.Builder(i18n("warning.java_interpreted_mode"), i18n("message.warning"), MessageType.WARNING)
                    .ok(null)
                    .addCancel(i18n("button.do_not_show_again"), () ->
                            state().getShownTips().put(JAVA_INTERPRETED_MODE_TIP, true))
                    .build());
        }

        // Check whether hardware acceleration is enabled
        if (!FXUtils.GPU_ACCELERATION_ENABLED && !Boolean.TRUE.equals(state().getShownTips().get(SOFTWARE_RENDERING))) {
            Controllers.dialog(new MessageDialogPane.Builder(i18n("warning.software_rendering"), i18n("message.warning"), MessageType.WARNING)
                    .ok(null)
                    .addCancel(i18n("button.do_not_show_again"), () ->
                            state().getShownTips().put(SOFTWARE_RENDERING, true))
                    .build());
        }

        if (SettingsManager.userState().agreementVersionProperty().get() < 1) {
            JFXDialogLayout agreementPane = new JFXDialogLayout();
            agreementPane.setHeading(new Label(i18n("launcher.agreement")));
            agreementPane.setBody(new Label(i18n("launcher.agreement.hint")));
            JFXHyperlink agreementLink = new JFXHyperlink(i18n("launcher.agreement"));
            agreementLink.setExternalLink(Metadata.EULA_URL);
            JFXButton yesButton = new JFXButton(i18n("launcher.agreement.accept"));
            yesButton.getStyleClass().add("dialog-accept");
            yesButton.setOnAction(e -> {
                UserState userState = userState();
                userState.agreementVersionProperty().set(1);
                agreementPane.fireEvent(new DialogCloseEvent());
            });
            JFXButton noButton = new JFXButton(i18n("launcher.agreement.decline"));
            noButton.getStyleClass().add("dialog-cancel");
            noButton.setOnAction(e -> javafx.application.Platform.exit());
            agreementPane.setActions(agreementLink, yesButton, noButton);
            Controllers.dialog(agreementPane);
        }

        aprilFools:
        if (AprilFools.isEnabled()) {
            int currentYear = LocalDate.now().getYear();
            if (state().getShownTips().get(APRIL_FOOLS) instanceof Number year && year.intValue() >= currentYear)
                break aprilFools;

            if (!I18n.getLocale().getLocale().getLanguage().equals("zh"))
                break aprilFools;

            @Nullable SupportedLocale lzh = SupportedLocale.getSupportedLocales().stream()
                    .filter(locale -> "lzh".equals(locale.getName()))
                    .findFirst().orElse(null);

            if (lzh == null) {
                LOG.warning("No supported locale found for lzh");
                break aprilFools;
            }

            Runnable updateShowTips = () -> state().getShownTips().put(APRIL_FOOLS, currentYear);

            Controllers.confirmWithCountdown(i18n("launcher.april_fools.switch_lzh"), null, 10,
                    MessageType.QUESTION, () -> {
                        Controllers.confirm(i18n("launcher.april_fools.switch_lzh.confirm"), null, MessageType.QUESTION, () -> {
                            LOG.info("Switching locale to " + lzh);

                            updateShowTips.run();
                            settings().languageProperty().set(lzh);

                            Controllers.onApplicationStop();

                            try {
                                FileSaver.waitForAllSaves();
                            } catch (InterruptedException ignored) {
                                // Ignore
                            }

                            try {
                                Restarter.restartSelf();
                            } catch (IOException e) {
                                LOG.warning("Failed to restart self", e);
                            }

                            Platform.exit();
                        }, updateShowTips);
                    }, updateShowTips);
        }

        tryInstallBundledModpack(GameDirectoryManager.getSelectedRepository());
    }

    /// Installs the package found by [Metadata#findBundledModpackFile()] asynchronously, if present.
    ///
    /// Shows progress without a cancel action and attempts to delete the package after success.
    /// Failures are logged; a retained package may be retried at the next startup.
    private static void tryInstallBundledModpack(HMCLGameRepository repository) {
        @Nullable Path modpackFile = Metadata.findBundledModpackFile();
        if (modpackFile == null) {
            return;
        }

        LOG.info("Found bundled modpack at " + modpackFile + "; starting automatic install");

        Controllers.taskDialog(
                Task.composeAsync(Schedulers.io(), () -> {
                            Modpack modpack = ModpackHelper.readModpackManifest(modpackFile);
                            return ModpackHelper.getInstallTask(
                                    repository, modpackFile, new GameInstanceID(modpack.getName()), modpack, null);
                        })
                        .whenComplete(Schedulers.javafx(), (ignored, exception) -> {
                            if (exception != null) {
                                LOG.warning("Failed to install bundled modpack", exception);
                                return;
                            }
                            try {
                                Files.deleteIfExists(modpackFile);
                            } catch (IOException e) {
                                LOG.warning("Failed to delete bundled modpack: " + modpackFile, e);
                            }
                        }), i18n("modpack.installing"), TaskCancellationAction.NO_CANCEL
        );
    }

    /// Shows the content in the main-window dialog stack without waiting for it to close.
    ///
    /// Does nothing if no decorator exists. If the decorator has no scene, display is deferred
    /// to the JavaFX application thread; otherwise this method must be called on that thread.
    ///
    /// @param content the dialog content
    /// @throws IllegalStateException if the decorator has a scene and the caller is not on the JavaFX application thread
    public static void dialog(Region content) {
        if (decorator != null)
            decorator.showDialog(content);
    }

    /// Shows an informational message with the default title; see [#dialog(String, String)].
    public static void dialog(String text) {
        dialog(text, null);
    }

    /// Shows a message using [#dialog(String, String, MessageType)] with [MessageType#INFO].
    public static void dialog(String text, @Nullable String title) {
        dialog(text, title, MessageType.INFO);
    }

    /// Shows a message using [#dialog(String, String, MessageType, Runnable)] with no OK action.
    public static void dialog(String text, @Nullable String title, MessageType type) {
        dialog(text, title, type, null);
    }

    /// Shows a message with an OK button and an optional action.
    ///
    /// A null title uses the message type's default title. Activating OK, including through Escape,
    /// invokes the action if non-null. See [#dialog(Region)].
    public static void dialog(String text, @Nullable String title, MessageType type, @Nullable Runnable ok) {
        dialog(new MessageDialogPane.Builder(text, title, type).ok(ok).build());
    }

    /// Shows a question using [#confirm(String, String, MessageType, Runnable, Runnable)] with [MessageType#QUESTION].
    public static void confirm(String text, @Nullable String title, @Nullable Runnable yes, @Nullable Runnable no) {
        confirm(text, title, MessageType.QUESTION, yes, no);
    }

    /// Shows a message with Yes and No buttons and optional actions.
    ///
    /// A null title uses the message type's default title. Each button invokes its corresponding
    /// action if non-null; Escape activates No. See [#dialog(Region)].
    public static void confirm(String text, @Nullable String title, MessageType type, @Nullable Runnable yes, @Nullable Runnable no) {
        dialog(new MessageDialogPane.Builder(text, title, type).yesOrNo(yes, no).build());
    }

    /// Shows a warning that confirms backing up a read-only settings file before overwriting it.
    ///
    /// @param text      the file-specific read-only warning
    /// @param overwrite the action that backs up and overwrites the file
    public static void confirmBackupAndOverwrite(String text, ExceptionalRunnable<?> overwrite) {
        dialog(new MessageDialogPane.Builder(
                text + "\n\n" + i18n("settings.file.force_write.confirm"),
                i18n("message.warning"),
                MessageType.WARNING)
                .addAction(i18n("settings.file.force_write"), () -> {
                    try {
                        overwrite.run();
                    } catch (Exception e) {
                        LOG.warning("Failed to force overwrite settings file", e);
                        dialog(i18n("message.failed") + "\n\n" + StringUtils.getStackTrace(e),
                                i18n("message.error"),
                                MessageType.ERROR);
                    }
                })
                .addCancel(null)
                .build());
    }

    /// Shows a message using [#confirmAction(String, String, MessageType, ButtonBase, Runnable)] with no cancel action.
    public static void confirmAction(String text, @Nullable String title, MessageType type, ButtonBase actionButton) {
        dialog(new MessageDialogPane.Builder(text, title, type).actionOrCancel(actionButton, null).build());
    }

    /// Shows a message with the supplied action button and a Cancel button.
    ///
    /// A null title uses the message type's default title. Cancel or Escape invokes the optional cancel action.
    /// The action button keeps its handler and gains a dialog-close handler. See [#dialog(Region)].
    public static void confirmAction(String text, @Nullable String title, MessageType type, ButtonBase actionButton, @Nullable Runnable cancel) {
        dialog(new MessageDialogPane.Builder(text, title, type).actionOrCancel(actionButton, cancel).build());
    }

    /// Shows a confirmation message whose OK button is enabled after a countdown.
    ///
    /// The countdown starts at this call; enabling OK follows JavaFX animation timing.
    /// Cancel or Escape stops the countdown and invokes the optional cancel action.
    /// A null title uses the message type's default title; the OK action may be null. See [#dialog(Region)].
    ///
    /// @param seconds the positive countdown duration, in seconds
    /// @throws IllegalArgumentException if the number of seconds is not positive
    public static void confirmWithCountdown(String text, @Nullable String title, int seconds, MessageType messageType,
                                            @Nullable Runnable ok, @Nullable Runnable cancel) {
        if (seconds <= 0)
            throw new IllegalArgumentException("Seconds must be greater than 0");

        JFXButton btnOk = new JFXButton(i18n("button.ok"));
        btnOk.getStyleClass().add(messageType == MessageType.WARNING || messageType == MessageType.ERROR
                ? "dialog-error"
                : "dialog-accept");

        if (ok != null)
            btnOk.setOnAction(e -> ok.run());
        btnOk.setDisable(true);

        KeyFrame[] keyFrames = new KeyFrame[seconds + 1];
        for (int i = 0; i < seconds; i++) {
            keyFrames[i] = new KeyFrame(Duration.seconds(i),
                    new KeyValue(btnOk.textProperty(), i18n("button.ok.countdown", seconds - i)));
        }
        keyFrames[seconds] = new KeyFrame(Duration.seconds(seconds),
                new KeyValue(btnOk.textProperty(), i18n("button.ok")),
                new KeyValue(btnOk.disableProperty(), false));

        Timeline timeline = new Timeline(keyFrames);
        confirmAction(text, title, messageType, btnOk, () -> {
            timeline.stop();
            if (cancel != null)
                cancel.run();
        });
        timeline.play();
    }

    /// Queues content until the dialog stack closes, or shows it if no dialog is open.
    ///
    /// Uses the availability and thread rules of [#dialog(Region)].
    public static void dialogLater(Region content) {
        if (decorator != null)
            decorator.showDialogLater(content);
    }

    /// Shows a text prompt using [#prompt(String, FutureCallback, String, ValidatorBase...)] with empty input and no validators.
    public static CompletableFuture<String> prompt(@Nullable String title, FutureCallback<String> onResult) {
        return prompt(title, onResult, "");
    }

    /// Shows a text prompt and returns a future for its accepted text.
    ///
    /// Accepting invokes the callback. Resolving completes the future with the text at resolution time
    /// and closes the dialog; rejecting displays the reason without completing the future.
    /// Closing alone does not complete or cancel the future; a pending callback may still resolve it.
    /// Without a decorator, the future remains incomplete. See [#dialog(Region)].
    ///
    /// @param title the prompt title, or null for no title text
    /// @param onResult the callback that accepts or rejects the entered text
    /// @param initialValue the initial text, or null for an empty field
    /// @param validators the input validators, or a null or empty array for none
    public static CompletableFuture<String> prompt(@Nullable String title, FutureCallback<String> onResult, @Nullable String initialValue, ValidatorBase @Nullable ... validators) {
        InputDialogPane pane = new InputDialogPane(title, initialValue, onResult, validators);
        dialog(pane);
        return pane.getCompletableFuture();
    }

    /// Shows questions that directly update the builder's question objects.
    ///
    /// The future completes with the builder's mutable question list when its callback resolves.
    /// Rejection displays the reason; closing neither completes nor cancels the future,
    /// and a pending callback may still resolve it.
    /// Without a decorator, the future remains incomplete. See [#dialog(Region)].
    public static CompletableFuture<List<PromptDialogPane.Builder.Question<?>>> prompt(PromptDialogPane.Builder builder) {
        PromptDialogPane pane = new PromptDialogPane(builder);
        dialog(pane);
        return pane.getCompletableFuture();
    }

    /// Creates and returns a progress pane without starting the executor.
    ///
    /// Requests closure when the executor stops. A null title supplies no title text.
    /// The pane is connected to the executor even if no decorator exists. See [#dialog(Region)].
    public static TaskExecutorDialogPane taskDialog(TaskExecutor executor, @Nullable String title, TaskCancellationAction onCancel) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(onCancel);
        pane.setTitle(title);
        pane.setExecutor(executor);
        dialog(pane);
        return pane;
    }

    /// Creates a pane using [#taskDialog(TaskExecutor, String, TaskCancellationAction)], then starts the task's executor.
    ///
    /// Starts the executor even if the pane cannot be shown, and returns the pane.
    public static TaskExecutorDialogPane taskDialog(Task<?> task, @Nullable String title, TaskCancellationAction onCancel) {
        TaskExecutor executor = task.executor();
        TaskExecutorDialogPane pane = taskDialog(executor, title, onCancel);
        executor.start();
        return pane;
    }

    /// Navigates to the node using the standard navigation animation. Requires an initialized decorator.
    public static void navigate(Node node) {
        decorator.navigate(node, ContainerAnimations.NAVIGATION, Motion.SHORT4, Motion.EASE);
    }

    /// Navigates to the node using the forward animation. Requires an initialized decorator.
    public static void navigateForward(Node node) {
        decorator.navigate(node, ContainerAnimations.FORWARD, Motion.SHORT4, Motion.EASE);
    }

    /// Shows a toast in the main window. Requires an initialized decorator.
    public static void showToast(String content) {
        decorator.showToast(content);
    }

    /// Shows `directoryChooser` with the current main window as its owner.
    ///
    /// @param directoryChooser the chooser to show
    /// @return the selected directory, or `null` if the chooser is cancelled
    public static @Nullable Path showDialog(DirectoryChooser directoryChooser) {
        return FileUtils.toPath(directoryChooser.showDialog(getStage()));
    }

    /// Shows `fileChooser` for opening one file with the current main window as its owner.
    ///
    /// @param fileChooser the chooser to show
    /// @return the selected file, or `null` if the chooser is cancelled
    public static @Nullable Path showOpenDialog(FileChooser fileChooser) {
        return FileUtils.toPath(fileChooser.showOpenDialog(getStage()));
    }

    /// Shows `fileChooser` for saving one file with the current main window as its owner.
    ///
    /// @param fileChooser the chooser to show
    /// @return the selected file, or `null` if the chooser is cancelled
    public static @Nullable Path showSaveDialog(FileChooser fileChooser) {
        return FileUtils.toPath(fileChooser.showSaveDialog(getStage()));
    }

    /// Shows `fileChooser` for opening multiple files with the current main window as its owner.
    ///
    /// @param fileChooser the chooser to show
    /// @return the selected files, or `null` if the chooser is cancelled
    public static @Nullable List<Path> showOpenMultipleDialog(FileChooser fileChooser) {
        return FileUtils.toPaths(fileChooser.showOpenMultipleDialog(getStage()));
    }

    /// Dispatches supported hmcl links or opens other links externally; ignores unknown internal links.
    public static void onHyperlinkAction(String href) {
        if (href.startsWith("hmcl://")) {
            switch (href) {
                case "hmcl://settings/feedback":
                    Controllers.getSettingsPage().showFeedback();
                    Controllers.navigate(Controllers.getSettingsPage());
                    break;
                case "hmcl://game/launch":
                    var repository = GameDirectoryManager.getSelectedRepository();
                    Instances.launch(repository.getSelectedInstance(), LauncherHelper::setKeep);
                    break;
            }
        } else {
            FXUtils.openLink(href);
        }
    }

    /// Offers to open the URI externally or copy it to the clipboard; does nothing for null.
    public static void openUriOrCopy(@Nullable URI url) {
        if (url == null) return;
        openUriOrCopy(url.toString());
    }

    /// Offers to open the URI externally or copy it to the clipboard; does nothing for null.
    public static void openUriOrCopy(@Nullable String url) {
        if (url == null) return;
        var dialog = new MessageDialogPane.Builder(
                i18n("web.open_in_browser", url),
                i18n("message.confirm"),
                MessageDialogPane.MessageType.QUESTION
        )
                .addAction(i18n("button.copy"), () -> FXUtils.copyText(url))
                .yesOrNo(() -> FXUtils.openLink(url), null)
                .build();
        dialog(dialog);
    }

    /// Returns whether no main-window decorator is retained, including before initialization and after shutdown.
    public static boolean isStopped() {
        return decorator == null;
    }

    /// Detaches the main stage, clears cached page and decorator references, and clears the built-in image cache.
    ///
    /// The launcher-title property is retained. See [#onApplicationStop()] for detachment and thread requirements.
    public static void shutdown() {
        onApplicationStop();
        rootPage = null;
        gameInstancePage = null;
        gameListPage = null;
        downloadPage = null;
        accountListPage = null;
        settingsPage = null;
        terracottaPage = null;
        decorator = null;

        FXUtils.shutdown();
    }

    /// Whether heap trimming is enabled; disabled by an HMCL_AUTO_TRIM_HEAP value equal to false, ignoring case.
    public static final boolean AUTO_TRIM_HEAP = !"false".equalsIgnoreCase(System.getenv("HMCL_AUTO_TRIM_HEAP"));

    /// Requests garbage collection if [#AUTO_TRIM_HEAP] is enabled.
    public static void trimHeap() {
        if (AUTO_TRIM_HEAP) {
            System.gc();
        }
    }
}
