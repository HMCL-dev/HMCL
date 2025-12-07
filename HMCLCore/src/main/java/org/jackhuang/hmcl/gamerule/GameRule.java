package org.jackhuang.hmcl.gamerule;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.ArrayList;

public sealed class GameRule permits GameRule.BooleanGameRule, GameRule.IntGameRule {
    ArrayList<String> ruleKey;
    String displayName;

    public static GameRule createGameRule(ArrayList<String> ruleKey, String displayName, boolean defaultValue) {
        return new BooleanGameRule(ruleKey, displayName, defaultValue);
    }

    public static GameRule createGameRule(ArrayList<String> ruleKey, String displayName, int defaultValue) {
        return new IntGameRule(ruleKey, displayName, defaultValue);
    }

    static final class BooleanGameRule extends GameRule {
        BooleanProperty value;
        BooleanProperty defaultValue;

        private BooleanGameRule(ArrayList<String> ruleKey, String displayName, boolean defaultValue) {
            this.ruleKey = ruleKey;
            this.displayName = displayName;
            this.defaultValue = new SimpleBooleanProperty(defaultValue);
        }
    }

    static final class IntGameRule extends GameRule {
        IntegerProperty value;
        IntegerProperty defaultValue;

        IntGameRule(ArrayList<String> ruleKey, String displayName, int defaultValue) {
            this.ruleKey = ruleKey;
            this.displayName = displayName;
            this.defaultValue = new SimpleIntegerProperty(defaultValue);
        }
    }
}
