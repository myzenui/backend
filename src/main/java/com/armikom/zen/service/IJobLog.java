package com.armikom.zen.service;

/**
 * Interface for logging job progress to Firestore.
 * Implementations should consolidate logs for a period of time before flushing to reduce Firestore read/write operations.
 */
public interface IJobLog {
    
    /**
     * Adds a log message to the buffer. Messages are consolidated and flushed periodically.
     * @param message The log message to add
     */
    void log(String message);
    
    /**
     * Adds a log message with a specific log level to the buffer.
     * @param level The log level (INFO, WARN, ERROR, etc.)
     * @param message The log message to add
     */
    void log(String level, String message);
    
    /**
     * Forces immediate flush of all buffered log messages to Firestore.
     * This method should be called when the job is completed or when immediate logging is required.
     */
    void flush();
    
    /**
     * Closes the logger and ensures all buffered messages are flushed.
     * This method should be called when the job is finished.
     */
    void close();
}
