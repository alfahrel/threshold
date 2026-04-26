import 'package:flutter/services.dart';
import 'package:threshold/helper/home_widget_bridge.dart';
import 'package:threshold/model.dart';

class UsageStatsHelper {
  static const MethodChannel _channel = MethodChannel('usage_stats');

  static Future<bool> hasPermission() async {
    try {
      final bool result = await _channel.invokeMethod('hasPermission');
      return result;
    } catch (e) {
      return false;
    }
  }

  static Future<void> requestPermission() async {
    await _channel.invokeMethod('requestPermission');
  }

  static Future<List<String>> getIgnoredPackages() async {
    try {
      final packages = await HomeWidgetBridge.loadIgnoredPackages();
      return packages.toList();
    } catch (e) {
      print('Error getting ignored packages: $e');
      return [];
    }
  }

  static Future<void> setIgnoredPackages(List<String> packages) async {
    try {
      await HomeWidgetBridge.saveIgnoredPackages(packages.toSet());
    } catch (e) {
      print('Error setting ignored packages: $e');
    }
  }

  static Future<bool> hasAccessibilityPermission() async {
    try {
      final bool result = await _channel.invokeMethod(
        'hasAccessibilityPermission',
      );
      return result;
    } catch (e) {
      return false;
    }
  }

  static Future<void> requestAccessibilityPermission() async {
    await _channel.invokeMethod('requestAccessibilityPermission');
  }

  static Future<bool> hasOverlayPermission() async {
    try {
      final bool result = await _channel.invokeMethod('hasOverlayPermission');
      return result;
    } catch (e) {
      return false;
    }
  }

  static Future<void> requestOverlayPermission() async {
    await _channel.invokeMethod('requestOverlayPermission');
  }

  static Future<bool> hasDeviceAdminPermission() async {
    try {
      final bool result = await _channel.invokeMethod(
        'hasDeviceAdminPermission',
      );
      return result;
    } catch (e) {
      return false;
    }
  }

  static Future<void> requestDeviceAdminPermission() async {
    await _channel.invokeMethod('requestDeviceAdminPermission');
  }

  static Future<List<AppUsageStat>> getStatsByTimestamps(
    int start,
    int end,
  ) async {
    try {
      final List<dynamic> result = await _channel.invokeMethod(
        'getStatsByTimestamps',
        {'start': start, 'end': end},
      );
      return result
          .map((item) => AppUsageStat.fromJson(Map<String, dynamic>.from(item)))
          .toList();
    } catch (e) {
      return [];
    }
  }

  static Future<List<AppUsageStat>> getStatsByDate(DateTime date) async {
    final start = DateTime(
      date.year,
      date.month,
      date.day,
    ).millisecondsSinceEpoch;
    final end = DateTime(
      date.year,
      date.month,
      date.day,
      23,
      59,
      59,
      999,
    ).millisecondsSinceEpoch;
    return getStatsByTimestamps(start, end);
  }

  static Future<int?> getEarliestDataTimestamp() async {
    try {
      final int? result = await _channel.invokeMethod(
        'getEarliestDataTimestamp',
      );
      return result;
    } catch (e) {
      return null;
    }
  }

  static Future<Map<String, dynamic>?> getAppInfo(String packageName) async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'getAppInfo',
        {'packageName': packageName},
      );
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return null;
    }
  }

  static Future<void> setAppTimer(String packageName, int limitMinutes) async {
    try {
      await _channel.invokeMethod('setAppTimer', {
        'packageName': packageName,
        'limitMinutes': limitMinutes,
      });
    } catch (e) {
      print('Error setting app timer: $e');
    }
  }

  static Future<Map<String, int>> getAppTimers() async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'getAppTimers',
      );
      return result.map((key, value) => MapEntry(key.toString(), value as int));
    } catch (e) {
      print('Error getting app timers: $e');
      return {};
    }
  }

  static Future<void> removeAppTimer(String packageName) async {
    try {
      await _channel.invokeMethod('removeAppTimer', {
        'packageName': packageName,
      });
    } catch (e) {
      print('Error removing app timer: $e');
    }
  }

  static Future<int?> getAppUsageToday(String packageName) async {
    try {
      final int? result = await _channel.invokeMethod('getAppUsageToday', {
        'packageName': packageName,
      });
      return result;
    } catch (e) {
      print('Error getting app usage today: $e');
      return null;
    }
  }

  /// Returns a map of hour (0–23) → usage in seconds for today only.
  static Future<Map<int, int>> getAppHourlyBreakdownToday(
    String packageName,
  ) async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'getAppHourlyBreakdownToday',
        {'packageName': packageName},
      );
      return result.map(
        (key, value) =>
            MapEntry(int.parse(key.toString()), (value as num).toInt()),
      );
    } catch (e) {
      print('Error getting hourly breakdown: $e');
      return {};
    }
  }

  /// Returns total usage in milliseconds for each of the last 7 days.
  /// Map key is the start-of-day DateTime, value is total ms across all apps.
  static Future<Map<DateTime, int>> getWeeklyStats() async {
    final results = <DateTime, int>{};
    final now = DateTime.now();
    for (int i = 6; i >= 0; i--) {
      final date = DateTime(
        now.year,
        now.month,
        now.day,
      ).subtract(Duration(days: i));
      final stats = await getStatsByDate(date);
      final total = stats.fold<int>(0, (sum, s) => sum + s.totalTime);
      results[date] = total;
    }
    return results;
  }

  /// Returns average daily usage in ms across all days that have any data,
  /// from the earliest available timestamp up to today.
  static Future<int> getAverageDailyUsage() async {
    final now = DateTime.now();
    final earliestTs = await getEarliestDataTimestamp();
    if (earliestTs == null) return 0;

    final earliestDate = DateTime.fromMillisecondsSinceEpoch(earliestTs);
    int totalDays = 0;
    int totalMs = 0;

    DateTime cursor = DateTime(
      earliestDate.year,
      earliestDate.month,
      earliestDate.day,
    );
    final today = DateTime(now.year, now.month, now.day);

    while (!cursor.isAfter(today)) {
      final stats = await getStatsByDate(cursor);
      final dayTotal = stats.fold<int>(0, (sum, s) => sum + s.totalTime);
      if (dayTotal > 0) {
        totalMs += dayTotal;
        totalDays++;
      }
      cursor = cursor.add(const Duration(days: 1));
    }

    return totalDays > 0 ? totalMs ~/ totalDays : 0;
  }
}
