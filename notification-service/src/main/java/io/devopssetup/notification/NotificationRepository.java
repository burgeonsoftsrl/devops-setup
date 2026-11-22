package io.devopssetup.notification;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserId(Long userId, Sort sort);

    List<Notification> findByOrderId(String orderId, Sort sort);
}
