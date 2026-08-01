package org.jrtech.platformmanagement.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * Verifies Entra app roles map to the correct endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig::class)
@TestPropertySource(
    properties = [
        "app.security.permit-all=false",
        "app.security.required-scope="
    ]
)
class AppRoleAuthorizationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `System Maintainer can list participants`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.SYSTEM_MAINTAINER))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `System Reader can list participants`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.SYSTEM_READER))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `System Reader can list entitlements and consumptions`() {
        mockMvc.get("/api/v1/entitlements") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.SYSTEM_READER))
        }.andExpect {
            status { isOk() }
        }

        mockMvc.get("/api/v1/consumptions") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.SYSTEM_READER))
        }.andExpect {
            status { isOk() }
        }

        mockMvc.get("/api/v1/service-offerings") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.SYSTEM_READER))
        }.andExpect {
            status { isOk() }
        }

        mockMvc.get("/api/v1/caller-registrations") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.SYSTEM_READER))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `System Reader can check entitlement`() {
        mockMvc.get("/api/v1/entitlements/check") {
            accept = MediaType.APPLICATION_JSON
            param("callerId", "alice@acme.example")
            param("serviceOfferingId", "gpt-5.1")
            param("fromDate", "2024-06-15")
            param("untilDate", "2024-06-15")
            with(jwtWithRoles(AppRoles.SYSTEM_READER))
        }.andExpect {
            status { isOk() }
            jsonPath("$.allowed") { value(true) }
        }
    }

    @Test
    fun `System Reader cannot create participants`() {
        mockMvc.post("/api/v1/participants") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "id": "reader-blocked",
                  "name": "Should Fail",
                  "contact": "x@example.com",
                  "status": "ACTIVE"
                }
            """.trimIndent()
            with(jwtWithRoles(AppRoles.SYSTEM_READER))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `System Reader cannot POST consumption`() {
        mockMvc.post("/api/v1/consumptions") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "callerId": "alice@acme.example",
                  "serviceOfferingId": "gpt-5.1",
                  "consumptionData": "{}"
                }
            """.trimIndent()
            with(jwtWithRoles(AppRoles.SYSTEM_READER))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `Entitlement Reader cannot list participants`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.ENTITLEMENT_READER))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `Entitlement Reader can check entitlement by caller id`() {
        // Seed entitlement for acme-corp / gpt-5.1 is valid 2024-01-15 .. 2025-12-31
        mockMvc.get("/api/v1/entitlements/check") {
            accept = MediaType.APPLICATION_JSON
            param("callerId", "alice@acme.example")
            param("serviceOfferingId", "gpt-5.1")
            param("fromDate", "2024-06-15")
            param("untilDate", "2024-06-15")
            with(jwtWithRoles(AppRoles.ENTITLEMENT_READER))
        }.andExpect {
            status { isOk() }
            jsonPath("$.allowed") { value(true) }
            jsonPath("$.reason") { value("ALLOWED") }
            jsonPath("$.participantId") { value("acme-corp") }
            jsonPath("$.serviceOfferingId") { value("gpt-5.1") }
            jsonPath("$.callerId") { value("alice@acme.example") }
        }
    }

    @Test
    fun `Entitlement Reader cannot list all entitlements`() {
        mockMvc.get("/api/v1/entitlements") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.ENTITLEMENT_READER))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `Consumption Registrator can POST consumption`() {
        val sourceRefId = "req-authz-${java.util.UUID.randomUUID()}"
        mockMvc.post("/api/v1/consumptions") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "callerId": "alice@acme.example",
                  "serviceOfferingId": "gpt-5.1",
                  "sourceRefId": "$sourceRefId",
                  "consumptionData": "{\"input_token\":10,\"output_token\":5}",
                  "capturedAt": "2024-07-01T12:00:00Z"
                }
            """.trimIndent()
            with(jwtWithRoles(AppRoles.CONSUMPTION_REGISTRATOR))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.serviceOfferingId") { value("gpt-5.1") }
            jsonPath("$.callerId") { value("alice@acme.example") }
            jsonPath("$.sourceRefId") { value(sourceRefId) }
            jsonPath("$.capturedAt") { value("2024-07-01T12:00:00Z") }
            jsonPath("$.createdAt") { exists() }
        }
    }

    @Test
    fun `Consumption Registrator cannot list consumptions`() {
        mockMvc.get("/api/v1/consumptions") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.CONSUMPTION_REGISTRATOR))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `System Maintainer can control Event Hub connector start and stop`() {
        mockMvc.get("/api/v1/connectors/consumption-eventhub") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.SYSTEM_MAINTAINER))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value("consumption-eventhub") }
            jsonPath("$.enabled") { value(false) }
            jsonPath("$.running") { value(false) }
        }

        // Disabled by default — start returns 400, still authorized as Maintainer
        mockMvc.post("/api/v1/connectors/consumption-eventhub/start") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.SYSTEM_MAINTAINER))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `System Reader cannot start Event Hub connector`() {
        mockMvc.post("/api/v1/connectors/consumption-eventhub/start") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.SYSTEM_READER))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `System Reader cannot list connectors`() {
        mockMvc.get("/api/v1/connectors") {
            accept = MediaType.APPLICATION_JSON
            with(jwtWithRoles(AppRoles.SYSTEM_READER))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `Consumption Registrator cannot check entitlements`() {
        mockMvc.get("/api/v1/entitlements/check") {
            accept = MediaType.APPLICATION_JSON
            param("callerId", "alice@acme.example")
            param("serviceOfferingId", "gpt-5.1")
            with(jwtWithRoles(AppRoles.CONSUMPTION_REGISTRATOR))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `JWT without app roles is forbidden on protected endpoints`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("user-without-roles")
                    }
            )
        }.andExpect {
            status { isForbidden() }
        }
    }

    private fun jwtWithRoles(vararg roles: String) =
        jwt()
            .jwt { token ->
                token.subject("principal-${roles.joinToString("-")}")
                token.claim("roles", roles.toList())
                token.claim("tid", "tenant-guid")
            }
            .authorities(*roles.map { SimpleGrantedAuthority("ROLE_$it") }.toTypedArray())
}
