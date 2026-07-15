package com.ligitabl.api.notification;

public interface EmailCommand {
    enum EmailType {
        PASSWORD_RESET,
        PASSWORD_RESET_CONFIRMATION,
        EMAIL_VERIFICATION
    }

    enum Priority {
        NORMAL,
        HIGH
    }
}
