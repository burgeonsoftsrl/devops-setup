package io.devopssetup.common.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Ships Logback output as OTLP log records (with trace/span context) through the OpenTelemetry SDK
 * that Spring Boot already configures - no Java agent. Enabled when an OTLP logs endpoint is set
 * (see {@link ObservabilityEnvironmentPostProcessor}).
 */
@AutoConfiguration(after = OpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({OpenTelemetryAppender.class, LoggerContext.class})
@ConditionalOnProperty(name = "management.otlp.logging.export.enabled", havingValue = "true")
public class OtlpLogsAutoConfiguration {

    static final String APPENDER_NAME = "OTLP";

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    InitializingBean otlpLogAppenderInstaller(OpenTelemetry openTelemetry) {
        return () -> {
            if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
                return;
            }
            Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            if (root.getAppender(APPENDER_NAME) == null) {
                OpenTelemetryAppender appender = new OpenTelemetryAppender();
                appender.setName(APPENDER_NAME);
                appender.setContext(context);
                appender.setCaptureKeyValuePairAttributes(true);
                appender.setCaptureLoggerContext(false);
                appender.start();
                root.addAppender(appender);
            }
            OpenTelemetryAppender.install(openTelemetry);
        };
    }
}
