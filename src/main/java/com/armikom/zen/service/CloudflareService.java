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
     * Creates a CNAME DNS record pointing to the tunnel domain (idempotent) - backward compatibility
     */
    public CloudflareResponse createTunnelDnsRecord(String dnsName, Integer port, String protocol) {
        return createTunnelDnsRecord(dnsName, port, protocol, "localhost");
    }

    /**
     * Creates a CNAME DNS record pointing to the tunnel domain (idempotent)
     * If the record already exists with same content, returns success
     * If the record exists with different content, updates it
     */
    public CloudflareResponse createTunnelDnsRecord(String dnsName, Integer port, String protocol, String host) {
        try {
            logger.info("Creating DNS record for {} pointing to {}://{}:{} (idempotent)", dnsName, protocol, host != null ? host : "localhost", port);
            
            // Define the expected content
            String expectedContent = cloudflareConfig.getTunnelId() + ".cfargotunnel.com";
            
            // First check if DNS record already exists
            CloudflareDnsRecord existingRecord = findExistingDnsRecord(dnsName);
            if (existingRecord != null) {
                // Check if the existing record has the correct content
                if (expectedContent.equals(existingRecord.getContent()) && 
                    "CNAME".equals(existingRecord.getType()) &&
                    Boolean.TRUE.equals(existingRecord.getProxied())) {
                    
                    logger.info("DNS record for {} already exists with correct configuration, returning success", dnsName);
                    String targetUrl = protocol + "://" + (host != null ? host : "localhost") + ":" + port;
                    return new CloudflareResponse(
                        true,
                        "DNS record already exists with correct configuration (idempotent operation). Record ID: " + existingRecord.getId(),
                        dnsName,
                        existingRecord.getId(),
                        targetUrl
                    );
                } else {
                    // Record exists but has wrong configuration - update it
                    logger.info("DNS record for {} exists but has incorrect configuration. Expected: {}, Actual: {}. Updating...", 
                        dnsName, expectedContent, existingRecord.getContent());
                    
                    CloudflareResponse updateResponse = updateDnsRecord(existingRecord.getId(), dnsName, expectedContent);
                    if (updateResponse.isSuccess()) {
                        String targetUrl = protocol + "://" + (host != null ? host : "localhost") + ":" + port;
                        return new CloudflareResponse(
                            true,
                            "DNS record updated to correct configuration (idempotent operation). Record ID: " + existingRecord.getId(),
                            dnsName,
                            existingRecord.getId(),
                            targetUrl
                        );
                    } else {
                        return updateResponse;
                    }
                }
            }
            
            // Create the DNS record
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
                logger.info("Successfully created new DNS record with ID: {}", response.getResult().getId());
                
                String targetUrl = protocol + "://" + (host != null ? host : "localhost") + ":" + port;
                
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
     * Finds an existing DNS record by name
     */
    private CloudflareDnsRecord findExistingDnsRecord(String dnsName) {
        try {
            List<CloudflareDnsRecord> records = listDnsRecords();
            for (CloudflareDnsRecord record : records) {
                if (dnsName.equals(record.getName())) {
                    logger.debug("Found existing DNS record: {} -> {} (Type: {}, Proxied: {})", 
                        record.getName(), record.getContent(), record.getType(), record.getProxied());
                    return record;
                }
            }
            return null;
        } catch (Exception e) {
            logger.warn("Error checking for existing DNS records: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Updates an existing DNS record with new content
     */
    private CloudflareResponse updateDnsRecord(String recordId, String dnsName, String newContent) {
        try {
            logger.info("Updating DNS record {} with new content: {}", recordId, newContent);
            
            CloudflareDnsRecord updateRecord = new CloudflareDnsRecord();
            updateRecord.setName(dnsName);
            updateRecord.setContent(newContent);
            updateRecord.setType("CNAME");
            updateRecord.setProxied(true);
            updateRecord.setTtl(1); // Auto TTL
            
            String url = "/zones/" + cloudflareConfig.getZoneId() + "/dns_records/" + recordId;
            
            CloudflareApiResponse<CloudflareDnsRecord> response = webClient
                    .put()
                    .uri(url)
                    .body(BodyInserters.fromValue(updateRecord))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CloudflareApiResponse<CloudflareDnsRecord>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                logger.info("Successfully updated DNS record {} to point to {}", recordId, newContent);
                return new CloudflareResponse(true, "DNS record updated successfully");
            } else {
                String errorMessage = "Failed to update DNS record";
                if (response != null && response.getErrors() != null && !response.getErrors().isEmpty()) {
                    errorMessage = response.getErrors().get(0).getMessage();
                }
                logger.error("Failed to update DNS record: {}", errorMessage);
                return new CloudflareResponse(false, errorMessage);
            }
            
        } catch (WebClientResponseException e) {
            logger.error("Cloudflare API error updating DNS record: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new CloudflareResponse(false, "Cloudflare API error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating DNS record", e);
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
     * Adds a new route to the tunnel configuration using simplified approach (idempotent) - backward compatibility
     */
    public CloudflareResponse addTunnelRoute(String hostname, Integer port, String protocol, String path) {
        return addTunnelRoute(hostname, port, protocol, path, "localhost");
    }

    /**
     * Adds a new route to the tunnel configuration using simplified approach (idempotent)
     * If the exact same route already exists, returns success without making changes
     */
    public CloudflareResponse addTunnelRoute(String hostname, Integer port, String protocol, String path, String host) {
        try {
            logger.info("Adding tunnel route: {} -> {}://{}:{} (idempotent)", hostname, protocol, host != null ? host : "localhost", port);
            
            // Get existing configuration first
            SimpleTunnelConfiguration config = getExistingSimpleTunnelConfiguration();
            
            // Create the target service URL
            String serviceUrl = protocol + "://" + (host != null ? host : "localhost") + ":" + port;
            String targetPath = (path != null && !path.isEmpty() && !"/".equals(path)) ? path : null;
            
            // Check if exact same route already exists
            SimpleTunnelConfiguration.SimpleIngressRule exactMatchRule = findExistingTunnelRoute(config, hostname, serviceUrl, targetPath);
            if (exactMatchRule != null) {
                logger.info("Tunnel route for {} -> {} already exists with exact configuration (idempotent operation)", hostname, serviceUrl);
                return new CloudflareResponse(true, 
                    "Tunnel route already exists with exact configuration (idempotent operation). Route: " + hostname + " -> " + serviceUrl);
            }
            
            // Check if hostname exists with different service/path - update it
            SimpleTunnelConfiguration.SimpleIngressRule existingHostnameRule = findExistingTunnelRouteByHostname(config, hostname);
            if (existingHostnameRule != null) {
                logger.info("Hostname {} exists with different configuration. " +
                    "Current: {} -> {} (path: {}). " +
                    "Updating to: {} -> {} (path: {})", 
                    hostname, 
                    existingHostnameRule.getHostname(), existingHostnameRule.getService(), existingHostnameRule.getPath(),
                    hostname, serviceUrl, targetPath);
            } else {
                logger.info("Adding new tunnel route: {} -> {} (path: {})", hostname, serviceUrl, targetPath);
            }
            
            // Create the new route
            SimpleTunnelConfiguration.SimpleIngressRule newRule = new SimpleTunnelConfiguration.SimpleIngressRule();
            newRule.setHostname(hostname);
            newRule.setService(serviceUrl);
            
            if (targetPath != null) {
                newRule.setPath(targetPath);
            }
            
            // Remove any existing route for this hostname to avoid duplicates
            config.getIngress().removeIf(rule -> hostname.equals(rule.getHostname()));
            
            // Remove catch-all rule temporarily
            SimpleTunnelConfiguration.SimpleIngressRule catchAllRule = null;
            for (int i = config.getIngress().size() - 1; i >= 0; i--) {
                SimpleTunnelConfiguration.SimpleIngressRule rule = config.getIngress().get(i);
                if (rule.getHostname() == null && "http_status:404".equals(rule.getService())) {
                    catchAllRule = config.getIngress().remove(i);
                    break;
                }
            }
            
            // Add the new rule
            config.getIngress().add(newRule);
            
            // Add back catch-all rule (or create new one if it didn't exist)
            if (catchAllRule == null) {
                catchAllRule = new SimpleTunnelConfiguration.SimpleIngressRule();
                catchAllRule.setService("http_status:404");
            }
            config.getIngress().add(catchAllRule);
            
            logger.info("Tunnel configuration now has {} ingress rules", config.getIngress().size());
            
            // Update configuration using simplified approach
            return updateSimpleTunnelConfiguration(config);
            
        } catch (Exception e) {
            logger.error("Error adding tunnel route", e);
            return new CloudflareResponse(false, "Error adding tunnel route: " + e.getMessage());
        }
    }

    /**
     * Finds an existing tunnel route that matches hostname, service, and path
     */
    private SimpleTunnelConfiguration.SimpleIngressRule findExistingTunnelRoute(
            SimpleTunnelConfiguration config, String hostname, String serviceUrl, String path) {
        
        for (SimpleTunnelConfiguration.SimpleIngressRule rule : config.getIngress()) {
            if (hostname.equals(rule.getHostname()) && 
                serviceUrl.equals(rule.getService())) {
                
                // Check path equality (both null or both equal)
                boolean pathMatches = (path == null && rule.getPath() == null) ||
                                    (path != null && path.equals(rule.getPath()));
                
                if (pathMatches) {
                    logger.debug("Found exact matching tunnel route: {} -> {} (path: {})", 
                        hostname, serviceUrl, path);
                    return rule;
                }
            }
        }
        return null;
    }

    /**
     * Finds any existing tunnel route for a hostname (regardless of service/path)
     */
    private SimpleTunnelConfiguration.SimpleIngressRule findExistingTunnelRouteByHostname(
            SimpleTunnelConfiguration config, String hostname) {
        
        for (SimpleTunnelConfiguration.SimpleIngressRule rule : config.getIngress()) {
            if (hostname.equals(rule.getHostname())) {
                logger.debug("Found existing tunnel route for hostname {}: {} -> {} (path: {})", 
                    hostname, rule.getHostname(), rule.getService(), rule.getPath());
                return rule;
            }
        }
        return null;
    }

    /**
     * Gets existing tunnel configuration and converts it to simplified format
     */
    private SimpleTunnelConfiguration getExistingSimpleTunnelConfiguration() {
        try {
            logger.info("Getting existing tunnel configuration for merging");
            
            String url = "/accounts/" + getAccountId() + "/cfd_tunnel/" + cloudflareConfig.getTunnelId() + "/configurations";
            
            CloudflareApiResponse<Object> response = webClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CloudflareApiResponse<Object>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                logger.info("Successfully retrieved existing tunnel configuration");
                
                Object result = response.getResult();
                if (result instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> resultMap = (java.util.Map<String, Object>) result;
                    
                    SimpleTunnelConfiguration config = new SimpleTunnelConfiguration();
                    
                    if (resultMap.containsKey("config")) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> configMap = (java.util.Map<String, Object>) resultMap.get("config");
                        
                        if (configMap.containsKey("ingress")) {
                            @SuppressWarnings("unchecked")
                            java.util.List<java.util.Map<String, Object>> ingressList = 
                                (java.util.List<java.util.Map<String, Object>>) configMap.get("ingress");
                            
                            for (java.util.Map<String, Object> ruleMap : ingressList) {
                                SimpleTunnelConfiguration.SimpleIngressRule rule = new SimpleTunnelConfiguration.SimpleIngressRule();
                                
                                if (ruleMap.containsKey("hostname")) {
                                    rule.setHostname((String) ruleMap.get("hostname"));
                                }
                                if (ruleMap.containsKey("service")) {
                                    rule.setService((String) ruleMap.get("service"));
                                }
                                if (ruleMap.containsKey("path")) {
                                    rule.setPath((String) ruleMap.get("path"));
                                }
                                
                                config.getIngress().add(rule);
                            }
                        }
                    }
                    
                    logger.info("Converted existing configuration with {} rules", config.getIngress().size());
                    return config;
                } else {
                    logger.warn("No existing tunnel configuration found, creating new one");
                    return new SimpleTunnelConfiguration();
                }
            } else {
                logger.warn("Failed to get existing tunnel configuration, creating new one");
                return new SimpleTunnelConfiguration();
            }
            
        } catch (WebClientResponseException e) {
            logger.warn("Cloudflare API error getting existing config: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.warn("Creating new empty configuration");
            return new SimpleTunnelConfiguration();
        } catch (Exception e) {
            logger.warn("Unexpected error getting existing tunnel configuration: {}", e.getMessage());
            logger.warn("Creating new empty configuration");
            return new SimpleTunnelConfiguration();
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
     * Creates DNS record and configures tunnel route in one operation (idempotent) - backward compatibility
     */
    public CloudflareResponse createCompleteRoute(String dnsName, Integer port, String protocol, String path) {
        return createCompleteRoute(dnsName, port, protocol, path, "localhost");
    }

    /**
     * Creates DNS record and configures tunnel route in one operation (idempotent)
     * If both DNS and tunnel route already exist, returns success without changes
     */
    public CloudflareResponse createCompleteRoute(String dnsName, Integer port, String protocol, String path, String host) {
        try {
            logger.info("Creating complete route: {} -> {}://{}:{} (idempotent)", dnsName, protocol, host != null ? host : "localhost", port);
            
            boolean dnsAlreadyExisted = false;
            boolean routeAlreadyExisted = false;
            
            // First create DNS record (idempotent)
            CloudflareResponse dnsResponse = createTunnelDnsRecord(dnsName, port, protocol, host);
            if (!dnsResponse.isSuccess()) {
                return dnsResponse;
            }
            
            // Check if DNS was already existing (idempotent operation)
            if (dnsResponse.getMessage() != null && dnsResponse.getMessage().contains("already exists")) {
                dnsAlreadyExisted = true;
                logger.info("DNS record for {} already existed", dnsName);
            }
            
                            // Try to configure tunnel route (idempotent)
                try {
                    CloudflareResponse routeResponse = addTunnelRoute(dnsName, port, protocol, path, host);
                if (routeResponse.isSuccess()) {
                    // Check if route was already existing (idempotent operation)
                    if (routeResponse.getMessage() != null && routeResponse.getMessage().contains("already exists")) {
                        routeAlreadyExisted = true;
                        logger.info("Tunnel route for {} already existed", dnsName);
                    }
                    
                    // Create appropriate message based on what existed
                    String message;
                    if (dnsAlreadyExisted && routeAlreadyExisted) {
                        message = "Complete route already exists (idempotent operation). Both DNS and tunnel route were already configured for " + dnsName;
                    } else if (dnsAlreadyExisted) {
                        message = "Complete route created successfully. DNS record already existed, tunnel route was " + 
                                (routeAlreadyExisted ? "already configured" : "newly created") + " for " + dnsName;
                    } else if (routeAlreadyExisted) {
                        message = "Complete route created successfully. DNS record was newly created, tunnel route already existed for " + dnsName;
                    } else {
                        message = "Complete route created successfully. " + dnsName + " is now accessible and routed to " + protocol + "://" + (host != null ? host : "localhost") + ":" + port;
                    }
                    
                    return new CloudflareResponse(true, 
                        message,
                        dnsName, 
                        dnsResponse.getRecordId(), 
                        protocol + "://" + (host != null ? host : "localhost") + ":" + port);
                } else {
                    logger.warn("DNS record {} but tunnel route configuration failed for {}", 
                        dnsAlreadyExisted ? "already existed" : "created", dnsName);
                    return new CloudflareResponse(true, 
                        "DNS record " + (dnsAlreadyExisted ? "already existed" : "created successfully") + 
                        ". Tunnel route configuration failed: " + routeResponse.getMessage() + 
                        ". Please configure tunnel manually using: cloudflared tunnel ingress " + dnsName + " " + protocol + "://" + (host != null ? host : "localhost") + ":" + port,
                        dnsName, 
                        dnsResponse.getRecordId(), 
                        protocol + "://" + (host != null ? host : "localhost") + ":" + port);
                }
            } catch (Exception routeException) {
                logger.warn("Failed to configure tunnel route for {} due to exception: {}", dnsName, routeException.getMessage());
                return new CloudflareResponse(true, 
                    "DNS record " + (dnsAlreadyExisted ? "already existed" : "created successfully") + 
                    ". Tunnel route configuration is not supported or failed. " +
                    "Please configure tunnel manually using: cloudflared tunnel ingress " + dnsName + " " + protocol + "://" + (host != null ? host : "localhost") + ":" + port + 
                    ". Error: " + routeException.getMessage(),
                    dnsName, 
                    dnsResponse.getRecordId(), 
                    protocol + "://" + (host != null ? host : "localhost") + ":" + port);
            }
            
        } catch (Exception e) {
            logger.error("Error creating complete route", e);
            return new CloudflareResponse(false, "Error creating complete route: " + e.getMessage());
        }
    }

    /**
     * Lists current tunnel routes in a simple format
     */
    public java.util.List<String> listTunnelRoutes() {
        try {
            SimpleTunnelConfiguration config = getExistingSimpleTunnelConfiguration();
            java.util.List<String> routes = new java.util.ArrayList<>();
            
            for (SimpleTunnelConfiguration.SimpleIngressRule rule : config.getIngress()) {
                if (rule.getHostname() != null) {
                    String routeInfo = rule.getHostname() + " -> " + rule.getService();
                    if (rule.getPath() != null && !rule.getPath().isEmpty()) {
                        routeInfo += " (path: " + rule.getPath() + ")";
                    }
                    routes.add(routeInfo);
                } else {
                    routes.add("Catch-all: " + rule.getService());
                }
            }
            
            logger.info("Found {} tunnel routes", routes.size());
            return routes;
            
        } catch (Exception e) {
            logger.error("Error listing tunnel routes", e);
            return java.util.Collections.singletonList("Error retrieving routes: " + e.getMessage());
        }
    }

    /**
     * Gets the account ID from configuration
     */
    private String getAccountId() {
        return cloudflareConfig.getAccountId();
    }
}