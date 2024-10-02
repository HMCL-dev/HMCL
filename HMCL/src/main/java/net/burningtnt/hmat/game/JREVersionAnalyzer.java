package net.burningtnt.hmat.game;

import net.burningtnt.hmat.AnalyzeResult;
import net.burningtnt.hmat.Analyzer;
import net.burningtnt.hmat.LogAnalyzable;
import net.burningtnt.hmat.solver.Solver;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Pair.pair;

public class JREVersionAnalyzer implements Analyzer<LogAnalyzable> {
    private static final Map<Pattern, BiFunction<LogAnalyzable, Matcher, GameJavaVersion>> KEYS = Lang.mapOf(
            pair(
                    // Mixin requires a higher class file version. Upgrade Java.
                    Pattern.compile("java.lang.IllegalArgumentException: The requested compatibility level JAVA_(?<version>[0-9]*) could not be set. Level is not supported by the active JRE or ASM version."),
                    (input, matcher) -> GameJavaVersion.normalize(Integer.parseInt(matcher.group("version")))
            ), pair(
                    // Only Java 11 provides this internal function. Set to Java 11.
                    Pattern.compile("Caused by: java.lang.NoSuchMethodError: 'java.lang.Class sun.misc.Unsafe.defineAnonymousClass\\(java.lang.Class, byte\\[], java\\.lang\\.Object\\[]\\)'"),
                    (input, matcher) -> GameJavaVersion.JAVA_8 // TODO: Enable GameJavaVersion to support downloading JAVA_8_312, JAVA_11
            ), pair(
                    // JVM cannot read the class files. Upgrade Java.
                    Pattern.compile("java.lang.UnsupportedClassVersionError: [a-zA-Z0-9/]* has been compiled by a more recent version of the Java Runtime \\(class file version (?<target>[0-9]*)(\\.[0-9]*)?\\), this version of the Java Runtime only recognizes class file versions up to (?<current>[0-9]*)(\\.[0-9]*)?"),
                    (input, matcher) -> {
                        int classVersionMagic = Integer.parseInt(matcher.group("target"));
                        if (classVersionMagic < 52) {
                            throw new IllegalArgumentException("Illegal class version magic number: " + classVersionMagic);
                        }
                        return GameJavaVersion.normalize(classVersionMagic - 44);
                    }
            ), pair(
                    // JVM cannot read the class files. Upgrade Java.
                    Pattern.compile("java.lang.IllegalArgumentException: Unsupported class file major version (?<target>[0-9]*)(\\.[0-9]*)?"),
                    (input, matcher) -> {
                        int classVersionMagic = Integer.parseInt(matcher.group("target"));
                        if (classVersionMagic < 52) {
                            throw new IllegalArgumentException("Illegal class version magic number: " + classVersionMagic);
                        }
                        return GameJavaVersion.normalize(classVersionMagic - 44);
                    }
            ), pair(
                    // ASM cannot read the class files. Downgrade Java to which suits the game.
                    Pattern.compile("Error loading class: (java|jdk)/[a-zA-Z0-9/]* \\(java.lang.IllegalArgumentException: Class file major version [0-9]* is not supported by active ASM \\(version [0-9]*(\\.[0-9]*)? supports class version [0-9]*\\), reading (java|jdk)/[a-zA-Z0-9/]*\\)"),
                    (input, matcher) -> GameJavaVersion.getMinimumJavaVersion(GameVersionNumber.asGameVersion(input.getRepository().getGameVersion(input.getVersion())))
            ), pair(
                    // Forge cannot hack system class loader on Java 11+. Downgrade Java to 8.
                    Pattern.compile("Exception in thread \"main\" java.lang.ClassCastException: class jdk.internal.loader.ClassLoaders\\$AppClassLoader cannot be cast to class java.net.URLClassLoader (jdk.internal.loader.ClassLoaders\\$AppClassLoader and java.net.URLClassLoader are in module java.base of loader 'bootstrap')"),
                    (input, matcher) -> GameJavaVersion.JAVA_8
            )
    );

    @Override
    public ControlFlow analyze(LogAnalyzable input, List<AnalyzeResult<LogAnalyzable>> results) {
        for (String line : input.getLogs()) {
            for (Pattern pattern : KEYS.keySet()) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    GameJavaVersion javaVersion;
                    try {
                        javaVersion = KEYS.get(pattern).apply(input, matcher);
                    } catch (RuntimeException ignored) {
                        continue;
                    }

                    results.add(new AnalyzeResult<>(this, AnalyzeResult.ResultID.LOG_GAME_JRE_VERSION, Solver.ofTask(Task.composeAsync(() -> {
                        int majorVersion = javaVersion.getMajorVersion();
                        for (JavaRuntime jre : JavaManager.getAllJava()) {
                            if (jre.getParsedVersion() == majorVersion) {
                                return Task.supplyAsync(() -> jre);
                            }
                        }
                        return JavaManager.getDownloadJavaTask(DownloadProviders.getDownloadProvider(), Platform.CURRENT_PLATFORM, javaVersion);
                    }).thenAcceptAsync(Schedulers.javafx(), SolverCollection.ofModifyJRE(input)))));

                    return ControlFlow.BREAK_OTHER;
                }
            }
        }

        return ControlFlow.CONTINUE;
    }
}
