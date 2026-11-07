package dev.troll;

import java.util.Locale;

public enum Troll {
    JUMPSCARE("jumpscare"),
    DISCONNECT("disconnect"),
    RESOURCE("resource"),
    REACH("reach"),
    ASCEND("ascend");

    private final String id;

    Troll(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Troll byId(String id) {
        if (id == null) return null;
        String wanted = id.toLowerCase(Locale.ROOT);
        for (Troll t : values()) {
            if (t.id.equals(wanted)) return t;
        }
        return null;
    }
}
