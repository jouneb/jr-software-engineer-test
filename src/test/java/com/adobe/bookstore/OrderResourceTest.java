package com.adobe.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderResourceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    // Test case 1: Creating an order with valid data (successful order)
    @Test
    @Sql(statements = "INSERT INTO book_stock (id, name, quantity) VALUES ('11111-22222', 'The Ancient Woods', 10)")
    public void shouldCreateOrder1() {
        String orderJson = """
            [
                {
                    "bookId": "11111-22222",
                    "quantity": 2
                }
            ]
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(orderJson, headers);

        var result = restTemplate.postForEntity("http://localhost:" + port + "/orders/", request, String.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).matches("Order processed successfully\\. Order ID: \\d+");

        // Verify that stock was updated (10 - 2 = 8)
        var stockResponse = restTemplate.getForEntity("http://localhost:" + port + "/books_stock/11111-22222", String.class);
        assertThat(stockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Look for substring without a space after colon
        assertThat(stockResponse.getBody()).contains("\"quantity\":8");
    }

    // Test case 2: Creating an order with a non-existing book ID (should return an error)
    @Test
    @Sql(statements = "INSERT INTO book_stock (id, name, quantity) VALUES ('33333-44444', 'Whispers in the Dark', 1)")
    public void notShouldCreateOrder1() {
        String orderJson = """
            [
                {
                    "bookId": "00000-11111",
                    "quantity": 2
                }
            ]
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(orderJson, headers);

        var result = restTemplate.postForEntity("http://localhost:" + port + "/orders/", request, String.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).matches("Error: Not existing book: 00000-11111");
    }

    // Test case 3: Creating an order with multiple books, all in stock (successful order)
    @Test
    @Sql(statements = "INSERT INTO book_stock (id, name, quantity) VALUES ('22222-33333', 'Silent Echoes', 5)")
    @Sql(statements = "INSERT INTO book_stock (id, name, quantity) VALUES ('44444-55555', 'Beneath the Storm', 6)")
    public void shouldCreateOrder2() {
        String orderJson = """
            [
                {
                    "bookId": "22222-33333",
                    "quantity": 2
                },
                {
                    "bookId": "44444-55555",
                    "quantity": 3
                }
            ]
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(orderJson, headers);

        var result = restTemplate.postForEntity("http://localhost:" + port + "/orders/", request, String.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).matches("Order processed successfully\\. Order ID: \\d+");

        // Verify that stock was updated:
        // For '22222-33333': 5 - 2 = 3
        // For '44444-55555': 6 - 3 = 3
        var stockResponse1 = restTemplate.getForEntity("http://localhost:" + port + "/books_stock/22222-33333", String.class);
        var stockResponse2 = restTemplate.getForEntity("http://localhost:" + port + "/books_stock/44444-55555", String.class);
        assertThat(stockResponse1.getBody()).contains("\"quantity\":3");
        assertThat(stockResponse2.getBody()).contains("\"quantity\":3");
    }

    // Test case 4: Trying to order more books than available in stock (should return an error)
    @Test
    @Sql(statements = "INSERT INTO book_stock (id, name, quantity) VALUES ('55555-66666', 'The Burning Horizon', 1)")
    public void notEnoughStock() {
        String orderJson = """
            [
                {
                    "bookId": "55555-66666",
                    "quantity": 3
                }
            ]
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(orderJson, headers);

        var result = restTemplate.postForEntity("http://localhost:" + port + "/orders/", request, String.class);
        // Expected error due to insufficient stock
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).matches("Error: Not enough stock for book: 55555-66666");
    }

    // Test case 5: Rejecting invalid order quantities (negative quantity)
    @Test
    @Sql(statements = "INSERT INTO book_stock (id, name, quantity) VALUES ('66666-77777', 'The Edge of Time', 10)")
    public void shouldRejectInvalidOrderQuantity() {
        String orderJson = """
            [
                {
                    "bookId": "66666-77777",
                    "quantity": -1
                }
            ]
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(orderJson, headers);

        var result = restTemplate.postForEntity("http://localhost:" + port + "/orders/", request, String.class);
        // Expected error due to negative quantity
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Test case 6: Rejecting orders with invalid book ID format (empty book ID)
    @Test
    @Sql(statements = "INSERT INTO book_stock (id, name, quantity) VALUES ('77777-88888', 'Eternal Night', 10)")
    public void shouldRejectInvalidOrderId() {
        String orderJson = """
            [
                {
                    "bookId": "",
                    "quantity": 2
                }
            ]
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(orderJson, headers);

        var result = restTemplate.postForEntity("http://localhost:" + port + "/orders/", request, String.class);
        // Expected error due to empty book ID
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Test case 7: Creating an order with multiple books, checking the entire order and stock update (successful order)
    @Test
    @Sql(statements = "INSERT INTO book_stock (id, name, quantity) VALUES ('88888-99999', 'The Last Dawn', 5)")
    @Sql(statements = "INSERT INTO book_stock (id, name, quantity) VALUES ('99999-00000', 'Echoes of Eternity', 6)")
    public void shouldCreateOrder3() {
        String orderJson = """
            [
                {
                    "bookId": "88888-99999",
                    "quantity": 2
                },
                {
                    "bookId": "99999-00000",
                    "quantity": 3
                }
            ]
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(orderJson, headers);

        var resultPost = restTemplate.postForEntity("http://localhost:" + port + "/orders/", request, String.class);
        assertThat(resultPost.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resultPost.getBody()).matches("Order processed successfully\\. Order ID: \\d+");

        // Retrieve orders to verify the newly created order details
        var resultGet = restTemplate.getForEntity("http://localhost:" + port + "/orders/", Order[].class);
        assertThat(resultGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resultGet.getBody()).isNotEmpty();

        Order[] orders = resultGet.getBody();
        Order order = orders[orders.length - 1];

        // Verify order details
        assertThat(order.getId()).isNotNull();
        assertThat(order.getItems()).hasSize(2);
        OrderItem item1 = order.getItems().get(0);
        OrderItem item2 = order.getItems().get(1);
        assertThat(item1.getBookId()).isEqualTo("88888-99999");
        assertThat(item1.getQuantity()).isEqualTo(2);
        assertThat(item2.getBookId()).isEqualTo("99999-00000");
        assertThat(item2.getQuantity()).isEqualTo(3);

        // Verify that stock was updated:
        // For '88888-99999': 5 - 2 = 3
        // For '99999-00000': 6 - 3 = 3
        var stockResponse1 = restTemplate.getForEntity("http://localhost:" + port + "/books_stock/88888-99999", String.class);
        var stockResponse2 = restTemplate.getForEntity("http://localhost:" + port + "/books_stock/99999-00000", String.class);
        assertThat(stockResponse1.getBody()).contains("\"quantity\":3");
        assertThat(stockResponse2.getBody()).contains("\"quantity\":3");
    }
}
