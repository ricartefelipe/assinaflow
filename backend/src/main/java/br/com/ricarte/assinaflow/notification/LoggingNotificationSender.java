package br.com.ricarte.assinaflow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notifications.sender", havingValue = "logging", matchIfMissing = true)
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(NotificationMessage message) {
        log.info(
                "notification eventType={} to={} subject=\"{}\" body=\"{}\" subscriptionId={} attrs={}",
                message.eventType(),
                message.email(),
                message.subject(),
                message.body(),
                message.subscriptionId(),
                message.attributes()
        );
    }
}
