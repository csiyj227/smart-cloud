package com.smart.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Jackson configuration for auth module.
 *
 * <h3>历史踩坑记录（务必保留，避免后人重蹈覆辙）</h3>
 *
 * <p>本类原本通过 {@code @ConditionalOnMissingBean(name = "objectMapper")} 抢注了一个
 * 名为 {@code objectMapper} 的全局 Bean。问题是 Spring Boot 默认的 ObjectMapper Bean 名是
 * {@code jacksonObjectMapper}（不是 {@code objectMapper}），condition 永远满足，结果会盖掉
 * Spring Boot 通过 {@code spring.jackson.*} 自动配置的那个 Mapper，导致 {@code application.yml}
 * 里配的 {@code date-format} / {@code time-zone} 全部失效——前端拿到的 LocalDateTime 直接变成
 * {@code [2026, 4, 30, 2, 48, 32, 135104]} 这种数组。
 *
 * <h3>当前方案（同时注册 2 个 Bean）</h3>
 *
 * <ol>
 *   <li><b>{@code primaryObjectMapper}（@Primary）</b>：作为全局通用 Mapper，承担所有
 *       「按类型注入 ObjectMapper」的场景。包括：
 *       <ul>
 *         <li>Spring MVC @RestController 的请求/响应序列化（接管 Spring Boot 自动配置的同名 Bean）</li>
 *         <li>Flowable 的 EventRegistry 等第三方库直接 {@code @Autowired ObjectMapper} 的位置</li>
 *         <li>业务代码里 {@code @RequiredArgsConstructor private final ObjectMapper objectMapper}
 *             的字段（不再因多 Bean 候选歧义而启动失败）</li>
 *       </ul>
 *       同时显式配置了 {@code yyyy-MM-dd HH:mm:ss} 日期格式和 GMT+8 时区，确保 LocalDateTime
 *       序列化为字符串而非数组。
 *   </li>
 *   <li><b>{@code oauth2ObjectMapper}（仅供 OAuth2 序列化）</b>：注册了
 *       {@link SecurityJackson2Modules}，专供 {@link RedisOAuth2AuthorizationService} 持久化
 *       {@code OAuth2Authorization} 时使用。必须用 {@code @Qualifier} 显式引用。
 *   </li>
 * </ol>
 *
 * <p>用 {@link Configuration} 而不是 {@code @AutoConfiguration}：因为本模块没有
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 文件，单独标 {@code @AutoConfiguration} 不会被 Spring Boot 发现并加载。改成普通
 * {@code @Configuration} 后，跟其他业务配置一样被 {@code @SpringBootApplication} 的组件扫描
 * 自动注册（前提是 {@code com.smart.auth.config} 在扫描根包之下，本项目已满足）。
 */
@Configuration
public class AuthJacksonConfiguration {

    /** Bean 名称常量，OAuth2 序列化场景使用 @Qualifier 引用 */
    public static final String OAUTH2_OBJECT_MAPPER = "oauth2ObjectMapper";

    /** 全局通用日期格式，与 application.yml 中 spring.jackson.date-format 保持一致。 */
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 默认时区 GMT+8，避免序列化出 UTC 时间引起前端「差 8 小时」。 */
    private static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai"));

    /**
     * 全局 {@link ObjectMapper} —— 标记为 {@link Primary}，所有按类型注入 ObjectMapper 的
     * 场景默认拿到这个。复用 Spring Boot 的 {@link Jackson2ObjectMapperBuilder}，自动应用
     * {@code spring.jackson.*} 配置（visibility / serialization features 等），同时再显式
     * 设置日期格式和时区作为兜底，避免某些 yml 配置缺失时退化成数组格式。
     */
    @Bean
    @Primary
    public ObjectMapper primaryObjectMapper(Jackson2ObjectMapperBuilder builder) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN);
        dateFormat.setTimeZone(DEFAULT_TIMEZONE);

        ObjectMapper mapper = builder
                .createXmlMapper(false)
                .timeZone(DEFAULT_TIMEZONE)
                .dateFormat(dateFormat)
                .build();
        // JavaTimeModule 通常 builder 已自动注册（Spring Boot 默认行为），这里显式补一次防御性编程
        mapper.registerModule(new JavaTimeModule());
        // 关闭 WRITE_DATES_AS_TIMESTAMPS：彻底杜绝 LocalDateTime 序列化成 [年,月,日,时,分,秒,纳秒] 数组。
        // 注意：spring.jackson.serialization.write-dates-as-timestamps=false 也能控制，
        // 但显式 disable 一次更安全，不依赖 yml 是否正确配置。
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean(OAUTH2_OBJECT_MAPPER)
    public ObjectMapper oauth2ObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // SecurityJackson2Modules 包含了 OAuth2 各种 Token / Authorization 的反序列化策略，
        // 必须在持久化 OAuth2Authorization 时使用，否则 Redis 反序列化会失败。
        mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }
}