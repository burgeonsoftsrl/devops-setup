package io.devopssetup.payment;

import io.devopssetup.common.events.PaymentProcessed.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PaymentRepository payments;

    @Test
    void findsPaymentByOrder() throws Exception {
        when(payments.findByOrderId("o-1")).thenReturn(Optional.of(
                new Payment("o-1", 1L, new BigDecimal("9.99"), Status.APPROVED)));

        mvc.perform(get("/api/payments").param("orderId", "o-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void unknownPaymentIs404() throws Exception {
        when(payments.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/payments/99")).andExpect(status().isNotFound());
    }
}
