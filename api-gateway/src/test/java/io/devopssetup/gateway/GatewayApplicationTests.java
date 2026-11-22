package io.devopssetup.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureWebTestClient
class GatewayApplicationTests {

    @Autowired
    RouteLocator routeLocator;

    @Autowired
    WebTestClient web;

    @Test
    void onlyHealthIsPublicByDefault() {
        web.get().uri("/actuator/health").exchange().expectStatus().isOk();
        web.get().uri("/actuator/prometheus").exchange().expectStatus().isNotFound();
        web.get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
        web.get().uri("/actuator/info").exchange().expectStatus().isNotFound();
    }

    @Test
    void routesAreConfigured() {
        List<String> ids = routeLocator.getRoutes().map(Route::getId).collectList().block();
        assertThat(ids).containsExactlyInAnyOrder(
                "user-service", "order-service", "payment-service", "notification-service");
    }
}
