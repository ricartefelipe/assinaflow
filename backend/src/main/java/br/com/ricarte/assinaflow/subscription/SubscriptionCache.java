package br.com.ricarte.assinaflow.subscription;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SubscriptionCache {

    public static final String ACTIVE_CACHE = "activeSubscription";

    private final CacheManager cacheManager;

    public SubscriptionCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictActive(UUID userId) {
        Cache cache = cacheManager.getCache(ACTIVE_CACHE);
        if (cache != null) {
            cache.evict(userId);
        }
    }
}
