package net.burningtnt.hmat.game;

import net.burningtnt.hmat.AnalyzeResult;
import net.burningtnt.hmat.Analyzer;
import net.burningtnt.hmat.LogAnalyzable;
import net.burningtnt.hmat.solver.Solver;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionRange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ForgeModsRequirementAnalyzer implements Analyzer<LogAnalyzable> {
    private static final String HEAD = "Missing or unsupported mandatory dependencies:";
    private static final Pattern LINE = Pattern.compile(
            "\tMod ID: '(?<target>\\w+)', Requested by: '\\w+', Expected range: '[\\[(](?<rangeL>[\\w.-]*),(?<rangeR>[\\w.-]*)[])]', Actual version: '(?<version>[\\w.-]*)'");

    @Override
    public ControlFlow analyze(LogAnalyzable input, List<AnalyzeResult<LogAnalyzable>> results) throws Exception {
        if (!input.getAnalyzer().getModLoaders().contains(ModLoaderType.FORGE)) {
            return ControlFlow.CONTINUE;
        }

        List<String> logs = input.getLogs();
        for (int l = logs.size(), i = 0; i < l; i++) {
            String current = logs.get(i);
            if (!current.contains(HEAD)) {
                continue;
            }

            List<ExceptionalRunnable<IOException>> tasks = new ArrayList<>();
            for (i++; i < l; i++) {
                Matcher matcher = LINE.matcher(logs.get(i));
                if (!matcher.matches()) {
                    results.add(new AnalyzeResult<>(this, AnalyzeResult.ResultID.LOG_GAME_MOD_REQUIREMENT, Solver.ofTask(Task.runAsync(() -> {
                        for (ExceptionalRunnable<IOException> task : tasks) {
                            task.run();
                        }
                    }))));

                    return ControlFlow.CONTINUE;
                }

                String targetMod = matcher.group("target");
                String rangeL = matcher.group("rangeL"), rangeR = matcher.group("rangeL");

                // TODO: infinity?
                VersionRange<VersionNumber> versionRange = VersionNumber.between(rangeL, rangeR);

                tasks.add(() -> {

                });
            }
        }
        return ControlFlow.CONTINUE;
    }
}
