package com.masternova.api.notification.web.dto;

import com.masternova.kernel.notification.NotificationCategory;

public record UnsubscribeResponse(NotificationCategory category) {}
