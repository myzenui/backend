package com.armikom.zen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * DTO for Cloudflare tunnel configuration
 */
public class CloudflareTunnelConfiguration {
    
    @JsonProperty("ingress")
    private List<CloudflareTunnelRoute> ingress;
    
    @JsonProperty("warp-routing")
    private WarpRouting warpRouting;
    
    @JsonProperty("originRequest")
    private CloudflareTunnelRoute.OriginRequest originRequest;
    
    public CloudflareTunnelConfiguration() {
        this.ingress = new ArrayList<>();
        // Add default catch-all rule
        CloudflareTunnelRoute catchAll = new CloudflareTunnelRoute();
        catchAll.setService("http_status:404");
        this.ingress.add(catchAll);
    }
    
    public List<CloudflareTunnelRoute> getIngress() {
        return ingress;
    }
    
    public void setIngress(List<CloudflareTunnelRoute> ingress) {
        this.ingress = ingress;
    }
    
    public void addRoute(String hostname, String service) {
        CloudflareTunnelRoute route = new CloudflareTunnelRoute(hostname, service);
        // Insert before the catch-all rule (last item)
        if (!ingress.isEmpty()) {
            ingress.add(ingress.size() - 1, route);
        } else {
            ingress.add(route);
        }
    }
    
    public void removeRoute(String hostname) {
        ingress.removeIf(route -> hostname.equals(route.getHostname()));
    }
    
    public WarpRouting getWarpRouting() {
        return warpRouting;
    }
    
    public void setWarpRouting(WarpRouting warpRouting) {
        this.warpRouting = warpRouting;
    }
    
    public CloudflareTunnelRoute.OriginRequest getOriginRequest() {
        return originRequest;
    }
    
    public void setOriginRequest(CloudflareTunnelRoute.OriginRequest originRequest) {
        this.originRequest = originRequest;
    }
    
    public static class WarpRouting {
        @JsonProperty("enabled")
        private Boolean enabled = false;
        
        public WarpRouting() {}
        
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }
}