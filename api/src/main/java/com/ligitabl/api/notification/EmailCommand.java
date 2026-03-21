package com.ligitabl.api.notification;

public interface EmailCommand {
    enum EmailType {
        PASSWORD_RESET,
        PASSWORD_RESET_CONFIRMATION
    }

    enum Priority {
        NORMAL,
        HIGH
    }
}
