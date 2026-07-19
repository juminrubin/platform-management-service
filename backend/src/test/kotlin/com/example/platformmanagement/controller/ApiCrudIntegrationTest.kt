package com.example.platformmanagement.controller

import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

/**
 * End-to-end HTTP coverage for controller CRUD paths and exception mapping.
 * Uses the open test profile (`app.security.permit-all=true`).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiCrudIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `participants CRUD filter validation and not found`() {
        val id = "api-p-${UUID.randomUUID().toString().take(8)}"

        mockMvc.post("/api/v1/participants") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"$id","name":"API Participant","contact":"a@x.com","status":"ACTIVE"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(id) }
        }

        mockMvc.get("/api/v1/participants/$id") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("API Participant") }
        }

        mockMvc.put("/api/v1/participants/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Renamed","contact":null,"status":"SUSPENDED"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUSPENDED") }
        }

        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            param("status", "SUSPENDED")
        }.andExpect {
            status { isOk() }
        }

        mockMvc.post("/api/v1/participants") {
            contentType = MediaType.APPLICATION_JSON
            // Blank id/name trip @NotBlank after Jackson constructs the DTO
            content = """{"id":"","name":"","status":"ACTIVE"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("Validation failed") }
            jsonPath("$.errors") { exists() }
        }

        mockMvc.get("/api/v1/participants/does-not-exist") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
        }

        mockMvc.delete("/api/v1/participants/$id").andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `service offerings caller identities entitlements and consumptions CRUD`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val participantId = "api-co-$suffix"
        val offeringId = "api-so-$suffix"

        mockMvc.post("/api/v1/participants") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"$participantId","name":"Co $suffix","status":"ACTIVE"}"""
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/service-offerings") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "id":"$offeringId",
                  "name":"Offering $suffix",
                  "description":"desc",
                  "category":"llm",
                  "config":"{}",
                  "active":true
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.category") { value("LLM") }
        }

        mockMvc.get("/api/v1/service-offerings/$offeringId") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(offeringId) }
        }

        mockMvc.put("/api/v1/service-offerings/$offeringId") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "name":"Updated $suffix",
                  "description":null,
                  "category":"speech",
                  "config":"{}",
                  "active":false
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(false) }
            jsonPath("$.category") { value("SPEECH") }
        }

        mockMvc.get("/api/v1/service-offerings") {
            accept = MediaType.APPLICATION_JSON
            param("activeOnly", "false")
            param("category", "SPEECH")
        }.andExpect { status { isOk() } }

        val callerBody = mockMvc.post("/api/v1/caller-identities") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "participantId":"$participantId",
                  "callerIdentity":"user-$suffix@example.com",
                  "status":"ACTIVE"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.participantId") { value(participantId) }
        }.andReturn().response.contentAsString

        val callerId = Regex(""""id"\s*:\s*"([^"]+)"""").find(callerBody)!!.groupValues[1]

        mockMvc.get("/api/v1/caller-identities/$callerId") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.callerIdentity") { value("user-$suffix@example.com") }
        }

        mockMvc.put("/api/v1/caller-identities/$callerId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"INACTIVE"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("INACTIVE") }
        }

        // Reactivate for entitlement/consumption checks
        mockMvc.put("/api/v1/caller-identities/$callerId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"ACTIVE"}"""
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/caller-identities") {
            accept = MediaType.APPLICATION_JSON
            param("participantId", participantId)
            param("status", "ACTIVE")
        }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(1))
        }

        val entitlementBody = mockMvc.post("/api/v1/entitlements") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "participantId":"$participantId",
                  "serviceOfferingId":"$offeringId",
                  "status":"ACTIVE",
                  "validFrom":"2024-01-01",
                  "validTo":"2030-12-31",
                  "config":"{\"max_tpm\":1}",
                  "notes":"api"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("ACTIVE") }
        }.andReturn().response.contentAsString

        val entitlementId = Regex(""""id"\s*:\s*"([^"]+)"""").find(entitlementBody)!!.groupValues[1]

        mockMvc.get("/api/v1/entitlements/$entitlementId") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.notes") { value("api") }
        }

        mockMvc.put("/api/v1/entitlements/$entitlementId") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "status":"ACTIVE",
                  "validFrom":"2024-01-01",
                  "validTo":null,
                  "config":"{}",
                  "notes":"updated"
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.notes") { value("updated") }
        }

        mockMvc.get("/api/v1/entitlements") {
            accept = MediaType.APPLICATION_JSON
            param("participantId", participantId)
            param("status", "ACTIVE")
        }.andExpect { status { isOk() } }

        val consumptionBody = mockMvc.post("/api/v1/consumptions") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "participantCallerIdentityId":"$callerId",
                  "serviceOfferingId":"$offeringId",
                  "consumptionData":"{\"input_token\":3}",
                  "consumedAt":"2024-08-01T12:00:00Z"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.serviceOfferingId") { value(offeringId) }
        }.andReturn().response.contentAsString

        val consumptionId = Regex(""""id"\s*:\s*"([^"]+)"""").find(consumptionBody)!!.groupValues[1]

        mockMvc.get("/api/v1/consumptions/$consumptionId") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.createdAt") { value("2024-08-01T12:00:00Z") }
        }

        mockMvc.get("/api/v1/consumptions") {
            accept = MediaType.APPLICATION_JSON
            param("participantCallerIdentityId", callerId)
            param("serviceOfferingId", offeringId)
        }.andExpect { status { isOk() } }

        mockMvc.delete("/api/v1/consumptions/$consumptionId").andExpect { status { isNoContent() } }
        mockMvc.delete("/api/v1/entitlements/$entitlementId").andExpect { status { isNoContent() } }
        mockMvc.delete("/api/v1/caller-identities/$callerId").andExpect { status { isNoContent() } }
        mockMvc.delete("/api/v1/service-offerings/$offeringId").andExpect { status { isNoContent() } }
        mockMvc.delete("/api/v1/participants/$participantId").andExpect { status { isNoContent() } }
    }

    @Test
    fun `conflict and bad request map through exception handler`() {
        mockMvc.post("/api/v1/participants") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"acme-corp","name":"Duplicate Acme","status":"ACTIVE"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.detail") { exists() }
        }

        mockMvc.get("/api/v1/entitlements/check") {
            accept = MediaType.APPLICATION_JSON
            param("serviceOfferingId", "gpt-5.1")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
