package io.devopssetup.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository payments;

    public PaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping
    public List<Payment> list(@RequestParam(required = false) Long userId,
                              @RequestParam(required = false) String orderId) {
        if (orderId != null) {
            return payments.findByOrderId(orderId).map(List::of).orElse(List.of());
        }
        return userId == null ? payments.findAll() : payments.findByUserId(userId);
    }

    @GetMapping("/{id}")
    public Payment get(@PathVariable Long id) {
        return payments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment " + id + " not found"));
    }
}
