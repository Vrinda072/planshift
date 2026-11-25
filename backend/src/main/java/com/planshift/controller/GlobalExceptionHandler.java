package com.planshift.controller;

import com.planshift.experiment.QueueFullException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getReason() != null ? e.getReason() : "Request failed"));
    }

    /**
     * An unmapped route (e.g. hitting "/" directly -- this is a REST API
     * with no homepage). NoResourceFoundException extends ServletException,
     * not ResponseStatusException, so without this it would otherwise fall
     * through to the generic 500 handler below and misreport a plain 404 as
     * a server error.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not found"));
    }

    @ExceptionHandler(QueueFullException.class)
    public ResponseEntity<Map<String, String>> handleQueueFull(QueueFullException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", e.getMessage()));
    }

    /**
     * A filter value that doesn't fit its column ("abc" against a number, say) is only discovered by Postgres.
     * SQLSTATE class 22 is "data exception" and 42883 "no such operator" (LIKE on a number); both are bad input.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDataAccess(DataAccessException e) {
        if (e.getMostSpecificCause() instanceof SQLException sql && sql.getSQLState() != null
                && (sql.getSQLState().startsWith("22") || sql.getSQLState().equals("42883"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "That filter value or operator doesn't fit the column's type."));
        }
        return handleUnexpected(e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("Unexpected error handling request", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred"));
    }
}
