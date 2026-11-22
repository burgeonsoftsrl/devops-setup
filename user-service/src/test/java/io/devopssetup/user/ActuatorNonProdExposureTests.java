package io.devopssetup.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

/** Non-prod opt-in, as set by docker-compose.yml. */
@SpringBootTest(properties = "management.endpoints.web.exposure.include=health,info,prometheus")
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false) // tests disable metrics exporters by default
class ActuatorNonProdExposureTests {

    @Autowired
    MockMvc mvc;

    @Test
    void prometheusIsExposedWhenOptedIn() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }
}
