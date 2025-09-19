package com.armikom.zen.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of IJobLog that consolidates log messages for 1 second before flushing to Firestore.
 * This reduces the number of Firestore write operations and improves performance.
 */
public class JobLog implements IJobLog {
    
    private static final Logger logger = LoggerFactory.getLogger(JobLog.class);
    private static final String JOBS_COLLECTION = "jobs";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final Firestore firestore;
    private final String jobId;
    private final List<String> logBuffer;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object bufferLock = new Object();
    
    public JobLog(Firestore firestore, String jobId) {
        this.firestore = firestore;
        this.jobId = jobId;
        this.logBuffer = new ArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "JobLog-" + jobId);
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic flush every 1 second
        scheduler.scheduleWithFixedDelay(this::flushInternal, 1, 1, TimeUnit.SECONDS);
        
        logger.debug("JobLog initialized for job: {}", jobId);
    }
    
    @Override
    public void log(String message) {
        log("INFO", message);
    }
    
    @Override
    public void log(String level, String message) {
        if (closed.get()) {
            logger.warn("Attempted to log to closed JobLog for job: {}", jobId);
            return;
        }
        
        String timestampedMessage = String.format("[%s] [%s] %s", 
            LocalDateTime.now().format(TIMESTAMP_FORMAT), level, message);
        
        synchronized (bufferLock) {
            logBuffer.add(timestampedMessage);
        }
        
        logger.debug("Added log message to buffer for job {}: {}", jobId, timestampedMessage);
    }
    
    @Override
    public void flush() {
        flushInternal();
    }
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            logger.debug("Closing JobLog for job: {}", jobId);
            
            // Flush any remaining messages
            flushInternal();
            
            // Shutdown the scheduler
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.debug("JobLog closed for job: {}", jobId);
        }
    }
    
    /**
     * Internal method to flush buffered log messages to Firestore
     */
    private void flushInternal() {
        List<String> messagesToFlush;
        
        synchronized (bufferLock) {
            if (logBuffer.isEmpty()) {
                return;
            }
            
            messagesToFlush = new ArrayList<>(logBuffer);
            logBuffer.clear();
        }
        
        try {
            DocumentReference jobRef = firestore.collection(JOBS_COLLECTION).document(jobId);
            
            // Create a single consolidated log entry with all messages
            StringBuilder consolidatedLog = new StringBuilder();
            for (String message : messagesToFlush) {
                consolidatedLog.append(message).append("\n");
            }
            
            Map<String, Object> updates = new HashMap<>();
            // Append to existing logs using Firestore array union
            updates.put("logs", FieldValue.arrayUnion(consolidatedLog.toString().trim()));
            updates.put("updatedAt", FieldValue.serverTimestamp());
            
            jobRef.update(updates).get();
            
            logger.debug("Flushed {} log messages to Firestore for job: {}", messagesToFlush.size(), jobId);
            
        } catch (Exception e) {
            logger.error("Failed to flush log messages to Firestore for job: {}", jobId, e);
            
            // If flush fails, put messages back in buffer for retry (unless closed)
            if (!closed.get()) {
                synchronized (bufferLock) {
                    logBuffer.addAll(0, messagesToFlush);
                }
            }
        }
    }
}
