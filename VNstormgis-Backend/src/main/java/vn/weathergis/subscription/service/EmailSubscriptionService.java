package vn.weathergis.subscription.service;

import java.util.UUID;
import vn.weathergis.alert.event.AlertPublishedEvent;
import vn.weathergis.subscription.dto.CreateSubscriptionRequest;
import vn.weathergis.subscription.dto.SubscriptionResponse;

public interface EmailSubscriptionService {

    SubscriptionResponse createSubscription(CreateSubscriptionRequest request);

    SubscriptionResponse verifyByOtp(UUID subscriptionId, String otp);

    SubscriptionResponse verifyByToken(String rawToken);

    SubscriptionResponse unsubscribeByToken(String rawToken);

    SubscriptionResponse sendTestNotification(UUID subscriptionId, String unsubscribeToken);

    int dispatchForPublishedAlert(AlertPublishedEvent event);
}
