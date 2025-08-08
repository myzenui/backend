package com.armikom.zen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Request object for configuring tunnel routes")
public class TunnelRouteRequest {
    
    @Schema(description = "Hostname to route (e.g., zen-app1.armikom.com)", example = "zen-app1.armikom.com")
    @NotBlank(message = "Hostname cannot be blank")
    private String hostname;
    
    @Schema(description = "Local port to route traffic to", example = "45001")
    @NotNull(message = "Port cannot be null")
    @Min(value = 1, message = "Port must be greater than 0")
    @Max(value = 65535, message = "Port must be less than or equal to 65535")
    private Integer port;
    
    @Schema(description = "Protocol to use", example = "http", allowableValues = {"http", "https"})
    private String protocol = "http";
    
    @Schema(description = "Path to route (optional)", example = "/api")
    private String path;
    
    public TunnelRouteRequest() {}
    
    public TunnelRouteRequest(String hostname, Integer port) {
        this.hostname = hostname;
        this.port = port;
    }
    
    public TunnelRouteRequest(String hostname, Integer port, String protocol) {
        this.hostname = hostname;
        this.port = port;
        this.protocol = protocol;
    }
    
    public String getHostname() {
        return hostname;
    }
    
    public void setHostname(String hostname) {
        this.hostname = hostname;
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
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getServiceUrl() {
        return protocol + "://localhost:" + port;
    }
    
    @Override
    public String toString() {
        return "TunnelRouteRequest{" +
                "hostname='" + hostname + '\'' +
                ", port=" + port +
                ", protocol='" + protocol + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}