package com.ligitabl.model.utils.db;

import org.jooq.Record;
import org.jooq.RecordMapper;

import com.ligitabl.model.domain.AbstractModel;

/**
 * Base type for mapping jOOQ Records to domain models. R is the jOOQ Record
 * type; T is a domain model extending AbstractModel with any ID type.
 */
public abstract class AbstractRecordMapper<R extends Record, T extends AbstractModel<?>>
    implements
        RecordMapper<R, T> {
}
