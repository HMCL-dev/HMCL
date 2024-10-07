package net.burningtnt.hmat;

import net.burningtnt.hmat.game.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface AnalyzableType<T> {
    List<Analyzer<T>> getAnalyzers();

    enum GameInstaller implements AnalyzableType<List<String>> {
        FORGE_INSTALLER, OPTIFINE_INSTALLER;

        @Override
        public List<Analyzer<List<String>>> getAnalyzers() {
            return Collections.emptyList();
        }
    }

    enum Log implements AnalyzableType<LogAnalyzable> {
        GAME;

        private static final List<Analyzer<LogAnalyzable>> ANALYZERS = Arrays.asList(
                new CodePageAnalyzer(),
                new JRE32BitAnalyzer(),
                new JREVersionAnalyzer(),
                new VirtualMemoryAnalyzer(),
                new BadModAnalyzer()
        );

        public List<Analyzer<LogAnalyzable>> getAnalyzers() {
            return ANALYZERS;
        }
    }

    enum JavaException implements AnalyzableType<Throwable> {
        JAVA_EXCEPTION;

        @Override
        public List<Analyzer<Throwable>> getAnalyzers() {
            return Collections.emptyList();
        }
    }
}
