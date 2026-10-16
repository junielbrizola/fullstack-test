package com.coderockr.investments.adapter.in.rest;

import java.util.List;

public record Problem(String type, String title, int status, String detail, String instance, List<FieldError> errors) {
    public record FieldError(String field, String message) {
    }
}
