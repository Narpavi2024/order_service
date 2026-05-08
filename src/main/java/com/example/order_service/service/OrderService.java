package com.example.order_service.service;

import com.example.order_service.Client.InventoryClient;
import com.example.order_service.dto.OrderRequest;
import com.example.order_service.event.OrderPlacedEvent;
import com.example.order_service.model.Order;
import com.example.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    // ✅ FIXED: Use Object as value type — works with JsonSerializer
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void placeOrder(@RequestBody OrderRequest orderRequest) {
        var isProduct = inventoryClient.isInStock(orderRequest.skuCode(), orderRequest.quantity());

        if (isProduct) {
            var order = mapToOrder(orderRequest);
            orderRepository.save(order);

            var orderPlacedEvent = new OrderPlacedEvent(
                    order.getOrderNumber(),
                    orderRequest.userDetails().email(),
                    orderRequest.userDetails().firstName(),
                    orderRequest.userDetails().lastName()
            );

            log.info("Start - Sending OrderPlacedEvent {} to Kafka Topic", orderPlacedEvent);
            kafkaTemplate.send("order-placed", orderPlacedEvent);
            log.info("End - Sending OrderPlacedEvent {} to Kafka Topic", orderPlacedEvent);

        } else {
            throw new RuntimeException("Product with SkuCode " + orderRequest.skuCode() + " is not in Stock");
        }
    }

    private static Order mapToOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setPrice(orderRequest.price());
        order.setQuantity(orderRequest.quantity());
        order.setSkuCode(orderRequest.skuCode());
        return order;
    }
}