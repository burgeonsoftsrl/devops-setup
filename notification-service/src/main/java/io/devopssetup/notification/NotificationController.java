package io.devopssetup.notification;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "receivedAt");

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<Notification> list(@RequestParam(required = false) Long userId,
                                   @RequestParam(required = false) String orderId) {
        if (orderId != null) {
            return notifications.findByOrderId(orderId, NEWEST_FIRST);
        }
        return userId == null ? notifications.findAll(NEWEST_FIRST) : notifications.findByUserId(userId, NEWEST_FIRST);
    }
}
