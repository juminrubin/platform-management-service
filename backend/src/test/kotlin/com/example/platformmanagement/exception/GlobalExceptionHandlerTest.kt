package com.example.platformmanagement.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import kotlin.reflect.jvm.javaMethod

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `maps not found conflict and bad request`() {
        val notFound = handler.handleNotFound(ResourceNotFoundException("gone"))
        assertThat(notFound.status).isEqualTo(HttpStatus.NOT_FOUND.value())
        assertThat(notFound.detail).isEqualTo("gone")
        assertThat(notFound.properties?.get("timestamp")).isNotNull()

        val conflict = handler.handleConflict(ConflictException("dup"))
        assertThat(conflict.status).isEqualTo(HttpStatus.CONFLICT.value())
        assertThat(conflict.detail).isEqualTo("dup")

        val bad = handler.handleBadRequest(BadRequestException("bad"))
        assertThat(bad.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        assertThat(bad.detail).isEqualTo("bad")
    }

    @Test
    fun `maps blank-message style defaults for security handlers`() {
        // Kotlin exception constructors require non-null messages; Spring security exceptions allow null.
        assertThat(handler.handleAccessDenied(AccessDeniedException("")).detail)
            .isEqualTo("")
        assertThat(handler.handleAuthentication(BadCredentialsException("")).detail)
            .isEqualTo("")
        // Explicit non-null messages still map correctly
        assertThat(handler.handleNotFound(ResourceNotFoundException("missing")).detail).isEqualTo("missing")
    }

    @Test
    fun `maps validation errors with field details`() {
        val target = object {
            var name: String? = null
        }
        val binding = BeanPropertyBindingResult(target, "target")
        binding.addError(FieldError("target", "name", "must not be blank"))
        val method = this::sample.javaMethod!!
        val parameter = MethodParameter(method, -1)
        val ex = MethodArgumentNotValidException(parameter, binding)

        val problem = handler.handleValidation(ex)
        assertThat(problem.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        assertThat(problem.detail).isEqualTo("Validation failed")
        @Suppress("UNCHECKED_CAST")
        val errors = problem.properties?.get("errors") as Map<String, String>
        assertThat(errors["name"]).isEqualTo("must not be blank")
    }

    @Test
    fun `maps access denied authentication and generic errors`() {
        val forbidden = handler.handleAccessDenied(AccessDeniedException("nope"))
        assertThat(forbidden.status).isEqualTo(HttpStatus.FORBIDDEN.value())
        assertThat(forbidden.detail).isEqualTo("nope")

        val unauthorized = handler.handleAuthentication(BadCredentialsException("bad token"))
        assertThat(unauthorized.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
        assertThat(unauthorized.detail).isEqualTo("bad token")

        val server = handler.handleGeneric(IllegalStateException("boom"))
        assertThat(server.status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value())
        assertThat(server.detail).contains("boom")
    }

    @Suppress("unused")
    private fun sample() = Unit
}
