package io.devopssetup.order;

import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface OrderRepository extends ListCrudRepository<Order, String> {

    List<Order> findByUserId(Long userId);
}
