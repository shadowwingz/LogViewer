package com.tibagni.logviewer.filter;

import com.tibagni.logviewer.log.LogEntry;
import com.tibagni.logviewer.log.LogLevel;
import com.tibagni.logviewer.log.LogStream;
import com.tibagni.logviewer.util.StringUtils;

import java.awt.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Filter {
  public static final String FILE_EXTENSION = "filter";

  private boolean applied;
  private String name;
  private Color color;
  private LogLevel verbosity = LogLevel.VERBOSE;
  private Pattern pattern;
  private int flags = Pattern.CASE_INSENSITIVE;
  private ContextInfo temporaryInfo;
  private boolean isSimpleFilter;
  private boolean isMultiKeywordFilter;
  private String[] keywords;

  public boolean wasLoadedFromLegacyFile = false;

  // We intentionally don't copy the temporary info as it is temporary
  // We intentionally don't copy 'wasLoadedFromLegacyFile' as the copied filter would not have been loaded from a file
  @SuppressWarnings("CopyConstructorMissesField")
  public Filter(Filter from) throws FilterException {
    name = from.name;
    color = new Color(from.color.getRGB());
    flags = from.flags;
    applied = from.isApplied();
    pattern = getPattern(from.pattern.pattern());
    verbosity = from.verbosity;
    isSimpleFilter = from.isSimpleFilter;
    isMultiKeywordFilter = from.isMultiKeywordFilter;
    keywords = from.keywords != null ? from.keywords.clone() : null;
  }

  public Filter(String name, String pattern, Color color, LogLevel verbosity) throws FilterException {
    this(name, pattern, color, verbosity, false);
  }

  public Filter(String name, String pattern, Color color, LogLevel verbosity, boolean caseSensitive)
      throws FilterException {
    updateFilter(name, pattern, color, verbosity, caseSensitive);
  }

  public Filter(String name, String[] keywords, Color color, LogLevel verbosity, boolean caseSensitive)
      throws FilterException {
    updateMultiKeywordFilter(name, keywords, color, verbosity, caseSensitive);
  }

  boolean nameIsPattern() {
    return StringUtils.areEquals(getName(), getPatternString());
  }

  public void updateFilter(String name, String pattern, Color color, LogLevel verbosity, boolean caseSensitive)
      throws FilterException {

    if (StringUtils.isEmpty(name) || StringUtils.isEmpty(pattern) || color == null) {
      throw new FilterException("You must provide a name, a regex pattern and a color for the filter");
    }

    if (caseSensitive) {
      flags &= ~Pattern.CASE_INSENSITIVE;
    } else {
      flags |= Pattern.CASE_INSENSITIVE;
    }

    this.name = name;
    this.color = color;
    this.pattern = getPattern(pattern);
    this.verbosity = verbosity;
    this.isSimpleFilter = !StringUtils.isPotentialRegex(pattern);
    this.isMultiKeywordFilter = false;
    this.keywords = null;
  }

  public void updateMultiKeywordFilter(String name, String[] keywords, Color color, LogLevel verbosity, boolean caseSensitive)
      throws FilterException {

    if (StringUtils.isEmpty(name) || keywords == null || keywords.length == 0 || color == null) {
      throw new FilterException("You must provide a name, keywords and a color for the filter");
    }

    // Check all keywords are not empty
    for (String keyword : keywords) {
      if (StringUtils.isEmpty(keyword)) {
        throw new FilterException("All keywords must be non-empty");
      }
    }

    if (caseSensitive) {
      flags &= ~Pattern.CASE_INSENSITIVE;
    } else {
      flags |= Pattern.CASE_INSENSITIVE;
    }

    this.name = name;
    this.color = color;
    this.verbosity = verbosity;
    this.isMultiKeywordFilter = true;
    this.isSimpleFilter = false;
    this.keywords = keywords.clone();
    
    // Generate regex pattern for multi-keyword search
    StringBuilder patternBuilder = new StringBuilder();
    for (int i = 0; i < keywords.length; i++) {
      if (i > 0) {
        patternBuilder.append(".*");
      }
      patternBuilder.append(Pattern.quote(keywords[i]));
    }
    this.pattern = getPattern(patternBuilder.toString());
  }

  public static Filter createFromString(String filterString) throws FilterException {
    // See format in 'serializeFilter'
    try {
      String[] params = filterString.split(",");
      if (params.length < 4) {
        throw new IllegalArgumentException();
      }

      String[] rgb = params[3].split(":");
      if (rgb.length != 3) {
        throw new IllegalArgumentException("Wrong color format");
      }

      boolean isLegacy = params.length == 4;
      // For multi-keyword format we serialize as:
      // name,base64(keywords joined by ';'),flags,R:G:B,verbosity,MULTI
      // Which results in params length == 6 and the type marker at index 5
      boolean isMultiKeyword = params.length >= 6 && "MULTI".equals(params[5]);

      String name = params[0];
      String patternData = StringUtils.decodeBase64(params[1]);
      Color color = new Color(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
      LogLevel verbosity = isLegacy ? LogLevel.VERBOSE : LogLevel.valueOf(params[4]);
      int flags = Integer.parseInt(params[2]);
      boolean isCaseSensitive = (flags & Pattern.CASE_INSENSITIVE) == 0;

      Filter filter;
      if (isMultiKeyword) {
        String[] keywords = patternData.split(";");
        filter = new Filter(name, keywords, color, verbosity, isCaseSensitive);
      } else {
        filter = new Filter(name, patternData, color, verbosity, isCaseSensitive);
      }
      
      filter.wasLoadedFromLegacyFile = isLegacy;
      return filter;
    } catch (Exception e) {
      throw new FilterException("Wrong filter format: " + filterString, e);
    }
  }

  public boolean isApplied() {
    return applied;
  }

  public void setApplied(boolean applied) {
    this.applied = applied;
  }

  public String getName() {
    return name;
  }

  public LogLevel getVerbosity() {
    return verbosity;
  }

  public Color getColor() {
    return color;
  }

  public String getPatternString() {
    return pattern.toString();
  }

  public ContextInfo getTemporaryInfo() {
    return temporaryInfo;
  }

  public void resetTemporaryInfo() {
    this.temporaryInfo = null;
  }

  void initTemporaryInfo() {
    temporaryInfo = new ContextInfo();
  }

  public boolean isCaseSensitive() {
    // Check if the CASE_INSENSITIVE is OFF!!
    return (flags & Pattern.CASE_INSENSITIVE) == 0;
  }

  public boolean isMultiKeywordFilter() {
    return isMultiKeywordFilter;
  }

  public String[] getKeywords() {
    return keywords != null ? keywords.clone() : null;
  }

  /**
   * Take a single String and return whether it appliesTo this filter or not
   *
   * @param entry A single log line entry
   * @return true if this filter is applicable to the input line. False otherwise
   */
  public boolean appliesTo(LogEntry entry) {
    String inputLine = entry.getLogText();
    boolean foundPattern;
    
    if (isMultiKeywordFilter) {
      foundPattern = multiKeywordMatch(inputLine);
    } else if (isSimpleFilter) {
      foundPattern = simpleMatch(inputLine);
    } else {
      foundPattern = regexMatch(inputLine);
    }
    
    boolean isVerbosityAllowed = verbosity.ordinal() <= entry.logLevel.ordinal();

    return foundPattern && isVerbosityAllowed;
  }

  private boolean simpleMatch(String inputLine) {
    if (isCaseSensitive()) {
      return inputLine.contains(getPatternString());
    }
    return inputLine.toLowerCase().contains(getPatternString().toLowerCase());
  }

  private boolean regexMatch(String inputLine) {
    return pattern.matcher(inputLine).find();
  }

  private boolean multiKeywordMatch(String inputLine) {
    if (keywords == null || keywords.length == 0) {
      return false;
    }
    
    String searchText = isCaseSensitive() ? inputLine : inputLine.toLowerCase();
    
    for (String keyword : keywords) {
      String searchKeyword = isCaseSensitive() ? keyword : keyword.toLowerCase();
      if (!searchText.contains(searchKeyword)) {
        return false;
      }
    }
    
    return true;
  }

  private Pattern getPattern(String pattern) throws FilterException {
    try {
      return Pattern.compile(pattern, flags);
    } catch (PatternSyntaxException e) {
      throw new FilterException("Invalid pattern: " + pattern, e);
    }
  }

  @Override
  public String toString() {
    return String.format("Filter: [Name=%s, pattern=%s, regexFlags=%d, color=%s, verbosity=%s, applied=%b]",
        name, pattern, flags, color, verbosity, applied);
  }

  public String serializeFilter() {
    String patternData;
    if (isMultiKeywordFilter && keywords != null) {
      // Serialize keywords array, separated by semicolons
      patternData = StringUtils.encodeBase64(String.join(";", keywords));
      // For multi-keyword filters, include the type identifier
      return String.format("%s,%s,%d,%d:%d:%d,%s,%s",
          name.replaceAll(",", " "),
          patternData,
          flags,
          color.getRed(),
          color.getGreen(),
          color.getBlue(),
          verbosity,
          "MULTI");
    } else {
      // For regular regex filters, use the old format for backward compatibility
      patternData = StringUtils.encodeBase64(getPatternString());
      return String.format("%s,%s,%d,%d:%d:%d,%s",
          name.replaceAll(",", " "),
          patternData,
          flags,
          color.getRed(),
          color.getGreen(),
          color.getBlue(),
          verbosity);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Filter filter = (Filter) o;
    return flags == filter.flags &&
        Objects.equals(name, filter.name) &&
        Objects.equals(color, filter.color) &&
        Objects.equals(getPatternString(), filter.getPatternString()) &&
        Objects.equals(temporaryInfo, filter.temporaryInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, color, pattern, flags, temporaryInfo);
  }

  public static class ContextInfo {
    private final Map<LogStream, Integer> linesFound;
    private Set<LogStream> allowedStreams;

    private ContextInfo() {
      linesFound = new HashMap<>();
    }
    public void setAllowedStreams(Set<LogStream> allowedStreams) {
      this.allowedStreams = allowedStreams;
    }

    public int getTotalLinesFound() {
      int totalLinesFound = 0;
      for (Map.Entry<LogStream, Integer> entry : linesFound.entrySet()) {
        if (allowedStreams == null || allowedStreams.contains(entry.getKey())) {
          totalLinesFound += entry.getValue();
        }
      }

      return totalLinesFound;
    }

    // This method must be synchronized as the filters can be applied in parallel
    public synchronized void incrementLineCount(LogStream stream) {
      int currentCount = 0;
      if (linesFound.containsKey(stream)) {
        currentCount = linesFound.get(stream);
      }

      linesFound.put(stream, currentCount + 1);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ContextInfo that = (ContextInfo) o;
      return Objects.equals(linesFound, that.linesFound) && Objects.equals(allowedStreams, that.allowedStreams);
    }

    @Override
    public int hashCode() {
      return Objects.hash(linesFound, allowedStreams);
    }
  }
}