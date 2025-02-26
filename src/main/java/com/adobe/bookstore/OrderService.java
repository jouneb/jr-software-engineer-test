package com.adobe.bookstore;

import com.adobe.bookstore.BookStock;
import com.adobe.bookstore.BookStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class OrderService {

    @Autowired
    private BookStockRepository bookStockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Transactional
public String processOrder(List<OrderItem> items) {
    for (OrderItem item : items) {
        Optional<BookStock> bookStockOpt = bookStockRepository.findById(item.getBookId());
        if (bookStockOpt.isEmpty()){
            throw new IllegalArgumentException("Not existing book: " + item.getBookId());
        }
        if (bookStockOpt.get().getQuantity() < item.getQuantity()) {
            throw new IllegalArgumentException("Not enough stock for book: " + item.getBookId());
        }
        if (item.getQuantity() <= 0) {
            throw new IllegalArgumentException("Invalid quantity for book: " + item.getBookId());
        }
    }

    Order order = new Order();
    order.setItems(items);
    items.forEach(item -> item.setOrder(order));

    orderRepository.save(order);
    items.forEach(orderItemRepository::save);

    updateStockAsync(items);

    return "Order processed successfully. Order ID: " + order.getId();
}

    @Transactional(readOnly = true) 
    public List<Order> getAllOrders() {
        return orderRepository.findAll(); 
    }

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    // Update the book stock asynchronous
    private void updateStockAsync(List<OrderItem> items) {
        new Thread(() -> {
            for (OrderItem item : items) {
                if (item.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Invalid quantity for book: " + item.getBookId());
                }
                try {
                    BookStock bookStock = bookStockRepository.findById(item.getBookId())
                        .orElseThrow(() -> new RuntimeException("BookStock not found for bookId: " + item.getBookId()));
    
                    bookStock.setQuantity(bookStock.getQuantity() - item.getQuantity());
                    bookStockRepository.save(bookStock);
                } catch (Exception e) {
                    logger.error("Error updating stock for bookId: " + item.getBookId(), e);
                }
            }
        }).start();
    }
}