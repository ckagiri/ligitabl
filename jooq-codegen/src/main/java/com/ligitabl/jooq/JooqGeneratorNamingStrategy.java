package com.ligitabl.jooq;

import org.jooq.codegen.DefaultGeneratorStrategy;
import org.jooq.meta.Definition;
import org.jooq.meta.TableDefinition;
import org.jooq.tools.StringUtils;

public class JooqGeneratorNamingStrategy extends DefaultGeneratorStrategy {

    private static final String FIELD_REPLACE_REGEX = "^(C_|FK_|PK_|c_|fk_|pk_)";
    // Strip a leading 'T' or 'J' from type names (e.g., t_team -> Team, j_job ->
    // Job)
    private static final String OBJECT_REPLACE_REGEX = "^[TJ]";

    @Override
    public String getJavaSetterName(Definition definition, Mode mode) {
        return "set" + formatColumnName(definition);
    }

    @Override
    public String getJavaGetterName(Definition definition, Mode mode) {
        return "get" + formatColumnName(definition);
    }

    @Override
    public String getJavaClassName(Definition definition, Mode mode) {
        String name = super.getJavaClassName(definition, mode);
        // Keep leading 'T' or 'J' ONLY for table classes in DEFAULT mode (i.e., the
        // table type itself).
        // For other modes (RECORD/POJO/etc.), even when definition is a
        // TableDefinition, strip the prefix.
        if (definition instanceof TableDefinition && mode == Mode.DEFAULT) {
            return name; // e.g., t_team -> TTeam (keeps the prefix for table class)
        }
        return name.replaceFirst(OBJECT_REPLACE_REGEX, ""); // e.g., TTeamRecord -> TeamRecord
    }

    private String formatColumnName(Definition definition) {
        String s = definition.getOutputName().replaceFirst(FIELD_REPLACE_REGEX, "");
        return StringUtils.toCamelCase(s.replace(' ', '_').replace('-', '_').replace('.', '_'));
    }
}
