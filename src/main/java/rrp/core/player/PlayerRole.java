package rrp.core.player;

import java.util.Locale;

/** Role chosen freely by a player; OBSERVER (default) means the mod never targets them. */
public enum PlayerRole {
    OBSERVER, SOVEREIGN, MERCENARY, MERCHANT;

    public static PlayerRole parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
