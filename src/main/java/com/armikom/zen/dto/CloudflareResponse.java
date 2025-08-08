package com.armikom.zen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object for Cloudflare tunnel DNS operations")
public class CloudflareResponse {
    
    @Schema(description = "Operation success status", example = "true")
    private boolean success;
    
    @Schema(description = "Response message", example = "DNS record created successfully")
    private String message;
    
    @Schema(description = "Created DNS name", example = "zen-app1.armikom.com")
    private String dnsName;
    
    @Schema(description = "DNS record ID from Cloudflare", example = "abc123def456")
    private String recordId;
    
    @Schema(description = "Target URL being exposed", example = "http://localhost:45001")
    private String targetUrl;
    
    public CloudflareResponse() {}
    
    public CloudflareResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public CloudflareResponse(boolean success, String message, String dnsName, String recordId, String targetUrl) {
        this.success = success;
        this.message = message;
        this.dnsName = dnsName;
        this.recordId = recordId;
        this.targetUrl = targetUrl;
    }
    
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
    
    public String getDnsName() {
        return dnsName;
    }
    
    public void setDnsName(String dnsName) {
        this.dnsName = dnsName;
    }
    
    public String getRecordId() {
        return recordId;
    }
    
    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }
    
    public String getTargetUrl() {
        return targetUrl;
    }
    
    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }
    
    @Override
    public String toString() {
        return "CloudflareResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", dnsName='" + dnsName + '\'' +
                ", recordId='" + recordId + '\'' +
                ", targetUrl='" + targetUrl + '\'' +
                '}';
    }
}