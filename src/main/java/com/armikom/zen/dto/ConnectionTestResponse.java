package com.armikom.zen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for database connection test
 */
@Schema(description = "Response for database connection test")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectionTestResponse {

    @Schema(description = "Whether the connection test was successful", example = "true")
    private boolean success;

    @Schema(description = "Connection test message", example = "Database connection successful")
    private String message;

    @Schema(description = "Database server information", example = "MySQL 8.0.33")
    private String serverInfo;

    @Schema(description = "Connection timeout in milliseconds", example = "5000")
    private Long timeoutMs;

    @Schema(description = "Response time in milliseconds", example = "150")
    private Long responseTimeMs;

    @Schema(description = "Timestamp of the connection test", example = "2024-01-15T10:30:00Z")
    private String timestamp;

    @Schema(description = "Error details if connection failed")
    private String errorDetails;

    // Default constructor
    public ConnectionTestResponse() {
        this.timestamp = java.time.Instant.now().toString();
    }

    // Constructor
    public ConnectionTestResponse(boolean success, String message) {
        this();
        this.success = success;
        this.message = message;
    }

    // Static factory methods
    public static ConnectionTestResponse success(String message) {
        return new ConnectionTestResponse(true, message);
    }

    public static ConnectionTestResponse error(String message) {
        return new ConnectionTestResponse(false, message);
    }

    public static ConnectionTestResponse error(String message, String errorDetails) {
        ConnectionTestResponse response = new ConnectionTestResponse(false, message);
        response.setErrorDetails(errorDetails);
        return response;
    }

    // Builder-style methods
    public ConnectionTestResponse withServerInfo(String serverInfo) {
        this.serverInfo = serverInfo;
        return this;
    }

    public ConnectionTestResponse withTimeout(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    public ConnectionTestResponse withResponseTime(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
        return this;
    }

    public ConnectionTestResponse withErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
        return this;
    }

    // Getters and setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(String serverInfo) {
        this.serverInfo = serverInfo;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    @Override
    public String toString() {
        return "ConnectionTestResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", serverInfo='" + serverInfo + '\'' +
                ", timeoutMs=" + timeoutMs +
                ", responseTimeMs=" + responseTimeMs +
                ", timestamp='" + timestamp + '\'' +
                ", errorDetails='" + errorDetails + '\'' +
                '}';
    }
}

