package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TAppSetting.T_APP_SETTING;

import java.util.Optional;

import org.jooq.DSLContext;

import com.ligitabl.model.repo.AppSettingRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AppSettingPersistenceAdapter implements AppSettingRepo {
    private final DSLContext dsl;

    @Override
    public Optional<String> findValue(String key) {
        return dsl.select(T_APP_SETTING.C_VALUE)
                .from(T_APP_SETTING)
                .where(T_APP_SETTING.PK_KEY.eq(key))
                .fetchOptional(T_APP_SETTING.C_VALUE);
    }
}
