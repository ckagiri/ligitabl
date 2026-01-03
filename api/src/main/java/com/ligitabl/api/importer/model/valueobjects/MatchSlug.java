package com.ligitabl.api.importer.model.valueobjects;

import lombok.Value;

@Value
public class MatchSlug {
    String value;

    public static MatchSlug of(String homeTla, String awayTla) {
        var slug = String.format("%s-%s", homeTla.toLowerCase(), awayTla.toLowerCase());
        return new MatchSlug(slug);
    }
}
