class AppUsageStat {
  final String packageName;
  final int totalTime; // milliseconds
  final List<DateTime> startTimes;
  final List<int> sessionDurations; // milliseconds per session

  AppUsageStat({
    required this.packageName,
    required this.totalTime,
    required this.startTimes,
    required this.sessionDurations,
  });

  int get totalMinutes => totalTime ~/ (1000 * 60);
  int get sessionCount => startTimes.length;

  /// Returns a map of hour (0–23) → usage in seconds for that hour.
  /// Each session's duration is attributed to the hour it started in.
  /// Sessions with no recorded duration (placeholder -1) are skipped.
  Map<int, int> getHourlyBreakdown() {
    final hourlyUsage = <int, int>{};
    for (int i = 0; i < 24; i++) {
      hourlyUsage[i] = 0;
    }

    for (int i = 0; i < startTimes.length; i++) {
      final hour = startTimes[i].hour;

      // Skip if no duration recorded for this session
      if (i >= sessionDurations.length) continue;
      final durationMs = sessionDurations[i];
      if (durationMs < 0)
        continue; // placeholder, session still open or unmatched

      final durationSec = (durationMs / 1000).round();
      hourlyUsage[hour] = hourlyUsage[hour]! + durationSec;
    }

    return hourlyUsage;
  }

  factory AppUsageStat.fromJson(Map<String, dynamic> json) => AppUsageStat(
    packageName: json['packageName'],
    totalTime: json['totalTime'],
    startTimes: (json['startTimes'] as List)
        .map((e) => DateTime.fromMillisecondsSinceEpoch(e as int))
        .toList(),
    // Backward compatible: defaults to empty list if not present
    sessionDurations: (json['sessionDurations'] as List? ?? [])
        .map((e) => (e as num).toInt())
        .toList(),
  );
}
