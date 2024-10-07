package net.burningtnt.hmat.game;

import net.burningtnt.hmat.AnalyzeResult;
import net.burningtnt.hmat.Analyzer;
import net.burningtnt.hmat.LogAnalyzable;
import net.burningtnt.hmat.solver.Solver;
import net.burningtnt.hmat.solver.SolverConfigurator;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.logging.Logger;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class BadModAnalyzer implements Analyzer<LogAnalyzable> {
    private static final String HEAD = "---- Minecraft Crash Report ----";

    /**
     * These packages are trusted by BadModAnalyzer.
     * If they exist in error cp or stack cp, they will be ignored.
     */
    private static final String[] TRUSTED_ERROR_PREFIX = {
            "java.", "jdk.", "sun.", "javax.", "com.sun.", // Java
            "net.minecraft.", "cpw.", "net.neoforged", "net.fabricmc.", // Minecraft, and Mod Loaders
            "it.unimi.dsi.fastutil." // Trusted Game Libraries
    };

    private static final String C_AT_STRING = "\tat ";
    private static final int C_AT_LENGTH = C_AT_STRING.length();
    private static final String C_CB_STRING = "Caused by: ";
    private static final int C_CB_LENGTH = C_CB_STRING.length();

    @Override
    public ControlFlow analyze(LogAnalyzable input, List<AnalyzeResult<LogAnalyzable>> results) throws Exception {
        Set<LocalModFile> mods = new HashSet<>();
        analyze0(input, mods);

        if (!mods.isEmpty()) {
            results.add(new AnalyzeResult<>(this, AnalyzeResult.ResultID.LOG_GAME_BAD_MOD, new Solver() {
                @Override
                public void configure(SolverConfigurator configurator) {
                    configurator.setDescription(i18n("analyzer.result.log_game_bad_mod.disabling", mods.stream().map(LocalModFile::getName).collect(Collectors.joining(", ", "[", "]"))));
                }

                @Override
                public void callbackSelection(SolverConfigurator configurator, int selectionID) {
                    if (selectionID == BTN_NEXT) {
                        for (LocalModFile mod : mods) {
                            try {
                                input.getRepository().getModManager(input.getVersion().getId()).disableMod(mod.getFile());
                            } catch (IOException e) {
                                Logger.LOG.warning("Cannot disable local mod: " + mod, e);
                            }
                        }

                        configurator.transferTo(null);
                    }
                }
            }));
        }

        return ControlFlow.CONTINUE;
    }

    /**
     * It will be impossible to read these codes.
     */
    private void analyze0(LogAnalyzable input, Set<LocalModFile> results) throws Exception {
        List<String> logs = input.getLogs();
        int length = logs.size();

        /* The log may contain "---- Minecraft Crash Report ----", like this:

           XXX
           XXX
           ---- Minecraft Crash Report ----
           XXX
           XXX

           If this line exists, we check the first errors after it. Next, we check every error before it.
           If we can conclude anything from any error, checking will be stopped.

           If this line doesn't exist, we check the last error first.

           Therefore, headI will point to this line, or the end of the document.
         */

        int headI = length - 1;
        for (int i = 0; i < length; i++) {
            if (HEAD.equals(logs.get(i))) {
                headI = i;
                break;
            }
        }

        // If this line exists, we check the first errors after it.
        for (int l = headI + 1; l < length; l++) {
            String line = logs.get(l);

            if (!line.startsWith(C_AT_STRING)) {
                continue;
            }

            // Move l to previous line.
            line = logs.get(--l);

            // Must be something like: [Caused by: ] xxx: xxx
            int start = findErrorStart(line);
            int pl = calcPL(line, start);
            if (checkPL(line, start, pl)) {
                l++; // Avoid scanning the same line.
                continue;
            }

            int cr = checkERROR(logs, l, start, pl, input, results);
            if (cr < 0) {
                return;
            }
            l = cr; // cr points to a non at-string line. However, the previous line is always a at-string line.
        }

        for (int l = headI; l >= 0; l--) {
            if (!logs.get(l).startsWith(C_AT_STRING)) {
                continue; // find an 'at ...'.
            }

            for (l--; l >= 0; l--) {
                String line = logs.get(l);

                if (line.startsWith(C_AT_STRING)) {
                    continue; // find the error line
                }

                int start = findErrorStart(line);
                int pl = calcPL(line, start);
                if (checkPL(line, start, pl)) {
                    break;
                }

                if (checkERROR(logs, l, start, pl, input, results) < 0) {
                    return;
                }
            }
        }

        return;
    }

    private int findErrorStart(String value) {
        return value.startsWith(C_CB_STRING) ? C_CB_LENGTH : 0;
    }

    private int calcPL(String line, int start) {
        int i = line.indexOf(':', start);
        if (i == -1) {
            return line.length();
        }
        return i;
    }

    private boolean checkPL(String line, int start, int pl) {
        int l = line.length();
        if (pl == l) {
            return checkInvalidCP(line, start, pl);
        }

        int n = pl + 1;
        return l <= n || line.charAt(n) != ' ' || checkInvalidCP(line, start, pl);
    }

    /**
     * @return >= 0 indicates further scanning should start from this index. -1 if all logs have been consumed. -2 if a potential bad mod has been settled.
     */
    private int checkERROR(List<String> logs, int errIndex, int errStart, int errPL, LogAnalyzable input, Set<LocalModFile> results) throws IOException {
        String errLine = logs.get(errIndex);
        if (checkCP(errLine, errStart, errPL, input, results)) {
            return -2;
        }

        for (int l = logs.size(), i = errIndex + 1; i < l; i++) {
            String ls = logs.get(i);
            if (!ls.startsWith(C_AT_STRING)) {
                return i;
            }

            int ce = ls.indexOf('(');
            if (ce == -1) {
                ce = ls.length() - 1;
            }

            if (checkInvalidCP(ls, C_AT_LENGTH, ce)) {
                continue;
            }

            if (checkCP(ls, C_AT_LENGTH, ce, input, results)) {
                return -2;
            }
        }

        return -1;
    }

    /**
     * @return True if the classpath is invalid
     */
    private boolean checkInvalidCP(String value, int start, int end) {
        int is = value.indexOf(' ', start);
        return is != -1 && is <= end;
    }

    /**
     * @return True if any information has been concluded.
     */
    private boolean checkCP(String value, int start, int end, LogAnalyzable input, Set<LocalModFile> results) throws IOException {
        int valueL = end - start;
        for (String tep : TRUSTED_ERROR_PREFIX) {
            if (valueL >= tep.length() && value.regionMatches(start, tep, 0, tep.length())) {
                return false;
            }
        }

        String path = computePath(value, start, end);
        if (path == null) {
            return false;
        }

        ModManager mods = input.getRepository().getModManager(input.getVersion().getId());
        for (LocalModFile mod : mods.getMods()) {
            Path file = mod.getFile();
            if (!"jar".equals(FileUtils.getExtension(file))) {
                continue;
            }

            try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(file)) {
                Path clazz = fs.getPath(path);
                if (Files.exists(clazz)) {
                    results.add(mod);
                    return true;
                }
            } catch (Throwable t) {
                Logger.LOG.warning("Cannot open " + file, t);
            }
        }

        return false;
    }

    private String computePath(String value, int start, int end) {
        int length = end - start;
        StringBuilder sb = new StringBuilder(length + 7).append('/').append(value, start, end);

        int ll = -1;
        for (int i = 1; i <= length; i++) {
            if (sb.charAt(i) == '.') {
                sb.setCharAt(i, '/');
                ll = i;
            }
        }
        if (ll == -1) {
            return null;
        }
        sb.setLength(ll);

        sb.append(".class");

        return sb.toString();
    }
}
