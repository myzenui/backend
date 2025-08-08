package com.armikom.zen.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class CloudflareConfig {

    @Value("${cloudflare.api.token}")
    private String apiToken;

    @Value("${cloudflare.zone.id}")
    private String zoneId;

    @Value("${cloudflare.account.id}")
    private String accountId;

    @Value("${cloudflare.tunnel.id:7a1dbacd-f34f-4662-81bc-6ee718f898e7}")
    private String tunnelId;

    @Value("${cloudflare.api.base-url:https://api.cloudflare.com/client/v4}")
    private String baseUrl;

    @Bean
    public WebClient cloudflareWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getTunnelId() {
        return tunnelId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}