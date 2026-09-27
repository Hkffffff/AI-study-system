package com.aistudy.common.error;

/** One entry of the {@code errors} property of a validation problem detail. */
public record FieldErrorItem(String field, String message) {
}
