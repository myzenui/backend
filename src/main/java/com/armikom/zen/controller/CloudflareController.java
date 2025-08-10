package com.armikom.zen.controller;

import com.armikom.zen.dto.CloudflareRequest;
import com.armikom.zen.dto.CloudflareResponse;
import com.armikom.zen.dto.CloudflareDnsRecord;
import com.armikom.zen.dto.CloudflareTunnelConfiguration;
import com.armikom.zen.dto.TunnelRouteRequest;
import com.armikom.zen.service.CloudflareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cloudflare")
@Tag(name = "Cloudflare Controller", description = "Cloudflare tunnel and DNS management API endpoints")
public class CloudflareController {
    
    private static final Logger logger = LoggerFactory.getLogger(CloudflareController.class);
    
    private final CloudflareService cloudflareService;

    public CloudflareController(CloudflareService cloudflareService) {
        this.cloudflareService = cloudflareService;
    }
    
    @PostMapping("/create-tunnel-dns")
    @Operation(
        summary = "Create Tunnel DNS Record Only", 
        description = "Creates only a CNAME DNS record that points to the Cloudflare tunnel. You will need to configure tunnel routing separately using the tunnel configuration API or cloudflared CLI."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "DNS record created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Cloudflare API error or internal server error")
    })
    public ResponseEntity<CloudflareResponse> createTunnelDnsRecord(
            @Parameter(description = "Cloudflare tunnel DNS request")
            @Valid @RequestBody CloudflareRequest request) {
        
        try {
            logger.info("Creating tunnel DNS record for {}", request.getDnsName());
            
            CloudflareResponse response = cloudflareService.createTunnelDnsRecord(
                request.getDnsName(), 
                request.getPort(), 
                request.getProtocol(),
                request.getHost()
            );
            
            if (response.isSuccess()) {
                logger.info("Successfully created DNS record for {}", request.getDnsName());
                return ResponseEntity.ok(response);
            } else {
                logger.error("Failed to create DNS record: {}", response.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error creating tunnel DNS record", e);
            CloudflareResponse errorResponse = new CloudflareResponse(false, "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/create-dns-only")
    @Operation(
        summary = "Create DNS Record Only (Recommended for now)", 
        description = "Creates only a CNAME DNS record pointing to the tunnel. This is the most reliable option. Configure tunnel routing manually with cloudflared CLI."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "DNS record created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Cloudflare API error or internal server error")
    })
    public ResponseEntity<CloudflareResponse> createDnsOnly(
            @Parameter(description = "Tunnel route request")
            @Valid @RequestBody TunnelRouteRequest request) {
        
        try {
            logger.info("Creating DNS record only for {}", request.getHostname());
            
            CloudflareResponse response = cloudflareService.createTunnelDnsRecord(
                request.getHostname(), 
                request.getPort(), 
                request.getProtocol(),
                request.getHost()
            );
            
            if (response.isSuccess()) {
                logger.info("Successfully created DNS record for {}", request.getHostname());
                
                // Enhance the response with manual configuration instructions
                String manualInstructions = String.format(
                    " To complete the setup, configure your tunnel manually:\n" +
                    "1. Edit your tunnel config file (~/.cloudflared/config.yml)\n" +
                    "2. Add: \n" +
                    "   ingress:\n" +
                    "     - hostname: %s\n" +
                    "       service: %s://%s:%d\n" +
                    "     - service: http_status:404\n" +
                    "3. Restart tunnel: cloudflared tunnel run %s",
                    request.getHostname(), 
                    request.getProtocol(), 
                    request.getHost(),
                    request.getPort(),
                    cloudflareService.getTunnelInfo().split("\n")[0].replace("Tunnel ID: ", "")
                );
                
                response.setMessage(response.getMessage() + manualInstructions);
                return ResponseEntity.ok(response);
            } else {
                logger.error("Failed to create DNS record: {}", response.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error creating DNS record", e);
            CloudflareResponse errorResponse = new CloudflareResponse(false, "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    @DeleteMapping("/dns-record/{recordId}")
    @Operation(
        summary = "Delete DNS Record", 
        description = "Deletes a DNS record by its Cloudflare record ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "DNS record deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid record ID or deletion failed"),
        @ApiResponse(responseCode = "500", description = "Cloudflare API error or internal server error")
    })
    public ResponseEntity<CloudflareResponse> deleteDnsRecord(
            @Parameter(description = "Cloudflare DNS record ID", required = true)
            @PathVariable String recordId) {
        
        try {
            logger.info("Deleting DNS record with ID: {}", recordId);
            
            CloudflareResponse response = cloudflareService.deleteDnsRecord(recordId);
            
            if (response.isSuccess()) {
                logger.info("Successfully deleted DNS record with ID: {}", recordId);
                return ResponseEntity.ok(response);
            } else {
                logger.error("Failed to delete DNS record: {}", response.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error deleting DNS record", e);
            CloudflareResponse errorResponse = new CloudflareResponse(false, "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    @GetMapping("/dns-records")
    @Operation(
        summary = "List DNS Records", 
        description = "Lists all DNS records for the configured Cloudflare zone"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "DNS records retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Cloudflare API error or internal server error")
    })
    public ResponseEntity<List<CloudflareDnsRecord>> listDnsRecords() {
        
        try {
            logger.info("Listing DNS records");
            
            List<CloudflareDnsRecord> records = cloudflareService.listDnsRecords();
            logger.info("Successfully retrieved {} DNS records", records.size());
            
            return ResponseEntity.ok(records);
            
        } catch (Exception e) {
            logger.error("Unexpected error listing DNS records", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    @GetMapping("/tunnel-info")
    @Operation(
        summary = "Get Tunnel Information", 
        description = "Gets information about the configured tunnel and instructions for tunnel configuration"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tunnel information retrieved successfully")
    })
    public ResponseEntity<String> getTunnelInfo() {
        
        try {
            logger.info("Getting tunnel information");
            
            String tunnelInfo = cloudflareService.getTunnelInfo();
            
            return ResponseEntity.ok(tunnelInfo);
            
        } catch (Exception e) {
            logger.error("Unexpected error getting tunnel info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving tunnel info: " + e.getMessage());
        }
    }

    @GetMapping("/tunnel-configuration")
    @Operation(
        summary = "Get Tunnel Configuration", 
        description = "Gets the current tunnel configuration including all ingress rules"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tunnel configuration retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Cloudflare API error or internal server error")
    })
    public ResponseEntity<CloudflareTunnelConfiguration> getTunnelConfiguration() {
        
        try {
            logger.info("Getting tunnel configuration");
            
            CloudflareTunnelConfiguration config = cloudflareService.getTunnelConfiguration();
            
            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            logger.error("Unexpected error getting tunnel configuration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/tunnel-routes")
    @Operation(
        summary = "List Current Tunnel Routes", 
        description = "Lists all current tunnel ingress routes in a simple format"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tunnel routes retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Cloudflare API error or internal server error")
    })
    public ResponseEntity<java.util.List<String>> getTunnelRoutes() {
        
        try {
            logger.info("Getting current tunnel routes list");
            
            java.util.List<String> routes = cloudflareService.listTunnelRoutes();
            
            return ResponseEntity.ok(routes);
            
        } catch (Exception e) {
            logger.error("Unexpected error getting tunnel routes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Collections.emptyList());
        }
    }

    @PostMapping("/tunnel-route")
    @Operation(
        summary = "Add Tunnel Route (Experimental with Enhanced Logging)", 
        description = "Adds a new route to the tunnel configuration using simplified API structure. Now includes detailed logging for debugging."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tunnel route added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or route addition failed"),
        @ApiResponse(responseCode = "500", description = "Cloudflare API error or internal server error")
    })
    public ResponseEntity<CloudflareResponse> addTunnelRoute(
            @Parameter(description = "Tunnel route request")
            @Valid @RequestBody TunnelRouteRequest request) {
        
        try {
            logger.info("Adding tunnel route for {} with enhanced logging and simplified structure", request.getHostname());
            
            CloudflareResponse response = cloudflareService.addTunnelRoute(
                request.getHostname(), 
                request.getPort(), 
                request.getProtocol(),
                request.getPath(),
                request.getHost()
            );
            
            if (response.isSuccess()) {
                logger.info("Successfully added tunnel route for {}", request.getHostname());
                return ResponseEntity.ok(response);
            } else {
                logger.error("Failed to add tunnel route: {}", response.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error adding tunnel route", e);
            CloudflareResponse errorResponse = new CloudflareResponse(false, "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @DeleteMapping("/tunnel-route/{hostname}")
    @Operation(
        summary = "Remove Tunnel Route", 
        description = "Removes a route from the tunnel configuration"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tunnel route removed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid hostname or route removal failed"),
        @ApiResponse(responseCode = "500", description = "Cloudflare API error or internal server error")
    })
    public ResponseEntity<CloudflareResponse> removeTunnelRoute(
            @Parameter(description = "Hostname to remove from tunnel routes", required = true)
            @PathVariable String hostname) {
        
        try {
            logger.info("Removing tunnel route for hostname: {}", hostname);
            
            CloudflareResponse response = cloudflareService.removeTunnelRoute(hostname);
            
            if (response.isSuccess()) {
                logger.info("Successfully removed tunnel route for {}", hostname);
                return ResponseEntity.ok(response);
            } else {
                logger.error("Failed to remove tunnel route: {}", response.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error removing tunnel route", e);
            CloudflareResponse errorResponse = new CloudflareResponse(false, "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/complete-route")
    @Operation(
        summary = "Create Complete Route", 
        description = "Creates both DNS record and tunnel route configuration in one operation"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Complete route created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or route creation failed"),
        @ApiResponse(responseCode = "500", description = "Cloudflare API error or internal server error")
    })
    public ResponseEntity<CloudflareResponse> createCompleteRoute(
            @Parameter(description = "Complete route request (supports both CloudflareRequest and TunnelRouteRequest format)")
            @Valid @RequestBody TunnelRouteRequest request) {
        
        try {
            logger.info("Creating complete route for {}", request.getHostname());
            
            CloudflareResponse response = cloudflareService.createCompleteRoute(
                request.getHostname(), 
                request.getPort(), 
                request.getProtocol(),
                request.getPath(),
                request.getHost()
            );
            
            if (response.isSuccess()) {
                logger.info("Successfully created complete route for {}", request.getHostname());
                return ResponseEntity.ok(response);
            } else {
                logger.error("Failed to create complete route: {}", response.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error creating complete route", e);
            CloudflareResponse errorResponse = new CloudflareResponse(false, "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}