package com.coderockr.investments.adapter.in.rest;

final class ApiSchemaPatterns {
    static final String NON_BLANK_WITHOUT_NUL = "^(?=.*[^\\u0009-\\u000D\\u001C-\\u0020\\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029\\u205F\\u3000])[^\\x00]+$";

    private ApiSchemaPatterns() {}
}
