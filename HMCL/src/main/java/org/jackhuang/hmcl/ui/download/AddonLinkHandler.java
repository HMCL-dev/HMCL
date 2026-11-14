// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import javafx.stage.FileChooser;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.addon.AddonProjectInfo;
import org.jackhuang.hmcl.addon.AddonProjectInfoRegistry;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
import org.jackhuang.hmcl.ui.instances.DownloadListPage;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.util.FutureCallback;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Recognizes addon-related links dropped onto the launcher or entered manually.
///
/// Modrinth and mcmod project pages are resolved to a remote addon and opened in the
/// in-launcher detail page. CurseForge project pages cannot be resolved without an
/// additional repository lookup and are reported as unresolvable. Any other link is
/// probed with an HTTP request and, when it points to a downloadable file, offered for
/// saving or installation as a mod, modpack or game instance.
@NotNullByDefault
public final class AddonLinkHandler {

    /// Matches the first HTTP(S) address inside arbitrary dropped text.
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    /// Matches a mcmod project page such as `/class/123.html` or `/modpack/456.html`.
    private static final Pattern MCMOD_PATH_PATTERN = Pattern.compile("^/(?<type>class|modpack)/(?<id>\\d+)\\.html$");

    /// Matches the redirect path used by mcmod's "related links" feature.
    private static final Pattern MCMOD_LINK_PATH_PATTERN = Pattern.compile("^/target/(?<target>.+)$");

    /// Matches a Modrinth project page.
    ///
    /// The pattern follows the metadata generator so that links recognized here stay
    /// consistent with the generated project tables.
    private static final Pattern MODRINTH_PATH_PATTERN = Pattern.compile(
            "^/(?<type>[a-zA-Z0-9-]+)/(?<slug>[^/]+)(?:/.*)?$");

    /// The base64 decoders tried when decoding the payload of a mcmod redirect.
    private static final @Unmodifiable List<Base64.Decoder> BASE64_DECODERS =
            List.of(Base64.getDecoder(), Base64.getUrlDecoder());

    /// Reports resolution failures through an error dialog and ignores successes.
    private static final FutureCallback.ResultHandler DROP_RESULT_HANDLER = new FutureCallback.ResultHandler() {
        @Override
        public void resolve() {
        }

        @Override
        public void reject(String reason) {
            showError(reason);
        }
    };

    /// Prevents instantiation of this utility class.
    private AddonLinkHandler() {
    }

    /// Opens the link dialog with an empty input field.
    public static void promptInput() {
        promptInput("");
    }

    /// Opens the link dialog with the given initial text.
    ///
    /// The accept button shows a spinner while the link is resolved, and the dialog closes
    /// only after resolution succeeds.
    ///
    /// @param initialValue the text initially shown in the input field, possibly `null`
    public static void promptInput(@Nullable String initialValue) {
        PromptDialogPane.Builder.StringQuestion input = new PromptDialogPane.Builder.StringQuestion(
                "", initialValue != null ? initialValue : "");
        PromptDialogPane.Builder builder = new PromptDialogPane.Builder(
                i18n("download.link.title"),
                (questions, handler) -> handle(input.getValue(), handler));
        builder.addQuestion(new PromptDialogPane.Builder.HintQuestion(i18n("download.link.prompt")));
        builder.addQuestion(input);
        Controllers.prompt(builder);
    }

    /// Handles a dropped link immediately without opening the input dialog.
    ///
    /// Resolution failures are reported through an error dialog.
    ///
    /// @param rawText the dropped text, possibly `null`
    public static void handle(@Nullable String rawText) {
        handle(rawText, DROP_RESULT_HANDLER);
    }

