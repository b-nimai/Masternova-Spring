package com.masternova.api.commerce.web.dto;

import com.masternova.api.commerce.application.WebhookService;

/** What one verified webhook delivery did; always with a 200 (API conventions §12). */
public record WebhookResponse(WebhookService.Result outcome) {}
