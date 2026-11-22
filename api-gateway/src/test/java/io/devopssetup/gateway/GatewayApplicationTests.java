package io.devopssetup.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayApplicationTests {

    @Autowired
    RouteLocator routeLocator;

    @Test
    void routesAreConfigured() {
        List<String> ids = routeLocator.getRoutes().map(Route::getId).collectList().block();
        assertThat(ids).containsExactlyInAnyOrder("user-service", "order-service");
    }
}