    /// Handles a dropped or entered link.
    ///
    /// The first HTTP(S) address in the text is used. Resolution reports back through the
    /// handler so the calling dialog can close on success or display the failure reason.
    ///
    /// @param rawText the dropped or entered text, possibly `null`
    /// @param handler the handler accepting or rejecting the resolution
    private static void handle(@Nullable String rawText, FutureCallback.ResultHandler handler) {
        if (rawText == null) {
            handler.reject(i18n("download.link.invalid"));
            return;
        }

        Matcher matcher = URL_PATTERN.matcher(rawText);
        if (!matcher.find()) {
            handler.reject(i18n("download.link.invalid"));
            return;
        }

        String url = matcher.group();
        int end = url.length();
        while (end > 0 && ")\"'>,，。；;".indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        resolve(url.substring(0, end), handler);
    }

    /// Resolves a link and dispatches by host.
    ///
    /// @param rawUrl  the link to resolve
    /// @param handler the handler accepting or rejecting the resolution
    private static void resolve(String rawUrl, FutureCallback.ResultHandler handler) {
        @Nullable WebURL webUrl = WebURL.tryParse(NetworkUtils.addHttpsIfMissing(rawUrl));
        if (webUrl == null) {
            handler.reject(i18n("download.link.invalid"));
            return;
        }

        @Nullable String scheme = webUrl.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            handler.reject(i18n("download.link.invalid"));
            return;
        }

        @Nullable String host = webUrl.getHost();
        if (host == null) {
            handler.reject(i18n("download.link.invalid"));
            return;
        }

        String path = webUrl.getPath();
        switch (host.toLowerCase(Locale.ROOT)) {
            case "link.mcmod.cn" -> resolveMcmodRedirect(path, handler);
            case "mcmod.cn", "www.mcmod.cn" -> resolveMcmodProject(webUrl.toString(), path, handler);
            case "curseforge.com", "www.curseforge.com", "legacy.curseforge.com", "beta.curseforge.com" ->
                handler.reject(i18n("download.link.curseforge.unsupported"));
            case "modrinth.com", "www.modrinth.com" -> resolveModrinthProject(path, handler);
            default -> probeDownload(webUrl.toString(), handler);
        }
    }

    /// Decodes and follows a mcmod "related links" redirect.
    ///
    /// A payload pointing back to `link.mcmod.cn` is rejected instead of followed.
    ///
    /// @param path    the redirect path
    /// @param handler the handler accepting or rejecting the resolution
    private static void resolveMcmodRedirect(String path, FutureCallback.ResultHandler handler) {
        Matcher matcher = MCMOD_LINK_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            handler.reject(i18n("download.link.not_project"));
            return;
        }

        @Nullable String decoded = decodeMcmodTarget(matcher.group("target"));
        if (decoded == null || decoded.isBlank()) {
            handler.reject(i18n("download.link.invalid"));
            return;
        }

        @Nullable WebURL target = WebURL.tryParse(NetworkUtils.addHttpsIfMissing(decoded));
        if (target != null && "link.mcmod.cn".equalsIgnoreCase(target.getHost())) {
            handler.reject(i18n("download.link.invalid"));
            return;
        }

