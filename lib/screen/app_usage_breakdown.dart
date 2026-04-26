import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:threshold/helper/app_info_cache.dart';
import 'package:threshold/helper/time_tools.dart';
import 'package:threshold/model.dart';
import 'package:threshold/helper/usage_stats.dart';

class AppUsageBreakdownScreen extends StatefulWidget {
  final AppUsageStat stat;
  final bool hasTimer;
  final int? timerLimit;
  final Function(int?) onTimerSet;
  final DateTime selectedDate;

  const AppUsageBreakdownScreen({
    Key? key,
    required this.stat,
    this.hasTimer = false,
    this.timerLimit,
    required this.onTimerSet,
    required this.selectedDate,
  }) : super(key: key);

  @override
  State<AppUsageBreakdownScreen> createState() =>
      _AppUsageBreakdownScreenState();
}

class _AppUsageBreakdownScreenState extends State<AppUsageBreakdownScreen> {
  Map<String, dynamic>? _appInfo;
  int? _usageToday;
  bool _isLoadingAppInfo = true;

  Map<int, int> _hourlyBreakdown = {};
  bool _isLoadingHourly = true;

  @override
  void initState() {
    super.initState();
    _loadAppInfo();
    _loadUsageToday();
    _loadHourlyBreakdown();
  }

  Future<void> _loadAppInfo() async {
    final info = await AppInfoCache.getAppInfo(widget.stat.packageName);
    if (mounted) {
      setState(() {
        _appInfo = info;
        _isLoadingAppInfo = false;
      });
    }
  }

  Future<void> _loadUsageToday() async {
    final usage = await UsageStatsHelper.getAppUsageToday(
      widget.stat.packageName,
    );
    if (mounted) {
      setState(() => _usageToday = usage);
    }
  }

  Future<void> _loadHourlyBreakdown() async {
    final today = DateTime.now();
    final isToday =
        widget.selectedDate.year == today.year &&
        widget.selectedDate.month == today.month &&
        widget.selectedDate.day == today.day;

    if (isToday) {
      // Use accurate native event query for today
      final breakdown = await UsageStatsHelper.getAppHourlyBreakdownToday(
        widget.stat.packageName,
      );
      if (mounted) {
        setState(() {
          _hourlyBreakdown = breakdown;
          _isLoadingHourly = false;
        });
      }
    } else {
      // Use session data already loaded in AppUsageStat
      final breakdown = widget.stat.getHourlyBreakdown();
      if (mounted) {
        setState(() {
          _hourlyBreakdown = breakdown;
          _isLoadingHourly = false;
        });
      }
    }
  }

  String get _appName =>
      _appInfo?['appName'] as String? ??
      widget.stat.packageName.split('.').last;

  // ── Timer bottom sheet ─────────────────────────────────────────────────────

