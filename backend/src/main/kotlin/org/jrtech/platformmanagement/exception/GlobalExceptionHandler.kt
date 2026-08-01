package org.jrtech.platformmanagement.exception

import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex.message ?: "Conflict")

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingRequestParam(ex: MissingServletRequestParameterException): ProblemDetail =
        problem(
            HttpStatus.BAD_REQUEST,
            ex.message ?: "Required request parameter '${ex.parameterName}' is not present"
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ProblemDetail =
        problem(
            HttpStatus.BAD_REQUEST,
            ex.message ?: "Invalid value for parameter '${ex.name}'"
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val details = ex.bindingResult.allErrors.associate { error ->
            val field = (error as? FieldError)?.field ?: error.objectName
            field to (error.defaultMessage ?: "invalid")
        }
        return problem(HttpStatus.BAD_REQUEST, "Validation failed").apply {
            setProperty("errors", details)
        }
    }

    /**
     * Method-security denials (@PreAuthorize) surface as AccessDeniedException.
     * Map them to 403 so they are not swallowed by the generic 500 handler.
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, ex.message ?: "Access is denied")

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, ex.message ?: "Full authentication is required")

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ProblemDetail =
        problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: ${ex.message}")

    private fun problem(status: HttpStatus, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            type = URI.create("about:blank")
            setProperty("timestamp", UtcTimestamps.now().toString())
        }
}
