package com.yardenzamir.simchat.client;

import net.minecraft.network.chat.Component;

public enum SortMode {
    RECENT(0, "simchat.sort.recent"),
    ALPHABETICAL(1, "simchat.sort.alphabetical");

    private final int id;
    private final String translationKey;

    SortMode(int id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public int getId() {
        return id;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public SortMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static SortMode fromId(int id) {
        for (SortMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return RECENT;
    }
}
