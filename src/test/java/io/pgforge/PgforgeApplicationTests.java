package io.pgforge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class PgforgeApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {}

    @Test
    void healthEndpointReturnsOk() {
        RestClient client =
                RestClient.builder().baseUrl("http://localhost:" + port).build();
        ResponseEntity<String> response = client.get()
                .uri("/health/ledger")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"ok\":true");
    }
}
