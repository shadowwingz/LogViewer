package com.tibagni.logviewer.log;

import com.tibagni.logviewer.filter.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class LogEntry implements Comparable<LogEntry> {

  private int index;
  public final LogTimestamp timestamp;
  public final String logText;
  public final LogLevel logLevel;
  public final LogStream logStream;
  public final String fileName;

  private Filter appliedFilter;
  @Nullable
  private Filter searchFilter;

  public LogEntry(String logText, LogLevel logLevel, LogTimestamp timestamp) {
    this(logText, logLevel, timestamp, "", "");
  }

  public LogEntry(String logText, LogLevel logLevel, LogTimestamp timestamp, String logName) {
    this(logText, logLevel, timestamp, logName, logName);
  }

  public LogEntry(String logText, LogLevel logLevel, LogTimestamp timestamp, String logName, String fileName) {
    this.logText = logText;
    this.logLevel = logLevel;
    this.timestamp = timestamp;
    this.logStream = LogStream.inferLogStreamFromName(logName);
    this.fileName = fileName;
  }

  public String getLogText() {
    return logText;
  }

  public LogLevel getLogLevel() {
    return logLevel;
  }

  public Filter getAppliedFilter() {
    return appliedFilter;
  }

  public void setAppliedFilter(Filter appliedFilter) {
    this.appliedFilter = appliedFilter;
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public LogStream getStream() {
    return logStream;
  }

  public String getFileName() {
    return fileName;
  }

  public int getLength() {
    return logText.length();
  }

  @Nullable
  public Filter getSearchFilter() {
    return searchFilter;
  }

  public LogEntry setSearchFilter(@Nullable Filter searchFilter) {
    this.searchFilter = searchFilter;
    return this;
  }

  @Override
  public String toString() {
    return getLogText();
  }

  @Override
  public int compareTo(@NotNull LogEntry o) {
    if (timestamp == null && o.timestamp == null) return 0;
    if (timestamp == null) return -1;
    if (o.timestamp == null) return 1;

    int time = timestamp.compareTo(o.timestamp);
    // compare index if same time
    return time == 0 ? Integer.compare(index, o.index) : time;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LogEntry logEntry = (LogEntry) o;
    return index == logEntry.index &&
        Objects.equals(timestamp, logEntry.timestamp) &&
        Objects.equals(logText, logEntry.logText) &&
        logLevel == logEntry.logLevel &&
        logStream == logEntry.logStream &&
        Objects.equals(fileName, logEntry.fileName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, timestamp, logText, logLevel, logStream, fileName);
  }
}
