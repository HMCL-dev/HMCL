package net.burningtnt.hmat.game;

import net.burningtnt.hmat.AnalyzeResult;
import net.burningtnt.hmat.Analyzer;
import net.burningtnt.hmat.LogAnalyzable;
import net.burningtnt.hmat.solver.Solver;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.logging.Logger;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

        for (int l = headI; l < length; l++) {
            String line = logs.get(l);

            int pl = line.indexOf(':');
            if (pl == -1 || line.charAt(pl + 1) != ' ' || line.indexOf(':', pl + 2) != -1 || checkInvalidCP(line, 0, pl)) {
                continue;
            }

            {
                String next = logs.get(l + 1);
                if (next.isEmpty() || next.charAt(0) != '\t') {
                    continue;
                }
            }

            if (checkCP(line, 0, pl, input, results)) {
                return ControlFlow.CONTINUE;
            }

            for (int i2 = l + 2; i2 < length; i2++) {
                String ls = logs.get(i2);
                if (!ls.startsWith(C_AT_STRING)) {
                    break;
                }

                int ce = ls.indexOf('(');
                if (ce == -1) {
                    ce = ls.length() - 1;
                }

                if (checkInvalidCP(ls, C_AT_LENGTH, ce)) {
                    break;
                }

                if (checkCP(ls, C_AT_LENGTH, ce, input, results)) {
                    return ControlFlow.CONTINUE;
                }
            }
        }

        out:
        for (int l = headI; l >= 0; l--) {
            if (logs.get(l).startsWith(C_AT_STRING)) {
                for (int l2 = l - 1; l2 >= 0; l2--) {
                    String line2 = logs.get(l2);

                    if (!line2.startsWith(C_AT_STRING)) {
                        int start2 = line2.startsWith(C_CB_STRING) ? C_CB_LENGTH : 0;

                        int pl = line2.indexOf(':', start2);
                        if (pl == -1 || line2.charAt(pl + 1) != ' ' || line2.indexOf(':', pl + 2) != -1 || checkInvalidCP(line2, 0, pl)) {
                            l = l2;
                            continue out;
                        }

                        if (checkCP(line2, start2, pl, input, results)) {
                            return ControlFlow.CONTINUE;
                        }

                        for (int l3 = l2 + 1; l3 < l; l3++) {
                            String line3 = logs.get(l3);
                            int ce = line3.indexOf('(', C_AT_LENGTH);
                            if (ce == -1) {
                                ce = line3.length() - 1;
                            }


                            if (checkCP(line3, C_AT_LENGTH, ce, input, results)) {
                                return ControlFlow.CONTINUE;
                            }
                        }
                    }
                }
            }
        }

        return ControlFlow.CONTINUE;
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
    private boolean checkCP(String value, int start, int end, LogAnalyzable input, List<AnalyzeResult<LogAnalyzable>> results) throws IOException {
        for (String tep : TRUSTED_ERROR_PREFIX) {
            if (start - end >= tep.length() && value.regionMatches(start, tep, 0, tep.length())) {
                return false;
            }
        }

        String path = '/' + value.substring(start, end).replace('.', '/');
        int ll = path.lastIndexOf('/');
        if (ll == -1) {
            return false;
        }
        path = path.substring(0, ll);

        ModManager mods = input.getRepository().getModManager(input.getVersion().getId());

        for (LocalModFile mod : mods.getMods()) {
            Path file = mod.getFile();
            if (!"jar".equals(FileUtils.getExtension(file))) {
                continue;
            }

            try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(file)) {
                Path clazz = fs.getPath(path);
                if (Files.exists(clazz)) {
                    results.add(new AnalyzeResult<>(this, AnalyzeResult.ResultID.BAD_MOD, Solver.ofTask(Task.runAsync(() -> {
                        mods.disableMod(file);
                    }))));
                    return true;
                }
            } catch (Throwable t) {
                Logger.LOG.warning("Cannot open " + file, t);
            }
        }

        return false;
    }
}
