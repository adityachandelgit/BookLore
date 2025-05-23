package com.adityachandel.booklore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Baseline sanity‑check: verifies the Spring context can start.
 * No assertions needed—if any bean fails to initialize, the test will fail.
 */
@SpringBootTest
@ActiveProfiles("test")
class SmokeTest {

    @Test
    void contextLoads() {
        // empty on purpose
    }
}
