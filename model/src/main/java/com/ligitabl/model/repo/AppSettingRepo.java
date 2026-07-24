package com.ligitabl.model.repo;

import java.util.Optional;

/**
 * Key-value application settings (t_app_setting), edited via SQL for now.
 */
public interface AppSettingRepo {

    /** Empty when the key is absent; a present-but-blank value is returned as-is. */
    Optional<String> findValue(String key);
}
