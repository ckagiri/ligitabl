package com.ligitabl.model.domain;

public sealed interface SlugError permits SlugError.Blank, SlugError.InvalidFormat {
    String message();

    record Blank() implements SlugError {
        @Override
        public String message() {
            return "Slug cannot be blank";
        }
    }

    record InvalidFormat(String message) implements SlugError {
        @Override
        public String message() {
            return message;
        }
    }
}
