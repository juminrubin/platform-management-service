package com.example.participantapi

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ParticipantApiApplicationTests {

    @Test
    fun contextLoads() {
        // Verifies Spring context, Flyway migrations, and H2 start cleanly
    }
}
