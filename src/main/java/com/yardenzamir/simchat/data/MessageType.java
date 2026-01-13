package com.yardenzamir.simchat.data;

/**
 * Type of chat message.
 */
public enum MessageType {
    /** Message from an NPC/entity */
    ENTITY,
    /** Message from a player */
    PLAYER,
    /** System message (transactions, events, etc.) */
    SYSTEM;

    public static MessageType fromOrdinal(int ordinal) {
        MessageType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ENTITY;
    }
}
