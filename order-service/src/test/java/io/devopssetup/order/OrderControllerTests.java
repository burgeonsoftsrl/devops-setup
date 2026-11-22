package io.devopssetup.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderRepository orders;

    @Test
    void createsOrder() throws Exception {
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"product\":\"Book\",\"quantity\":2,\"price\":9.99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.product").value("Book"));
    }

    @Test
    void listsOrdersByUser() throws Exception {
        when(orders.findByUserId(1L)).thenReturn(List.of(
                new Order("o-1", 1L, "Book", 1, new BigDecimal("9.99"), Instant.now())));

        mvc.perform(get("/api/orders").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("o-1"));
    }

    @Test
    void rejectsInvalidOrder() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"\",\"quantity\":0,\"price\":-1}"))
                .andExpect(status().isBadRequest());
    }
}
