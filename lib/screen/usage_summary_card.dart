import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:threshold/model.dart';
import 'package:threshold/helper/time_tools.dart';
import 'package:threshold/screen/app_usage_breakdown.dart';

class TotalUsageCard extends StatelessWidget {
  final String totalTime;
  final int appCount;

  const TotalUsageCard({
    super.key,
    required this.totalTime,
    required this.appCount,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      color: theme.colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Total Screen Time',
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: theme.colorScheme.onPrimaryContainer,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    totalTime,
                    style: theme.textTheme.displayMedium?.copyWith(
                      color: theme.colorScheme.onPrimaryContainer,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '$appCount apps used',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onPrimaryContainer.withOpacity(
                        0.7,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class UsagePieChart extends StatefulWidget {
  final List<AppUsageStat> stats;
  final Map<String, int> appTimers;
  final Future<void> Function(String packageName, int? limit) onTimerSet;

  const UsagePieChart({
    super.key,
    required this.stats,
    required this.appTimers,
    required this.onTimerSet,
  });

  @override
  State<UsagePieChart> createState() => _UsagePieChartState();
}

class _UsagePieChartState extends State<UsagePieChart> {
  int _touchedIndex = -1;

  static const _colors = [Colors.blue, Colors.red, Colors.green, Colors.purple];

  @override
  Widget build(BuildContext context) {
    if (widget.stats.isEmpty) return const SizedBox();

    final theme = Theme.of(context);
    final topApps = widget.stats.take(3).toList();
    final othersTime = widget.stats
        .skip(3)
        .fold<int>(0, (sum, stat) => sum + stat.totalTime);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Usage Distribution',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Tap a section to view details',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 40),
            SizedBox(
              height: 200,
              child: PieChart(
                PieChartData(
                  sections: _buildSections(topApps, othersTime),
                  centerSpaceRadius: 50,
                  sectionsSpace: 2,
                  startDegreeOffset: -90,
                  pieTouchData: PieTouchData(
                    touchCallback: (FlTouchEvent event, pieTouchResponse) {
                      setState(() {
                        if (!event.isInterestedForInteractions ||
                            pieTouchResponse == null ||
                            pieTouchResponse.touchedSection == null) {
                          _touchedIndex = -1;
                          return;
                        }
                        _touchedIndex = pieTouchResponse
                            .touchedSection!
                            .touchedSectionIndex;
                      });

                      if (event is FlTapUpEvent && _touchedIndex >= 0) {
                        if (_touchedIndex < topApps.length) {
                          _navigateToBreakdown(context, topApps[_touchedIndex]);
                        }
                      }
                    },
                  ),
                ),
                swapAnimationDuration: const Duration(milliseconds: 300),
                swapAnimationCurve: Curves.easeOutCubic,
              ),
            ),
            const SizedBox(height: 40),
            _buildLegend(context, topApps, othersTime),
          ],
        ),
      ),
    );
  }

  List<PieChartSectionData> _buildSections(
    List<AppUsageStat> topApps,
    int othersTime,
  ) {
    final sections = <PieChartSectionData>[];

    for (int i = 0; i < topApps.length; i++) {
      final stat = topApps[i];
      final isTouched = i == _touchedIndex;

      sections.add(
        PieChartSectionData(
          value: stat.totalTime.toDouble(),
          title: isTouched ? TimeTools.formatTime(stat.totalTime) : '',
          titleStyle: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.bold,
            color: Colors.white,
            shadows: [
              Shadow(color: Colors.black.withOpacity(0.5), blurRadius: 4),
            ],
          ),
          color: _colors[i],
          radius: isTouched ? 85.0 : 70.0,
        ),
      );
    }

    if (othersTime > 0) {
      final isTouched = topApps.length == _touchedIndex;
      sections.add(
        PieChartSectionData(
          value: othersTime.toDouble(),
          title: isTouched ? TimeTools.formatTime(othersTime) : '',
          titleStyle: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.bold,
            color: Colors.white,
            shadows: [
              Shadow(color: Colors.black.withOpacity(0.5), blurRadius: 4),
            ],
          ),
          color: _colors[3],
          radius: isTouched ? 85.0 : 70.0,
        ),
      );
    }

    return sections;
  }

  Widget _buildLegend(
    BuildContext context,
    List<AppUsageStat> topApps,
    int othersTime,
  ) {
    return Wrap(
      spacing: 12,
      runSpacing: 8,
      children: [
        ...topApps.asMap().entries.map((entry) {
          return InkWell(
            onTap: () => _navigateToBreakdown(context, topApps[entry.key]),
            borderRadius: BorderRadius.circular(8),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              child: _LegendItem(
                color: _colors[entry.key],
                label: entry.value.packageName.split('.').last,
                time: TimeTools.formatTime(entry.value.totalTime),
              ),
            ),
          );
        }),
        if (othersTime > 0)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            child: _LegendItem(
              color: _colors[3],
              label: 'Others',
              time: TimeTools.formatTime(othersTime),
            ),
          ),
      ],
    );
  }

  void _navigateToBreakdown(BuildContext context, AppUsageStat stat) {
    final hasTimer = widget.appTimers.containsKey(stat.packageName);
    DateTime _selectedDate = DateTime.now();
    final timerLimit = widget.appTimers[stat.packageName];

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => AppUsageBreakdownScreen(
          stat: stat,
          hasTimer: hasTimer,
          selectedDate: _selectedDate,
          timerLimit: timerLimit,
          onTimerSet: (limit) => widget.onTimerSet(stat.packageName, limit),
        ),
      ),
    );
  }
}

class _LegendItem extends StatelessWidget {
  final Color color;
  final String label;
  final String? time;

  const _LegendItem({required this.color, required this.label, this.time});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 12,
          height: 12,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(3),
          ),
        ),
        const SizedBox(width: 6),
        Text(
          label,
          style: Theme.of(
            context,
          ).textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w600),
        ),
        if (time != null) ...[
          const SizedBox(width: 4),
          Text(
            '($time)',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ],
    );
  }
}
