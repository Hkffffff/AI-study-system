package com.aistudy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the whole application against a real MySQL (Flyway + JPA validation).
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("rawtypes")
    void healthReportsDatabaseUp() {
        var response = restTemplate.getForEntity("/api/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP").containsEntry("database", "UP");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void unknownApiPathIsProblemDetail() {
        var response = restTemplate.getForEntity("/api/does-not-exist", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "NOT_FOUND");
    }
}
