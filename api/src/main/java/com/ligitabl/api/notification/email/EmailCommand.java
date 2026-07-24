package com.ligitabl.api.notification.email;

public interface EmailCommand {
    enum EmailType {
        PASSWORD_RESET,
        PASSWORD_RESET_CONFIRMATION,
        EMAIL_VERIFICATION,
        ROUND_RESULTS
    }

    enum Priority {
        NORMAL,
        HIGH
    }
}
