package com.armikom.zen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Cloudflare DNS record operations
 */
public class CloudflareDnsRecord {
    
    @JsonProperty("type")
    private String type = "CNAME";
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("content")
    private String content;
    
    @JsonProperty("ttl")
    private Integer ttl = 1; // 1 means automatic
    
    @JsonProperty("proxied")
    private Boolean proxied = true;
    
    @JsonProperty("id")
    private String id;
    
    public CloudflareDnsRecord() {}
    
    public CloudflareDnsRecord(String name, String content) {
        this.name = name;
        this.content = content;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Integer getTtl() {
        return ttl;
    }
    
    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }
    
    public Boolean getProxied() {
        return proxied;
    }
    
    public void setProxied(Boolean proxied) {
        this.proxied = proxied;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
}