package com.example.participantapi.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import com.example.participantapi.domain.UtcTimestamps
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

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ProblemDetail =
        problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: ${ex.message}")

    private fun problem(status: HttpStatus, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            type = URI.create("about:blank")
            setProperty("timestamp", UtcTimestamps.now().toString())
        }
}
