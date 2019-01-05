/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Immutable;

import java.util.*;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class CompatibilityRule {

    private final Action action;
    private final OSRestriction os;
    private final Map<String, Boolean> features;

    public CompatibilityRule() {
        this(Action.ALLOW, null);
    }

    public CompatibilityRule(Action action, OSRestriction os) {
        this(action, os, null);
    }

    public CompatibilityRule(Action action, OSRestriction os, Map<String, Boolean> features) {
        this.action = action;
        this.os = os;
        this.features = features;
    }

    public Optional<Action> getAppliedAction(Map<String, Boolean> supportedFeatures) {
        if (os != null && !os.allow())
            return Optional.empty();

        if (features != null)
            for (Map.Entry<String, Boolean> entry : features.entrySet())
                if (!Objects.equals(supportedFeatures.get(entry.getKey()), entry.getValue()))
                    return Optional.empty();

        return Optional.ofNullable(action);
    }

    public static boolean appliesToCurrentEnvironment(Collection<CompatibilityRule> rules) {
        return appliesToCurrentEnvironment(rules, Collections.emptyMap());
    }

    public static boolean appliesToCurrentEnvironment(Collection<CompatibilityRule> rules, Map<String, Boolean> features) {
        if (rules == null || rules.isEmpty())
            return true;

        Action action = Action.DISALLOW;
        for (CompatibilityRule rule : rules) {
            Optional<Action> thisAction = rule.getAppliedAction(features);
            if (thisAction.isPresent())
                action = thisAction.get();
        }

        return action == Action.ALLOW;
    }

    public static boolean equals(Collection<CompatibilityRule> rules1, Collection<CompatibilityRule> rules2) {
        return Objects.hashCode(rules1) == Objects.hashCode(rules2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompatibilityRule that = (CompatibilityRule) o;
        return action == that.action &&
                Objects.equals(os, that.os) &&
                Objects.equals(features, that.features);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, os, features);
    }

    public enum Action {
        ALLOW,
        DISALLOW
    }
}
