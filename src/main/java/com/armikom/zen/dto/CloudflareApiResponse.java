package com.armikom.zen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for Cloudflare API response structure
 */
public class CloudflareApiResponse<T> {
    
    @JsonProperty("success")
    private Boolean success;
    
    @JsonProperty("errors")
    private List<CloudflareError> errors;
    
    @JsonProperty("messages")
    private List<String> messages;
    
    @JsonProperty("result")
    private T result;
    
    public CloudflareApiResponse() {}
    
    public Boolean getSuccess() {
        return success;
    }
    
    public void setSuccess(Boolean success) {
        this.success = success;
    }
    
    public List<CloudflareError> getErrors() {
        return errors;
    }
    
    public void setErrors(List<CloudflareError> errors) {
        this.errors = errors;
    }
    
    public List<String> getMessages() {
        return messages;
    }
    
    public void setMessages(List<String> messages) {
        this.messages = messages;
    }
    
    public T getResult() {
        return result;
    }
    
    public void setResult(T result) {
        this.result = result;
    }
    
    public static class CloudflareError {
        @JsonProperty("code")
        private Integer code;
        
        @JsonProperty("message")
        private String message;
        
        public CloudflareError() {}
        
        public Integer getCode() {
            return code;
        }
        
        public void setCode(Integer code) {
            this.code = code;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}