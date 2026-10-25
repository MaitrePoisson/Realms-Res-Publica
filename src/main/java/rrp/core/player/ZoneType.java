package rrp.core.player;

import java.util.Locale;

/** Kind of plot a ruler can draw over the claimed land. */
public enum ZoneType {
    RESIDENTIAL, FARM, MARKET, BARRACKS, FORBIDDEN;

    public static ZoneType parse(String s) {
        if (s == null) {
            return null;
        }
        for (ZoneType t : values()) {
            if (t.name().equals(s.toUpperCase(Locale.ROOT))) {
                return t;
            }
        }
        return null;
    }
}
