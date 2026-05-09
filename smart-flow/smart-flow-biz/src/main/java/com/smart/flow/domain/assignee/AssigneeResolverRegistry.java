package com.smart.flow.domain.assignee;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central catalogue of all {@link AssigneeResolver} beans wired into the application context.
 *
 * <p>Spring injects every resolver bean through the constructor, after which the registry
 * indexes them by their {@link AssigneeResolver#strategyKey()}. Lookup is O(1), and any
 * duplicate-key collision is detected eagerly at startup so the deployment fails loudly
 * instead of silently dropping one of two competing strategies.
 *
 * <p>Why a dedicated registry rather than just injecting {@code List<AssigneeResolver>}
 * everywhere?
 * <ul>
 *   <li>Centralised duplicate-detection;</li>
 *   <li>Clearer error message - "no resolver for strategy 'foo'" vs. a generic
 *       {@link java.util.NoSuchElementException};</li>
 *   <li>Easier to add cross-cutting behaviour later (timing, audit, fallback chains).</li>
 * </ul>
 */
@Slf4j
@Component
public class AssigneeResolverRegistry {

    private final Map<String, AssigneeResolver> resolversByKey;

    public AssigneeResolverRegistry(List<AssigneeResolver> resolvers) {
        Map<String, AssigneeResolver> index = new HashMap<>(resolvers.size() * 2);
        for (AssigneeResolver resolver : resolvers) {
            String key = resolver.strategyKey();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException(
                        "AssigneeResolver " + resolver.getClass().getName() + " returned a blank strategyKey");
            }
            AssigneeResolver previous = index.put(key, resolver);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate AssigneeResolver strategyKey '" + key + "': "
                                + previous.getClass().getName() + " vs " + resolver.getClass().getName());
            }
        }
        this.resolversByKey = Map.copyOf(index);
        log.info("AssigneeResolverRegistry initialised with {} strategies: {}",
                resolversByKey.size(), resolversByKey.keySet());
    }

    /**
     * Looks up the resolver registered under {@code strategyKey}.
     *
     * @throws IllegalArgumentException if no resolver is registered under that key.
     */
    public AssigneeResolver require(String strategyKey) {
        AssigneeResolver resolver = resolversByKey.get(strategyKey);
        if (resolver == null) {
            throw new IllegalArgumentException(
                    "No AssigneeResolver registered for strategy '" + strategyKey
                            + "'. Available: " + resolversByKey.keySet());
        }
        return resolver;
    }

    /** Read-only view of every strategy key currently wired. Useful for admin endpoints. */
    public Set<String> registeredKeys() {
        return resolversByKey.keySet();
    }
}
