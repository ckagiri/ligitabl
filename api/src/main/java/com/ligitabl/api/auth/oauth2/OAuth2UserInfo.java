package com.ligitabl.api.auth.oauth2;

import java.util.Map;

public record OAuth2UserInfo(String id, String email, String name) {

    public static OAuth2UserInfo fromGoogle(Map<String, Object> attributes) {
        String id = asString(attributes.get("sub"));
        String email = asString(attributes.get("email"));
        String name = asString(attributes.get("name"));

        return new OAuth2UserInfo(id, email, name);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
