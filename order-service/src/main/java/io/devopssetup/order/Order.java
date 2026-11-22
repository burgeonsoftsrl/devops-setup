package io.devopssetup.order;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Document("orders")
public record Order(
        @Id String id,
        @Indexed Long userId,
        String product,
        int quantity,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal price,
        Instant createdAt) {
}
