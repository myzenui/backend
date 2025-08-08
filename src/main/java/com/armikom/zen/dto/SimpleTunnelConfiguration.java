package com.armikom.zen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * Simplified DTO for Cloudflare tunnel configuration
 * Avoids complex OriginRequest settings that cause JSON parsing issues
 */
public class SimpleTunnelConfiguration {
    
    @JsonProperty("ingress")
    private List<SimpleIngressRule> ingress;
    
    public SimpleTunnelConfiguration() {
        this.ingress = new ArrayList<>();
    }
    
    public List<SimpleIngressRule> getIngress() {
        return ingress;
    }
    
    public void setIngress(List<SimpleIngressRule> ingress) {
        this.ingress = ingress;
    }
    
    public void addRoute(String hostname, String service) {
        SimpleIngressRule rule = new SimpleIngressRule();
        rule.setHostname(hostname);
        rule.setService(service);
        
        // Remove catch-all rule if present
        ingress.removeIf(r -> r.getHostname() == null);
        
        // Add new rule
        ingress.add(rule);
        
        // Add back catch-all rule
        SimpleIngressRule catchAll = new SimpleIngressRule();
        catchAll.setService("http_status:404");
        ingress.add(catchAll);
    }
    
    public void removeRoute(String hostname) {
        ingress.removeIf(rule -> hostname.equals(rule.getHostname()));
    }
    
    public static class SimpleIngressRule {
        @JsonProperty("hostname")
        private String hostname;
        
        @JsonProperty("service")
        private String service;
        
        @JsonProperty("path")
        private String path;
        
        public SimpleIngressRule() {}
        
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
    }
}