import 'dart:convert';
import 'package:home_widget/home_widget.dart';

class HomeWidgetBridge {
  static const _ignoredKey = 'ignored_packages';
  static const _androidWidgetName = 'UsageStatsWidget';
  static const _qualifiedAndroidName =
      'com.alfahrel.threshold.UsageStatsWidget';

  static Future<void> saveIgnoredPackages(Set<String> packages) async {
    final encoded = jsonEncode(packages.toList());
    await HomeWidget.saveWidgetData<String>(_ignoredKey, encoded);
    await HomeWidget.updateWidget(
      androidName: _androidWidgetName,
      qualifiedAndroidName: _qualifiedAndroidName,
    );
  }

  static Future<Set<String>> loadIgnoredPackages() async {
    try {
      final raw = await HomeWidget.getWidgetData<String>(_ignoredKey);
      if (raw == null) return {};
      final list = jsonDecode(raw) as List<dynamic>;
      return list.cast<String>().toSet();
    } catch (e) {
      return {};
    }
  }
}