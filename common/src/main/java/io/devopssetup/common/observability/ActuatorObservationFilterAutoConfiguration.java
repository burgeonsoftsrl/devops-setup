package io.devopssetup.common.observability;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Don't trace/time requests to {@code /actuator/**}: Render health checks and Prometheus scrapes
 * would otherwise flood the trace backend (and Grafana Cloud's free quota) with noise.
 */
@AutoConfiguration
@ConditionalOnClass(ObservationPredicate.class)
public class ActuatorObservationFilterAutoConfiguration {

    static boolean isActuator(String path) {
        return path != null && path.startsWith("/actuator");
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class Servlet {
        @Bean
        ObservationPredicate ignoreActuatorServletRequests() {
            return (name, context) -> !(context instanceof org.springframework.http.server.observation.ServerRequestObservationContext c)
                    || !isActuator(c.getCarrier().getRequestURI());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class Reactive {
        @Bean
        ObservationPredicate ignoreActuatorReactiveRequests() {
            return (name, context) -> !(context instanceof org.springframework.http.server.reactive.observation.ServerRequestObservationContext c)
                    || !isActuator(c.getCarrier().getPath().value());
        }
    }
}
