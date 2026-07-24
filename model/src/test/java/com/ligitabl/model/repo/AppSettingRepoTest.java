package com.ligitabl.model.repo;

import static com.ligitabl.model.db.tables.TAppSetting.T_APP_SETTING;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.ligitabl.model.infra.AppSettingPersistenceAdapter;

@Tag("integration")
class AppSettingRepoTest {

    private static Connection jdbc;
    private static DSLContext dsl;
    private static AppSettingRepo repo;

    @BeforeAll
    static void setup() throws Exception {
        jdbc = TestDbConnections.open();
        dsl = DSL.using(jdbc, SQLDialect.POSTGRES);
        repo = new AppSettingPersistenceAdapter(dsl);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jdbc != null) {
            jdbc.close();
        }
    }

    @BeforeEach
    void cleanTable() {
        dsl.deleteFrom(T_APP_SETTING).execute();
    }

    @Test
    void findValueReturnsStoredValue() {
        dsl.insertInto(T_APP_SETTING)
                .set(T_APP_SETTING.PK_KEY, "round_results_email_ignore_list")
                .set(T_APP_SETTING.C_VALUE, "a@test.com, b@test.com")
                .execute();

        assertThat(repo.findValue("round_results_email_ignore_list")).contains("a@test.com, b@test.com");
    }

    @Test
    void findValueDistinguishesBlankFromAbsent() {
        dsl.insertInto(T_APP_SETTING)
                .set(T_APP_SETTING.PK_KEY, "blank_setting")
                .set(T_APP_SETTING.C_VALUE, "")
                .execute();

        assertThat(repo.findValue("blank_setting")).contains("");
        assertThat(repo.findValue("no_such_key")).isEmpty();
    }
}
