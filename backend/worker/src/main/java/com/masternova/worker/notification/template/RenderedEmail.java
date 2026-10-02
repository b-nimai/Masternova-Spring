package com.masternova.worker.notification.template;

/** What a template produces: everything a mail provider needs except the addresses. */
public record RenderedEmail(String subject, String html, String text) {}