  void _showTimerBottomSheet() {
    final theme = Theme.of(context);
    int selectedMinutes = widget.timerLimit ?? 30;

    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setSheetState) => Container(
          decoration: BoxDecoration(
            color: theme.colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
          ),
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(context).viewInsets.bottom,
          ),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 32,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 20),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.onSurfaceVariant.withOpacity(
                        0.4,
                      ),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                Text(
                  'Set Timer for $_appName',
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 24),
                Material(
                  color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
                  borderRadius: BorderRadius.circular(20),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(
                      children: [
                        Container(
                          width: 40,
                          height: 40,
                          decoration: BoxDecoration(
                            color: theme.colorScheme.primary.withOpacity(0.12),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Icon(
                            Icons.timer_outlined,
                            color: theme.colorScheme.primary,
                            size: 20,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Daily Limit',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '$selectedMinutes minutes',
                                style: theme.textTheme.titleSmall?.copyWith(
                                  fontWeight: FontWeight.w700,
                                  color: theme.colorScheme.primary,
                                ),
                              ),
                            ],
                          ),
                        ),
                        if (_usageToday != null) ...[
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                'Used today',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                TimeTools.formatTime(_usageToday!),
                                style: theme.textTheme.titleSmall?.copyWith(
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 20),
                Slider(
                  value: selectedMinutes.toDouble(),
                  min: 5,
                  max: 300,
                  divisions: 59,
                  label: '$selectedMinutes min',
                  onChanged: (value) =>
                      setSheetState(() => selectedMinutes = value.toInt()),
                ),
                const SizedBox(height: 24),
                Row(
                  children: [
                    if (widget.hasTimer) ...[
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () {
                            widget.onTimerSet(null);
                            Navigator.pop(context);
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text('Timer removed'),
                                duration: Duration(seconds: 2),
                              ),
                            );
                          },
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: const Text('Remove'),
                        ),
                      ),
                      const SizedBox(width: 12),
                    ],
                    Expanded(
                      flex: 2,
                      child: FilledButton(
                        onPressed: () {
                          widget.onTimerSet(selectedMinutes);
                          Navigator.pop(context);
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                'Timer set to $selectedMinutes minutes',
                              ),
                              duration: const Duration(seconds: 2),
                            ),
                          );
                        },
                        style: FilledButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        child: const Text('Set Timer'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // ── Build ──────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: NestedScrollView(
        headerSliverBuilder: (context, innerBoxIsScrolled) => [
          SliverAppBar(
            expandedHeight: 120.0,
            floating: false,
            pinned: true,
            flexibleSpace: FlexibleSpaceBar(
              title: Text(
                "",
                style: theme.textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              titlePadding: const EdgeInsets.only(left: 56, bottom: 16),
              expandedTitleScale: 1.5,
            ),
            backgroundColor: theme.colorScheme.surface,
            foregroundColor: theme.colorScheme.onSurface,
            actions: [
              IconButton(
                icon: Icon(
                  widget.hasTimer ? Icons.timer : Icons.timer_outlined,
                  color: widget.hasTimer ? theme.colorScheme.primary : null,
                ),
                onPressed: _showTimerBottomSheet,
                tooltip: 'Set Timer',
              ),
            ],
          ),
        ],
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          children: [
            _buildAppHeader(theme),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _buildStatCard(
                    theme,
                    icon: Icons.access_time_rounded,
                    iconColor: theme.colorScheme.primary,
                    label: 'Screen Time',
                    value: TimeTools.formatTime(widget.stat.totalTime),
                    valueColor: theme.colorScheme.primary,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _buildStatCard(
                    theme,
                    icon: Icons.refresh_rounded,
                    iconColor: theme.colorScheme.primary,
                    label: 'Sessions',
                    value: '${widget.stat.sessionCount}',
                    valueColor: theme.colorScheme.primary,
                  ),
                ),
              ],
            ),
            if (widget.hasTimer && widget.timerLimit != null) ...[
              const SizedBox(height: 16),
              _buildTimerStatusCard(theme),
            ],
            const SizedBox(height: 20),
            Padding(
              padding: const EdgeInsets.fromLTRB(4, 0, 0, 10),
              child: Text(
                _isToday ? 'Hourly Breakdown (Today)' : 'Hourly Breakdown',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            _buildScrollableBarChart(theme),
            const SizedBox(height: 100),
          ],
        ),
      ),
    );
  }

  bool get _isToday {
    final today = DateTime.now();
    return widget.selectedDate.year == today.year &&
        widget.selectedDate.month == today.month &&
        widget.selectedDate.day == today.day;
  }

  // ── App header ─────────────────────────────────────────────────────────────

  Widget _buildAppHeader(ThemeData theme) {
    return Material(
      color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
      borderRadius: BorderRadius.circular(20),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            _buildAppIcon(theme),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _appName,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w700,
                      height: 1.2,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    widget.stat.packageName,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAppIcon(ThemeData theme) {
    if (!_isLoadingAppInfo) {
      final iconBytes = _appInfo?['icon'] as List<int>?;
      if (iconBytes != null && iconBytes.isNotEmpty) {
        return Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(14)),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(14),
            child: Image.memory(
              Uint8List.fromList(iconBytes),
              fit: BoxFit.cover,
              gaplessPlayback: true,
            ),
          ),
        );
      }
    }
    return Container(
      width: 48,
      height: 48,
      decoration: BoxDecoration(
        color: theme.colorScheme.primary.withOpacity(0.15),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Icon(Icons.apps, size: 24, color: theme.colorScheme.primary),
    );
  }

  // ── Stat card ──────────────────────────────────────────────────────────────

  Widget _buildStatCard(
    ThemeData theme, {
    required IconData icon,
    required Color iconColor,
    required String label,
    required String value,
    required Color valueColor,
  }) {
    return Material(
      color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
      borderRadius: BorderRadius.circular(20),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: iconColor.withOpacity(0.12),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, color: iconColor, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    value,
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                      color: valueColor,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── Timer status card ──────────────────────────────────────────────────────

  Widget _buildTimerStatusCard(ThemeData theme) {
    final limitMs = widget.timerLimit! * 60 * 1000;
    final usedMs = _usageToday ?? widget.stat.totalTime;
    final progress = (usedMs / limitMs).clamp(0.0, 1.0);
    final isOverLimit = usedMs >= limitMs;
    final statusColor = isOverLimit
        ? theme.colorScheme.error
        : theme.colorScheme.primary;

    return Material(
      color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
      borderRadius: BorderRadius.circular(20),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: statusColor.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    isOverLimit ? Icons.timer_off : Icons.timer,
                    color: statusColor,
                    size: 20,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        isOverLimit ? 'Limit Reached' : 'Daily Timer',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${widget.timerLimit} min limit',
                        style: theme.textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                          color: statusColor,
                        ),
                      ),
                    ],
                  ),
                ),
                if (_usageToday != null)
                  Text(
                    TimeTools.formatTime(_usageToday!),
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: progress,
                minHeight: 5,
                backgroundColor: theme.colorScheme.surfaceContainerHighest,
                color: statusColor,
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── Scrollable 24-bar chart ────────────────────────────────────────────────

  Widget _buildScrollableBarChart(ThemeData theme) {
    if (_isLoadingHourly) {
      return Material(
        color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
        borderRadius: BorderRadius.circular(20),
        child: const SizedBox(
          height: 200,
          child: Center(child: CircularProgressIndicator()),
        ),
      );
    }

    final now = DateTime.now();

    // One entry per hour 0–23
    final hourData = List.generate(
      24,
      (h) => _HourBar(
        hour: h,
        seconds: _hourlyBreakdown[h] ?? 0,
        label: _formatHourShort(h),
      ),
    );

    final maxSec = hourData.fold<int>(
      0,
      (m, b) => b.seconds > m ? b.seconds : m,
    );

    const double barWidth = 36.0;
    const double barMaxHeight = 110.0;
    const double chartHeight = 180.0;

    // For today: auto-scroll to current hour. For past dates: start at 0.
    final initialOffset = _isToday
        ? ((now.hour * barWidth) - barWidth * 3).clamp(0.0, double.infinity)
        : 0.0;

    return Material(
      color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
      borderRadius: BorderRadius.circular(20),
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 16, 12, 12),
        child: SizedBox(
          height: chartHeight,
          child: ScrollConfiguration(
            behavior: _NoGlowScrollBehavior(),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              controller: ScrollController(initialScrollOffset: initialOffset),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: hourData.map((bar) {
                  final barH = maxSec > 0
                      ? (bar.seconds / maxSec * barMaxHeight).clamp(
                          4.0,
                          barMaxHeight,
                        )
                      : 4.0;
                  final isEmpty = bar.seconds == 0;
                  // Only highlight current hour when viewing today
                  final isCurrent = _isToday && bar.hour == now.hour;

                  final barColor = isCurrent && !isEmpty
                      ? theme.colorScheme.primary
                      : isEmpty
                      ? theme.colorScheme.outlineVariant.withOpacity(0.25)
                      : theme.colorScheme.primary.withOpacity(0.55);

                  return SizedBox(
                    width: barWidth,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        // Value label above bar (only when non-empty)
                        SizedBox(
                          height: 16,
                          child: !isEmpty
                              ? Text(
                                  _formatSeconds(bar.seconds),
                                  textAlign: TextAlign.center,
                                  style: theme.textTheme.labelSmall?.copyWith(
                                    fontSize: 8,
                                    color: isCurrent
                                        ? theme.colorScheme.primary
                                        : theme.colorScheme.onSurfaceVariant,
                                    fontWeight: isCurrent
                                        ? FontWeight.w700
                                        : FontWeight.normal,
                                  ),
                                )
                              : const SizedBox.shrink(),
                        ),
                        const SizedBox(height: 3),

                        // Bar itself
                        AnimatedContainer(
                          duration: const Duration(milliseconds: 450),
                          curve: Curves.easeOut,
                          height: barH,
                          width: barWidth - 10,
                          decoration: BoxDecoration(
                            color: barColor,
                            borderRadius: BorderRadius.circular(5),
                            boxShadow: isCurrent && !isEmpty
                                ? [
                                    BoxShadow(
                                      color: theme.colorScheme.primary
                                          .withOpacity(0.35),
                                      blurRadius: 8,
                                      offset: const Offset(0, 2),
                                    ),
                                  ]
                                : null,
                          ),
                        ),
                        const SizedBox(height: 6),

                        // Hour label below bar
                        Text(
                          bar.label,
                          style: theme.textTheme.labelSmall?.copyWith(
                            fontSize: 9,
                            color: isCurrent
                                ? theme.colorScheme.primary
                                : theme.colorScheme.onSurfaceVariant,
                            fontWeight: isCurrent
                                ? FontWeight.w700
                                : FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  );
                }).toList(),
              ),
            ),
          ),
        ),
      ),
    );
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  /// e.g. 0 → "12a", 7 → "7a", 12 → "12p", 15 → "3p"
  String _formatHourShort(int hour) {
    if (hour == 0) return '12a';
    if (hour < 12) return '${hour}a';
    if (hour == 12) return '12p';
    return '${hour - 12}p';
  }

  /// Compact duration label: "45s", "12m", "1h30m"
  String _formatSeconds(int seconds) {
    if (seconds < 60) return '${seconds}s';
    final mins = seconds ~/ 60;
    if (mins < 60) return '${mins}m';
    final hrs = mins ~/ 60;
    final rem = mins % 60;
    return rem > 0 ? '${hrs}h${rem}m' : '${hrs}h';
  }
}

// ── Supporting types ──────────────────────────────────────────────────────────

class _HourBar {
  final int hour;
  final int seconds;
  final String label;
  const _HourBar({
    required this.hour,
    required this.seconds,
    required this.label,
  });
}

class _NoGlowScrollBehavior extends ScrollBehavior {
  @override
  Widget buildOverscrollIndicator(
    BuildContext context,
    Widget child,
    ScrollableDetails details,
  ) => child;
}
