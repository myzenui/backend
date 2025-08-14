package com.armikom.zen.enums;

/**
 * Enum representing different database environments
 */
public enum DatabaseEnvironment {
    PREVIEW("preview", "Preview Database"),
    PRODUCTION("production", "Production Database");

    private final String value;
    private final String description;

    DatabaseEnvironment(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get DatabaseEnvironment from string value
     * @param value the string value
     * @return DatabaseEnvironment enum
     * @throws IllegalArgumentException if value is not valid
     */
    public static DatabaseEnvironment fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Database environment cannot be null");
        }
        
        for (DatabaseEnvironment env : values()) {
            if (env.value.equalsIgnoreCase(value)) {
                return env;
            }
        }
        
        throw new IllegalArgumentException("Invalid database environment: " + value + 
            ". Valid values are: preview, production");
    }

    @Override
    public String toString() {
        return value;
    }
}