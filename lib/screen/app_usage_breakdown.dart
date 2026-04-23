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

  const AppUsageBreakdownScreen({
    Key? key,
    required this.stat,
    this.hasTimer = false,
    this.timerLimit,
    required this.onTimerSet,
  }) : super(key: key);

  @override
  State<AppUsageBreakdownScreen> createState() =>
      _AppUsageBreakdownScreenState();
}

class _AppUsageBreakdownScreenState extends State<AppUsageBreakdownScreen> {
  Map<String, dynamic>? _appInfo;
  int? _usageToday;
  bool _isLoadingAppInfo = true;

  @override
  void initState() {
    super.initState();
    _loadAppInfo();
    _loadUsageToday();
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
                // ── Drag handle ──
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

                // ── Limit display card ──
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

                // ── Slider ──
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

                // ── Actions ──
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
            // ── App header card ──
            _buildAppHeader(theme),
            const SizedBox(height: 16),

            // ── Stat row ──
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

            // ── Timer status card (if active) ──
            if (widget.hasTimer && widget.timerLimit != null) ...[
              const SizedBox(height: 16),
              _buildTimerStatusCard(theme),
            ],

            // ── Hourly chart ──
            const SizedBox(height: 20),
            Padding(
              padding: const EdgeInsets.fromLTRB(4, 0, 0, 10),
              child: Text(
                'Hourly Breakdown',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            _buildLineChart(theme),

            const SizedBox(height: 100),
          ],
        ),
      ),
    );
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
    final statusColor = isOverLimit ? theme.colorScheme.error : theme.colorScheme.primary;

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

  Widget _buildLineChart(ThemeData theme) {
    final hourlyUsage = widget.stat.getHourlyBreakdown();

    final blocks = <Map<String, dynamic>>[];
    for (int start = 0; start < 24; start += 4) {
      final totalSec = List.generate(
        4,
        (i) => start + i,
      ).fold(0, (sum, h) => sum + (hourlyUsage[h] ?? 0));
      blocks.add({
        'label': TimeTools.formatHour(start),
        'value': (totalSec / 60).ceilToDouble(), // seconds → minutes
      });
    }

    final maxBlock = blocks.fold<double>(
      0,
      (m, b) => (b['value'] as double) > m ? b['value'] as double : m,
    );

    return Material(
      color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
      borderRadius: BorderRadius.circular(20),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: SizedBox(
          height: 180,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: blocks.map((block) {
              final value = block['value'] as double;
              final barH = maxBlock > 0
                  ? (value / maxBlock * 120).clamp(4.0, 120.0)
                  : 4.0;
              final isEmpty = value == 0;

              return Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      if (!isEmpty)
                        Text(
                          '${value.toInt()}m',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                            fontSize: 9,
                          ),
                        ),
                      const SizedBox(height: 4),
                      Container(
                        height: barH,
                        decoration: BoxDecoration(
                          color: isEmpty
                              ? theme.colorScheme.outlineVariant.withOpacity(
                                  0.3,
                                )
                              : theme.colorScheme.primary.withOpacity(0.7),
                          borderRadius: BorderRadius.circular(4),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        block['label'] as String,
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
              );
            }).toList(),
          ),
        ),
      ),
    );
  }
}
