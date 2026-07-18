package br.com.ricarte.assinaflow.notification;

import java.util.Map;
import java.util.UUID;

public record NotificationMessage(
        String eventType,
        UUID userId,
        String email,
        UUID subscriptionId,
        String subject,
        String body,
        Map<String, String> attributes
) {
}
