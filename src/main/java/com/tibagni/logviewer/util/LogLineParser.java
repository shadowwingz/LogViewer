package com.tibagni.logviewer.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing Android log lines to extract process ID and thread ID
 */
public class LogLineParser {
    
    // Android log format: MM-DD HH:mm:ss.SSS <pid> <tid> <level> <message>
    // Example: 04-02 13:16:34.662 12345 67890 I TagName: message content
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "^\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+(\\d+)\\s+(\\d+)\\s+[VDIWE]\\s+.*"
    );
    
    /**
     * Extract process ID from Android log line
     * @param logLine the log line to parse
     * @return process ID as string, or null if not found
     */
    public static String extractProcessId(String logLine) {
        if (logLine == null || logLine.trim().isEmpty()) {
            return null;
        }
        
        Matcher matcher = LOG_PATTERN.matcher(logLine);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * Extract thread ID from Android log line
     * @param logLine the log line to parse
     * @return thread ID as string, or null if not found
     */
    public static String extractThreadId(String logLine) {
        if (logLine == null || logLine.trim().isEmpty()) {
            return null;
        }
        
        Matcher matcher = LOG_PATTERN.matcher(logLine);
        if (matcher.find()) {
            return matcher.group(2);
        }
        
        return null;
    }
    
    /**
     * Check if a log line matches the Android log format
     * @param logLine the log line to check
     * @return true if the line matches Android log format
     */
    public static boolean isAndroidLogFormat(String logLine) {
        if (logLine == null || logLine.trim().isEmpty()) {
            return false;
        }
        
        return LOG_PATTERN.matcher(logLine).find();
    }
}
