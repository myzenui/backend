package com.armikom.zen.service;

import com.armikom.zen.config.CloudflareConfig;
import com.armikom.zen.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

@Service
public class CloudflareService {

    private static final Logger logger = LoggerFactory.getLogger(CloudflareService.class);
    
    private final WebClient webClient;
    private final CloudflareConfig cloudflareConfig;
    private final ObjectMapper objectMapper;

    @Autowired
    public CloudflareService(WebClient cloudflareWebClient, CloudflareConfig cloudflareConfig, ObjectMapper objectMapper) {
        this.webClient = cloudflareWebClient;
        this.cloudflareConfig = cloudflareConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a CNAME DNS record pointing to the tunnel domain
     * and configures the tunnel to route traffic to the specified local port
     */
    public CloudflareResponse createTunnelDnsRecord(String dnsName, Integer port, String protocol) {
        try {
            logger.info("Creating DNS record for {} pointing to {}://localhost:{}", dnsName, protocol, port);
            
            // First, create the DNS record
            CloudflareDnsRecord dnsRecord = new CloudflareDnsRecord();
            dnsRecord.setName(dnsName);
            // Point to the tunnel domain - Cloudflare tunnels typically use format like tunnelid.cfargotunnel.com
            dnsRecord.setContent(cloudflareConfig.getTunnelId() + ".cfargotunnel.com");
            dnsRecord.setType("CNAME");
            dnsRecord.setProxied(true);
            
            String url = "/zones/" + cloudflareConfig.getZoneId() + "/dns_records";
            
            CloudflareApiResponse<CloudflareDnsRecord> response = webClient
                    .post()
                    .uri(url)
                    .body(BodyInserters.fromValue(dnsRecord))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CloudflareApiResponse<CloudflareDnsRecord>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                logger.info("Successfully created DNS record with ID: {}", response.getResult().getId());
                
                // Note: The actual tunnel configuration needs to be done via cloudflared CLI or tunnel API
                // For now, we'll return success assuming the tunnel is managed externally
                String targetUrl = protocol + "://localhost:" + port;
                
                return new CloudflareResponse(
                    true,
                    "DNS record created successfully. Please configure the tunnel to route " + dnsName + " to " + targetUrl,
                    dnsName,
                    response.getResult().getId(),
                    targetUrl
                );
            } else {
                String errorMessage = "Failed to create DNS record";
                if (response != null && response.getErrors() != null && !response.getErrors().isEmpty()) {
                    errorMessage = response.getErrors().get(0).getMessage();
                }
                logger.error("Failed to create DNS record: {}", errorMessage);
                return new CloudflareResponse(false, errorMessage);
            }
            
        } catch (WebClientResponseException e) {
            logger.error("Cloudflare API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new CloudflareResponse(false, "Cloudflare API error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating DNS record", e);
            return new CloudflareResponse(false, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Deletes a DNS record by its ID
     */
    public CloudflareResponse deleteDnsRecord(String recordId) {
        try {
            logger.info("Deleting DNS record with ID: {}", recordId);
            
            String url = "/zones/" + cloudflareConfig.getZoneId() + "/dns_records/" + recordId;
            
            CloudflareApiResponse<Object> response = webClient
                    .delete()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CloudflareApiResponse<Object>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                logger.info("Successfully deleted DNS record with ID: {}", recordId);
                return new CloudflareResponse(true, "DNS record deleted successfully");
            } else {
                String errorMessage = "Failed to delete DNS record";
                if (response != null && response.getErrors() != null && !response.getErrors().isEmpty()) {
                    errorMessage = response.getErrors().get(0).getMessage();
                }
                logger.error("Failed to delete DNS record: {}", errorMessage);
                return new CloudflareResponse(false, errorMessage);
            }
            
        } catch (WebClientResponseException e) {
            logger.error("Cloudflare API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new CloudflareResponse(false, "Cloudflare API error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error deleting DNS record", e);
            return new CloudflareResponse(false, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Lists all DNS records for the configured zone
     */
    public List<CloudflareDnsRecord> listDnsRecords() {
        try {
            logger.info("Listing DNS records for zone: {}", cloudflareConfig.getZoneId());
            
            String url = "/zones/" + cloudflareConfig.getZoneId() + "/dns_records";
            
            CloudflareApiResponse<List<CloudflareDnsRecord>> response = webClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CloudflareApiResponse<List<CloudflareDnsRecord>>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                logger.info("Successfully retrieved {} DNS records", response.getResult().size());
                return response.getResult();
            } else {
                String errorMessage = "Failed to list DNS records";
                if (response != null && response.getErrors() != null && !response.getErrors().isEmpty()) {
                    errorMessage = response.getErrors().get(0).getMessage();
                }
                logger.error("Failed to list DNS records: {}", errorMessage);
                throw new RuntimeException(errorMessage);
            }
            
        } catch (WebClientResponseException e) {
            logger.error("Cloudflare API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Cloudflare API error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error listing DNS records", e);
            throw new RuntimeException("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Gets tunnel information and instructions for configuration
     */
    public String getTunnelInfo() {
        return String.format(
            "Tunnel ID: %s\n" +
            "Zone ID: %s\n" +
            "Tunnel routes can be configured programmatically via this API or manually using cloudflared CLI:\n" +
            "cloudflared tunnel route dns %s <your-domain>\n" +
            "cloudflared tunnel ingress <your-domain> http://localhost:<port>",
            cloudflareConfig.getTunnelId(),
            cloudflareConfig.getZoneId(),
            cloudflareConfig.getTunnelId()
        );
    }

    /**
     * Gets the current tunnel configuration including all ingress rules
     */
    public CloudflareTunnelConfiguration getTunnelConfiguration() {
        try {
            logger.info("Getting tunnel configuration for tunnel: {}", cloudflareConfig.getTunnelId());
            
            String url = "/accounts/" + getAccountId() + "/cfd_tunnel/" + cloudflareConfig.getTunnelId() + "/configurations";
            
            CloudflareApiResponse<Object> response = webClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CloudflareApiResponse<Object>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                logger.info("Successfully retrieved tunnel configuration");
                
                // The response might be wrapped in a "config" object or be a direct configuration
                Object result = response.getResult();
                if (result instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> resultMap = (java.util.Map<String, Object>) result;
                    if (resultMap.containsKey("config")) {
                        // Configuration is wrapped
                        return objectMapper.convertValue(resultMap.get("config"), CloudflareTunnelConfiguration.class);
                    } else {
                        // Configuration is direct
                        return objectMapper.convertValue(result, CloudflareTunnelConfiguration.class);
                    }
                } else {
                    // If it's not expected to be retrieved, return a new empty configuration
                    logger.warn("No existing tunnel configuration found, creating new one");
                    return new CloudflareTunnelConfiguration();
                }
            } else {
                String errorMessage = "Failed to get tunnel configuration";
                if (response != null && response.getErrors() != null && !response.getErrors().isEmpty()) {
                    errorMessage = response.getErrors().get(0).getMessage();
                }
                logger.error("Failed to get tunnel configuration: {}", errorMessage);
                // Return empty configuration instead of throwing exception
                logger.warn("Returning empty tunnel configuration due to API error");
                return new CloudflareTunnelConfiguration();
            }
            
        } catch (WebClientResponseException e) {
            logger.error("Cloudflare API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            // Return empty configuration instead of throwing exception
            logger.warn("Returning empty tunnel configuration due to API error");
            return new CloudflareTunnelConfiguration();
        } catch (Exception e) {
            logger.error("Unexpected error getting tunnel configuration", e);
            // Return empty configuration instead of throwing exception
            logger.warn("Returning empty tunnel configuration due to unexpected error");
            return new CloudflareTunnelConfiguration();
        }
    }

    /**
     * Updates the tunnel configuration with new ingress rules
     */
    public CloudflareResponse updateTunnelConfiguration(CloudflareTunnelConfiguration configuration) {
        try {
            logger.info("Updating tunnel configuration for tunnel: {}", cloudflareConfig.getTunnelId());
            
            String url = "/accounts/" + getAccountId() + "/cfd_tunnel/" + cloudflareConfig.getTunnelId() + "/configurations";
            
            // Wrap the configuration in the expected format
            var configWrapper = new java.util.HashMap<String, Object>();
            configWrapper.put("config", configuration);
            
            // Log the payload being sent for debugging
            try {
                String payloadJson = objectMapper.writeValueAsString(configWrapper);
                logger.info("Sending tunnel configuration payload to URL: {}", url);
                logger.info("Payload: {}", payloadJson);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize payload for logging: {}", e.getMessage());
            }
            
            CloudflareApiResponse<Object> response = webClient
                    .put()
                    .uri(url)
                    .body(BodyInserters.fromValue(configWrapper))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CloudflareApiResponse<Object>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            // Log the response for debugging
            try {
                String responseJson = objectMapper.writeValueAsString(response);
                logger.info("Received response: {}", responseJson);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize response for logging: {}", e.getMessage());
            }

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                logger.info("Successfully updated tunnel configuration");
                return new CloudflareResponse(true, "Tunnel configuration updated successfully");
            } else {
                String errorMessage = "Failed to update tunnel configuration";
                if (response != null && response.getErrors() != null && !response.getErrors().isEmpty()) {
                    errorMessage = response.getErrors().get(0).getMessage();
                }
                logger.error("Failed to update tunnel configuration: {}", errorMessage);
                return new CloudflareResponse(false, errorMessage);
            }
            
        } catch (WebClientResponseException e) {
            logger.error("Cloudflare API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.error("Request URL was: {}", "/accounts/" + getAccountId() + "/cfd_tunnel/" + cloudflareConfig.getTunnelId() + "/configurations");
            return new CloudflareResponse(false, "Cloudflare API error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating tunnel configuration", e);
            return new CloudflareResponse(false, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Adds a new route to the tunnel configuration using simplified approach
     */
    public CloudflareResponse addTunnelRoute(String hostname, Integer port, String protocol, String path) {
        try {
            logger.info("Adding tunnel route: {} -> {}://localhost:{}", hostname, protocol, port);
            
            // Create a simplified tunnel configuration
            SimpleTunnelConfiguration config = new SimpleTunnelConfiguration();
            
            // Create the route
            String serviceUrl = protocol + "://localhost:" + port;
            SimpleTunnelConfiguration.SimpleIngressRule newRule = new SimpleTunnelConfiguration.SimpleIngressRule();
            newRule.setHostname(hostname);
            newRule.setService(serviceUrl);
            
            if (path != null && !path.isEmpty()) {
                newRule.setPath(path);
            }
            
            // Add the rule
            config.getIngress().add(newRule);
            
            // Add catch-all rule
            SimpleTunnelConfiguration.SimpleIngressRule catchAll = new SimpleTunnelConfiguration.SimpleIngressRule();
            catchAll.setService("http_status:404");
            config.getIngress().add(catchAll);
            
            // Update configuration using simplified approach
            return updateSimpleTunnelConfiguration(config);
            
        } catch (Exception e) {
            logger.error("Error adding tunnel route", e);
            return new CloudflareResponse(false, "Error adding tunnel route: " + e.getMessage());
        }
    }

    /**
     * Updates tunnel configuration using simplified structure
     */
    private CloudflareResponse updateSimpleTunnelConfiguration(SimpleTunnelConfiguration configuration) {
        try {
            logger.info("Updating tunnel configuration (simplified) for tunnel: {}", cloudflareConfig.getTunnelId());
            
            String url = "/accounts/" + getAccountId() + "/cfd_tunnel/" + cloudflareConfig.getTunnelId() + "/configurations";
            
            // Wrap the configuration in the expected format
            var configWrapper = new java.util.HashMap<String, Object>();
            configWrapper.put("config", configuration);
            
            // Log the payload being sent for debugging
            try {
                String payloadJson = objectMapper.writeValueAsString(configWrapper);
                logger.info("Sending simplified tunnel configuration payload to URL: {}", url);
                logger.info("Simplified Payload: {}", payloadJson);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize payload for logging: {}", e.getMessage());
            }
            
            CloudflareApiResponse<Object> response = webClient
                    .put()
                    .uri(url)
                    .body(BodyInserters.fromValue(configWrapper))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CloudflareApiResponse<Object>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            // Log the response for debugging
            try {
                String responseJson = objectMapper.writeValueAsString(response);
                logger.info("Received response: {}", responseJson);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize response for logging: {}", e.getMessage());
            }

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                logger.info("Successfully updated simplified tunnel configuration");
                return new CloudflareResponse(true, "Tunnel configuration updated successfully");
            } else {
                String errorMessage = "Failed to update tunnel configuration";
                if (response != null && response.getErrors() != null && !response.getErrors().isEmpty()) {
                    errorMessage = response.getErrors().get(0).getMessage();
                }
                logger.error("Failed to update tunnel configuration: {}", errorMessage);
                return new CloudflareResponse(false, errorMessage);
            }
            
        } catch (WebClientResponseException e) {
            logger.error("Cloudflare API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.error("Request URL was: {}", "/accounts/" + getAccountId() + "/cfd_tunnel/" + cloudflareConfig.getTunnelId() + "/configurations");
            return new CloudflareResponse(false, "Cloudflare API error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating tunnel configuration", e);
            return new CloudflareResponse(false, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Removes a route from the tunnel configuration
     */
    public CloudflareResponse removeTunnelRoute(String hostname) {
        try {
            logger.info("Removing tunnel route for hostname: {}", hostname);
            
            // Get current configuration
            CloudflareTunnelConfiguration config = getTunnelConfiguration();
            
            // Remove route
            config.removeRoute(hostname);
            
            // Update configuration
            return updateTunnelConfiguration(config);
            
        } catch (Exception e) {
            logger.error("Error removing tunnel route", e);
            return new CloudflareResponse(false, "Error removing tunnel route: " + e.getMessage());
        }
    }

    /**
     * Creates DNS record and configures tunnel route in one operation
     */
    public CloudflareResponse createCompleteRoute(String dnsName, Integer port, String protocol, String path) {
        try {
            logger.info("Creating complete route: {} -> {}://localhost:{}", dnsName, protocol, port);
            
            // First create DNS record
            CloudflareResponse dnsResponse = createTunnelDnsRecord(dnsName, port, protocol);
            if (!dnsResponse.isSuccess()) {
                return dnsResponse;
            }
            
            // Try to configure tunnel route (this is experimental)
            try {
                CloudflareResponse routeResponse = addTunnelRoute(dnsName, port, protocol, path);
                if (routeResponse.isSuccess()) {
                    return new CloudflareResponse(true, 
                        "Complete route created successfully. " + dnsName + " is now accessible and routed to " + protocol + "://localhost:" + port,
                        dnsName, 
                        dnsResponse.getRecordId(), 
                        protocol + "://localhost:" + port);
                } else {
                    logger.warn("DNS record created but tunnel route configuration failed for {}", dnsName);
                    return new CloudflareResponse(true, 
                        "DNS record created successfully. Tunnel route configuration failed: " + routeResponse.getMessage() + 
                        ". Please configure tunnel manually using: cloudflared tunnel ingress " + dnsName + " " + protocol + "://localhost:" + port,
                        dnsName, 
                        dnsResponse.getRecordId(), 
                        protocol + "://localhost:" + port);
                }
            } catch (Exception routeException) {
                logger.warn("Failed to configure tunnel route for {} due to exception: {}", dnsName, routeException.getMessage());
                return new CloudflareResponse(true, 
                    "DNS record created successfully. Tunnel route configuration is not supported or failed. " +
                    "Please configure tunnel manually using: cloudflared tunnel ingress " + dnsName + " " + protocol + "://localhost:" + port + 
                    ". Error: " + routeException.getMessage(),
                    dnsName, 
                    dnsResponse.getRecordId(), 
                    protocol + "://localhost:" + port);
            }
            
        } catch (Exception e) {
            logger.error("Error creating complete route", e);
            return new CloudflareResponse(false, "Error creating complete route: " + e.getMessage());
        }
    }

    /**
     * Gets the account ID from configuration
     */
    private String getAccountId() {
        return cloudflareConfig.getAccountId();
    }
}