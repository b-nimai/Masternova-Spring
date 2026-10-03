package com.masternova.api.catalog.domain;

/** One requirement evaluated against one course: what the wizard's checklist shows per row. */
public record PublishCheck(String code, String message, boolean satisfied) {}