        resolve(decoded, handler);
    }

    /// Decodes the base64 payload of a mcmod "related links" redirect.
    ///
    /// Both the standard and URL-safe alphabets are accepted, with optional padding.
    ///
    /// @param target the encoded payload
    /// @return the decoded address, or `null` when the payload cannot be decoded
    private static @Nullable String decodeMcmodTarget(String target) {
        String value = NetworkUtils.decodeURL(target);
        int remainder = value.length() % 4;
        if (remainder == 2) {
            value += "==";
        } else if (remainder == 3) {
            value += "=";
        }

        for (Base64.Decoder decoder : BASE64_DECODERS) {
            try {
                return new String(decoder.decode(value), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // Try the next alphabet.
            }
        }
        return null;
    }

    /// Opens the remote page mapped to a mcmod project page.
    ///
    /// @param url     the mcmod project address, reported when no mapping is found
    /// @param path    the mcmod project path
    /// @param handler the handler accepting or rejecting the resolution
    private static void resolveMcmodProject(String url, String path, FutureCallback.ResultHandler handler) {
        Matcher matcher = MCMOD_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            handler.reject(i18n("download.link.not_project"));
            return;
        }

        boolean modpack = "modpack".equals(matcher.group("type"));
        int mcmodId;
        try {
            mcmodId = Integer.parseInt(matcher.group("id"));
        } catch (NumberFormatException e) {
            handler.reject(i18n("download.link.invalid"));
            return;
        }

        AddonProjectInfoRegistry<?> registry = modpack
                ? AddonProjectInfoRegistry.MODPACK
                : AddonProjectInfoRegistry.MOD;
        @Nullable AddonProjectInfo info = registry.getInfoByMcmodId(mcmodId);
        if (info == null) {
            handler.reject(i18n("download.link.resolve.failed", url));
            return;
        }

        openProjectInfo(info, handler);
    }

    /// Opens the remote page corresponding to a Modrinth project page.
    ///
    /// @param path    the Modrinth path
    /// @param handler the handler accepting or rejecting the resolution
    private static void resolveModrinthProject(String path, FutureCallback.ResultHandler handler) {
        Matcher matcher = MODRINTH_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            handler.reject(i18n("download.link.not_project"));
            return;
        }

        @Nullable RemoteAddon.Type type = switch (matcher.group("type")) {
            case "mod", "project" -> RemoteAddon.Type.MOD;
            case "modpack" -> RemoteAddon.Type.MODPACK;
            case "resourcepack" -> RemoteAddon.Type.RESOURCE_PACK;
            case "shader" -> RemoteAddon.Type.SHADER_PACK;
            default -> null;
        };
        if (type == null) {
            handler.reject(i18n("download.link.not_project"));
            return;
        }

        resolveModrinthAddon(type, matcher.group("slug"), handler);
    }

    /// Looks up a remote addon on Modrinth and opens its detail page.
    ///
    /// @param type    the addon type
    /// @param id      the Modrinth slug or project identifier
    /// @param handler the handler accepting or rejecting the resolution
    private static void resolveModrinthAddon(RemoteAddon.Type type, String id, FutureCallback.ResultHandler handler) {
        RemoteAddonRepository repository = ModrinthRemoteAddonRepository.getInstance();
        Task.supplyAsync(() -> repository.getAddonById(DownloadProviders.getDownloadProvider(), id))
                .whenComplete(Schedulers.javafx(), (@Nullable RemoteAddon addon, @Nullable Exception exception) -> {
                    if (addon != null) {
                        openAddonPage(repository, type, addon, handler);
                    } else {
                        LOG.warning("Failed to resolve Modrinth project: " + id, exception);
                        handler.reject(i18n("download.link.resolve.failed", id));
                    }
                }).start();
    }

    /// Opens the Modrinth page recorded for a matched local project.
    ///
    /// @param info    the matched local project
    /// @param handler the handler accepting or rejecting the resolution
    private static void openProjectInfo(AddonProjectInfo info, FutureCallback.ResultHandler handler) {
        @Nullable String modrinthUrl = info.modrinthUrl();
        @Nullable WebURL url = modrinthUrl != null ? WebURL.tryParse(modrinthUrl) : null;
        @Nullable String path = url != null ? url.getPath() : null;
        if (path == null) {
            handler.reject(i18n("download.link.resolve.failed", info.mcmodUrl()));
            return;
        }

        resolveModrinthProject(path, handler);
    }

    /// Opens the in-launcher detail page for a resolved addon.
    ///
    /// The link dialog is closed first so the detail page is shown on a clean dialog stack.
    /// For instance-scoped addons the currently selected instance is passed to the page so
    /// that versions recommended for that instance are listed. Modpacks are installed as new
    /// instances, so they are opened unbound and show the latest recommended version.
    ///
    /// @param repository the repository that resolved the addon
    /// @param type       the addon type
    /// @param addon      the resolved addon
    /// @param handler    the handler accepting the resolution
    private static void openAddonPage(RemoteAddonRepository repository, RemoteAddon.Type type, RemoteAddon addon,
                                      FutureCallback.ResultHandler handler) {
        handler.resolve();

        HMCLGameRepository gameRepository = GameDirectoryManager.getSelectedRepository();
        @Nullable HMCLGameInstance selectedInstance = gameRepository.getSelectedInstance();
        // Modpacks are installed as new instances, so they follow the same unbound path as
        // the regular download list, which shows the latest recommended version.
        HMCLGameInstance.Optional instanceReference = type != RemoteAddon.Type.MODPACK && selectedInstance != null
                ? HMCLGameInstance.Optional.of(selectedInstance)
                : HMCLGameInstance.Optional.empty(gameRepository);

        DownloadListPage page = new DownloadListPage(type, repository);
        Controllers.navigate(new org.jackhuang.hmcl.ui.instances.DownloadPage(
                page, addon, instanceReference, createCallback(type)));
    }

    /// Returns the download callback used by the detail page for the addon type.
    ///
    /// @param type the addon type
    /// @return the callback, or `null` to fall back to saving the file
    private static org.jackhuang.hmcl.ui.instances.DownloadPage.@Nullable DownloadCallback createCallback(
            RemoteAddon.Type type) {
        return switch (type) {
            case MOD -> DownloadPage.FOR_MOD;
            case RESOURCE_PACK -> DownloadPage.FOR_RESOURCE_PACK;
            case SHADER_PACK -> DownloadPage.FOR_SHADER;
            case MODPACK -> (downloadProvider, repository, instanceId, addon, file) ->
                    Instances.downloadModpackImpl(downloadProvider, repository, instanceId, addon, file);
            default -> null;
        };
    }

    /// Probes a link with HTTP and offers actions when it is a downloadable file.
    ///
    /// @param url     the link to probe
    /// @param handler the handler accepting or rejecting the resolution
    private static void probeDownload(String url, FutureCallback.ResultHandler handler) {
        Task.supplyAsync(() -> probe(url))
                .whenComplete(Schedulers.javafx(), (@Nullable ProbeResult result, @Nullable Exception exception) -> {
                    if (result == null) {
                        LOG.warning("Failed to probe url: " + url, exception);
                        handler.reject(i18n("download.link.resolve.failed", url));
                        return;
                    }
                    showProbeActions(result, handler);
                }).start();
    }

    /// Returns the file information for a probed link.
    ///
    /// @param url the link to probe
    /// @return the probed file information
    /// @throws IOException if the link cannot be inspected or does not point to a downloadable file
    private static ProbeResult probe(String url) throws IOException {
        @Nullable WebURL webUrl = WebURL.tryParse(url);
        if (webUrl == null)
            throw new IOException("Invalid link: " + url);

        @Nullable NetworkUtils.RemoteFile file = NetworkUtils.detectFile(webUrl);
        if (file == null)
            throw new IOException("Not a downloadable file: " + url);

        return createProbeResult(file.url(), file.fileName(), file.contentType());
    }

    /// Determines which installation actions apply to a probed file.
    ///
    /// @param url         the final download URL
    /// @param fileName    the detected filename
    /// @param contentType the response content type, possibly `null`
    /// @return the probe result
    private static ProbeResult createProbeResult(
            String url, String fileName, @Nullable String contentType) {
        String extension = StringUtils.substringAfterLast(fileName, '.').toLowerCase(Locale.ROOT);
        String mime = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";

        if ("mrpack".equals(extension))
            return new ProbeResult(url, fileName, false, true, false);

        boolean zip = "zip".equals(extension) || mime.contains("zip");
        boolean mod = zip || "jar".equals(extension) || "litemod".equals(extension) || mime.contains("java-archive");
        boolean json = "json".equals(extension) || mime.contains("json");

        return new ProbeResult(url, fileName, mod, zip, json);
    }

    /// Shows the available actions for a probed download.
    ///
    /// The link dialog is closed first, then the action dialog is shown.
    ///
    /// @param result  the probe result
    /// @param handler the handler accepting or rejecting the resolution
    private static void showProbeActions(ProbeResult result, FutureCallback.ResultHandler handler) {
        handler.resolve();

        MessageDialogPane.Builder builder = new MessageDialogPane.Builder(
                i18n("download.link.detected", result.fileName()),
                i18n("download.link.title"),
                MessageDialogPane.MessageType.QUESTION
        );

        @Nullable HMCLGameInstance instance =
                GameDirectoryManager.getSelectedRepository().getSelectedInstance();

        if (result.mod() && instance != null) {
            builder.addAction(i18n("download.link.install.mod"), () -> installMod(result, instance));
        }
        if (result.modpack()) {
            builder.addAction(i18n("download.link.install.modpack"), () -> installModpack(result));
        }
        if (result.json()) {
            builder.addAction(i18n("download.link.install.instance"), () -> installInstance(result));
        }
        builder.addAction(i18n("button.save_as"), () -> saveAs(result));
        builder.addCancel(null);
        Controllers.dialog(builder.build());
    }

    /// Saves a probed download through the save-file dialog.
    ///
    /// @param result the probe result
    private static void saveAs(ProbeResult result) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("button.save_as"));
        chooser.setInitialFileName(result.fileName());

        @Nullable Path destination = Controllers.showSaveDialog(chooser);
        if (destination == null)
            return;

        Controllers.taskDialog(
                new FileDownloadTask(result.url(), destination),
                i18n("message.downloading.file", result.fileName()),
                TaskCancellationAction.NORMAL
        );
    }

    /// Downloads a probed file and installs it into the given instance.
    ///
    /// @param result   the probe result
    /// @param instance the instance receiving the mod
    private static void installMod(ProbeResult result, HMCLGameInstance instance) {
        ModManager modManager = instance.getModManager();
        Path temporaryFile;
        try {
            temporaryFile = createTemporaryFile(result.fileName(), "jar");
        } catch (IOException e) {
            LOG.warning("Failed to create temporary file for " + result.url(), e);
            showError(I18n.localizeErrorMessage(e));
            return;
        }

        FileDownloadTask task = new FileDownloadTask(result.url(), temporaryFile);
        task.setName(result.fileName());
        Controllers.taskDialog(task.whenComplete(Schedulers.javafx(), (@Nullable Exception exception) -> {
            try {
                if (exception == null) {
                    modManager.addMod(renameToUnique(modManager.getDirectory(), temporaryFile));
                    Controllers.showToast(i18n("install.success"));
                } else if (!(exception instanceof CancellationException)) {
                    LOG.warning("Failed to download mod: " + result.url(), exception);
                    showError(I18n.localizeErrorMessage(exception));
                }
            } catch (IOException e) {
                LOG.warning("Failed to install mod: " + temporaryFile, e);
                showError(I18n.localizeErrorMessage(e));
            } finally {
                FileUtils.deleteDirectoryQuietly(temporaryFile.getParent());
            }
        }), i18n("mods.install"), TaskCancellationAction.NORMAL);
    }

    /// Downloads a probed file and starts the modpack installation wizard.
    ///
    /// @param result the probe result
    private static void installModpack(ProbeResult result) {
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        Path temporaryFile;
        try {
            temporaryFile = Files.createTempFile("hmcl-modpack-", "." + extensionOr(result.fileName(), "zip"));
        } catch (IOException e) {
            LOG.warning("Failed to create temporary file for " + result.url(), e);
            showError(I18n.localizeErrorMessage(e));
            return;
        }

        FileDownloadTask task = new FileDownloadTask(result.url(), temporaryFile);
        task.setName(result.fileName());
        Controllers.taskDialog(task.whenComplete(Schedulers.javafx(), (@Nullable Exception exception) -> {
            if (exception == null) {
                Controllers.getDecorator().startWizard(
                        new ModpackInstallWizardProvider(repository, temporaryFile),
                        i18n("install.modpack")
                );
            } else if (!(exception instanceof CancellationException)) {
                LOG.warning("Failed to download modpack: " + result.url(), exception);
                deleteQuietly(temporaryFile);
                showError(I18n.localizeErrorMessage(exception));
            }
        }), i18n("modpack.download.title", result.fileName()), TaskCancellationAction.NORMAL);
    }

    /// Downloads a probed game instance manifest and installs it through the JSON import flow.
    ///
    /// The manifest is saved as `instance.json` in a temporary directory.
    ///
    /// @param result the probe result
    private static void installInstance(ProbeResult result) {
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        Path temporaryDirectory;
        Path temporaryFile;
        try {
            temporaryDirectory = Files.createTempDirectory("hmcl-instance-");
            temporaryFile = temporaryDirectory.resolve("instance.json");
        } catch (IOException e) {
            LOG.warning("Failed to create temporary file for " + result.url(), e);
            showError(I18n.localizeErrorMessage(e));
            return;
        }

        FileDownloadTask task = new FileDownloadTask(result.url(), temporaryFile);
        task.setName(result.fileName());
        Controllers.taskDialog(task.whenComplete(Schedulers.javafx(), (@Nullable Exception exception) -> {
            try {
                if (exception == null) {
                    Instances.installFromJson(repository, temporaryFile);
                } else if (!(exception instanceof CancellationException)) {
                    LOG.warning("Failed to download instance manifest: " + result.url(), exception);
                    showError(I18n.localizeErrorMessage(exception));
                }
            } finally {
                deleteQuietly(temporaryFile);
                deleteQuietly(temporaryDirectory);
            }
        }), i18n("install.new_game"), TaskCancellationAction.NORMAL);
    }

    /// Creates a temporary file named after the probed download.
    ///
    /// The file is placed in a new temporary directory so that the probed name, which may come
    /// from `Content-Disposition` or the final URL, can be kept verbatim.
    ///
    /// @param fileName          the probed file name
    /// @param fallbackExtension the extension appended when the name has none, without a leading dot
    /// @return the created file; its parent directory should be deleted together with it
    /// @throws IOException if the temporary directory or file cannot be created
    private static Path createTemporaryFile(String fileName, String fallbackExtension) throws IOException {
        Path directory = Files.createTempDirectory("hmcl-link-");
        return Files.createFile(directory.resolve(sanitizeFileName(fileName, fallbackExtension)));
    }

    /// Returns a filename containing only portable characters.
    ///
    /// Characters outside `[A-Za-z0-9._+-]` are replaced with `_`. A base name without any letter
    /// or digit becomes `mod`. A missing extension falls back to the given one.
    ///
    /// @param fileName          the desired file name
    /// @param fallbackExtension the extension appended when the name has none, without a leading dot
    /// @return the sanitized file name
    private static String sanitizeFileName(String fileName, String fallbackExtension) {
        String extension = FileUtils.getExtension(fileName).replaceAll("[^A-Za-z0-9]", "");
        if (extension.isEmpty())
            extension = fallbackExtension;

        String baseName = FileUtils.getNameWithoutExtension(fileName).replaceAll("[^A-Za-z0-9._+-]", "");
        if (!baseName.matches(".*[A-Za-z0-9].*"))
            baseName = "mod";

        return baseName + "." + extension;
    }

    /// Returns the given file renamed so that it does not collide with a file in the directory.
    ///
    /// A numeric suffix is inserted before the extension until the name is unused.
    ///
    /// @param directory the directory receiving the file
    /// @param file      the file to rename
    /// @return the file itself when its name is unused, otherwise the renamed file
    /// @throws IOException if the file cannot be renamed
    private static Path renameToUnique(Path directory, Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String extension = FileUtils.getExtension(fileName);
        String suffix = extension.isEmpty() ? "" : "." + extension;
        String baseName = FileUtils.getNameWithoutExtension(fileName);

        String candidateName = fileName;
        for (int i = 1; Files.exists(directory.resolve(candidateName)); i++)
            candidateName = baseName + " (" + i + ")" + suffix;

        return candidateName.equals(fileName) ? file : Files.move(file, file.resolveSibling(candidateName));
    }

    /// Returns the extension of a filename, or the fallback when there is none.
    ///
    /// @param fileName the filename
    /// @param fallback the fallback extension without a leading dot
    /// @return the extension without a leading dot
    private static String extensionOr(String fileName, String fallback) {
        String extension = StringUtils.substringAfterLast(fileName, '.');
        return StringUtils.isBlank(extension) ? fallback : extension;
    }

    /// Deletes a temporary file or directory, logging but ignoring failures.
    ///
    /// @param path the path to delete
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warning("Failed to delete temporary file: " + path, e);
        }
    }

    /// Shows an error message for a failed link resolution.
    ///
    /// @param message the message to show
    private static void showError(String message) {
        Controllers.dialog(message, i18n("download.link.title"), MessageDialogPane.MessageType.ERROR);
    }

    /// Information about a probed download link.
    ///
    /// @param url      the final download URL
    /// @param fileName the detected filename
    /// @param mod      whether the file can be installed as a mod
    /// @param modpack  whether the file can be installed as a modpack
    /// @param json     whether the file is a game instance manifest
    private record ProbeResult(String url, String fileName, boolean mod, boolean modpack, boolean json) {
    }
}
