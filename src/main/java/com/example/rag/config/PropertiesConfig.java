package com.example.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 让 {@link RagProperties} 真正被 Spring 识别并绑定。
 *
 * <p>有两种做法：在 {@code RagApplication} 上加 {@code @ConfigurationPropertiesScan}，
 * 或者像这里一样用 {@code @EnableConfigurationProperties} 显式指定。
 * 后者更明确，出问题时容易定位。
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class PropertiesConfig {
}
