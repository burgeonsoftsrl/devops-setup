package io.devopssetup.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Secure by default: without MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE only health is exposed (prod). */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorExposureTests {

    @Autowired
    MockMvc mvc;

    @Test
    void onlyHealthIsPublicByDefault() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/info")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isNotFound());
    }
}
