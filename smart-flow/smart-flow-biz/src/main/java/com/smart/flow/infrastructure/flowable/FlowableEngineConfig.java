package com.smart.flow.infrastructure.flowable;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.stereotype.Component;

/**
 * Tweaks the embedded Flowable process engine to fit the Smart platform.
 *
 * <p>The customisation is intentionally narrow:
 * <ul>
 *   <li>Use the {@code FULL} history level so that the task center can replay every approval
 *       step without a separate audit table;</li>
 *   <li>Disable the async executor on boot so dev runs are silent; production deployments
 *       enable it explicitly via configuration when needed;</li>
 *   <li>Force the schema to be created/updated automatically so the {@code act_*} tables stay
 *       in sync with the engine version on every startup.</li>
 * </ul>
 *
 * <p>Note: we do not override the data source - Flowable picks up the same one configured by
 * {@code smart-common-data}, so it shares transactions with the Smart business tables.
 *
 * <p>Implemented as a plain {@code @Component} (not {@code @Configuration}) because this class
 * holds no {@code @Bean} methods; using {@code @Configuration} would force CGLib proxying for no
 * benefit and could obscure the generic type that the Flowable starter introspects.
 */
@Component
public class FlowableEngineConfig implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {

    @Override
    public void configure(SpringProcessEngineConfiguration engineConfig) {
        engineConfig.setDatabaseSchemaUpdate(SpringProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        engineConfig.setHistoryLevel(HistoryLevel.FULL);
        engineConfig.setAsyncExecutorActivate(false);
    }
}
