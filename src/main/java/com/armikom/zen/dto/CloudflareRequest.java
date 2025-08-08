package com.armikom.zen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Request object for Cloudflare tunnel DNS operations")
public class CloudflareRequest {
    
    @Schema(description = "DNS name to create (e.g., zen-app1.armikom.com)", example = "zen-app1.armikom.com")
    @NotBlank(message = "DNS name cannot be blank")
    private String dnsName;
    
    @Schema(description = "Local port to expose", example = "45001")
    @NotNull(message = "Port cannot be null")
    @Min(value = 1, message = "Port must be greater than 0")
    @Max(value = 65535, message = "Port must be less than or equal to 65535")
    private Integer port;
    
    @Schema(description = "Protocol to use", example = "http", allowableValues = {"http", "https"})
    private String protocol = "http";
    
    public CloudflareRequest() {}
    
    public CloudflareRequest(String dnsName, Integer port) {
        this.dnsName = dnsName;
        this.port = port;
    }
    
    public CloudflareRequest(String dnsName, Integer port, String protocol) {
        this.dnsName = dnsName;
        this.port = port;
        this.protocol = protocol;
    }
    
    public String getDnsName() {
        return dnsName;
    }
    
    public void setDnsName(String dnsName) {
        this.dnsName = dnsName;
    }
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    @Override
    public String toString() {
        return "CloudflareRequest{" +
                "dnsName='" + dnsName + '\'' +
                ", port=" + port +
                ", protocol='" + protocol + '\'' +
                '}';
    }
}