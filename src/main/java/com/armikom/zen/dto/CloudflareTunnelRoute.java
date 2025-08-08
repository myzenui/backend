package com.armikom.zen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Cloudflare tunnel route configuration
 */
public class CloudflareTunnelRoute {
    
    @JsonProperty("hostname")
    private String hostname;
    
    @JsonProperty("service")
    private String service;
    
    @JsonProperty("path")
    private String path;
    
    @JsonProperty("originRequest")
    private OriginRequest originRequest;
    
    public CloudflareTunnelRoute() {}
    
    public CloudflareTunnelRoute(String hostname, String service) {
        this.hostname = hostname;
        this.service = service;
    }
    
    public String getHostname() {
        return hostname;
    }
    
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
    
    public String getService() {
        return service;
    }
    
    public void setService(String service) {
        this.service = service;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public OriginRequest getOriginRequest() {
        return originRequest;
    }
    
    public void setOriginRequest(OriginRequest originRequest) {
        this.originRequest = originRequest;
    }
    
    public static class OriginRequest {
        @JsonProperty("connectTimeout")
        private String connectTimeout = "30s";
        
        @JsonProperty("tlsTimeout")
        private String tlsTimeout = "10s";
        
        @JsonProperty("tcpKeepAlive")
        private String tcpKeepAlive = "30s";
        
        @JsonProperty("keepAliveConnections")
        private Integer keepAliveConnections = 100;
        
        @JsonProperty("keepAliveTimeout")
        private String keepAliveTimeout = "90s";
        
        public OriginRequest() {}
        
        // Getters and setters
        public String getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(String connectTimeout) { this.connectTimeout = connectTimeout; }
        
        public String getTlsTimeout() { return tlsTimeout; }
        public void setTlsTimeout(String tlsTimeout) { this.tlsTimeout = tlsTimeout; }
        
        public String getTcpKeepAlive() { return tcpKeepAlive; }
        public void setTcpKeepAlive(String tcpKeepAlive) { this.tcpKeepAlive = tcpKeepAlive; }
        
        public Integer getKeepAliveConnections() { return keepAliveConnections; }
        public void setKeepAliveConnections(Integer keepAliveConnections) { this.keepAliveConnections = keepAliveConnections; }
        
        public String getKeepAliveTimeout() { return keepAliveTimeout; }
        public void setKeepAliveTimeout(String keepAliveTimeout) { this.keepAliveTimeout = keepAliveTimeout; }
    }
}