package org.jackhuang.hmcl.setting;

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.util.ToStringBuilder;

import java.util.Objects;
import java.util.UUID;

public class AccountReference {
    private final String type;
    private final String username;
    private final String character;
    private final UUID uuid;

    public AccountReference(String type, String username, String character, UUID uuid) {
        this.type = type;
        this.username = username;
        this.character = character;
        this.uuid = uuid;
    }

    public static AccountReference ofAccount(Account account) {
        if (account == null) {
            return null;
        }
        String type = Accounts.getLoginType(Accounts.getAccountFactory(account));
        return new AccountReference(type, account.getUsername(), account.getCharacter(), account.getUUID());
    }

    public String getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public String getCharacter() {
        return character;
    }

    public UUID getUUID() {
        return uuid;
    }

    public boolean isReferenceTo(Account account) {
        return Objects.equals(type, Accounts.getLoginType(Accounts.getAccountFactory(account)))
                && Objects.equals(username, account.getUsername())
                && Objects.equals(character, account.getCharacter())
                && Objects.equals(uuid, account.getUUID());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountReference)) return false;
        AccountReference that = (AccountReference) o;
        return Objects.equals(type, that.type)
                && Objects.equals(username, that.username)
                && Objects.equals(character, that.character)
                && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, username, character, uuid);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("username", username)
                .append("character", character)
                .append("uuid", uuid)
                .toString();
    }
}
