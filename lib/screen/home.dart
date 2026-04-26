import 'package:flutter/material.dart';
import 'package:threshold/helper/app_info_cache.dart';
import 'package:threshold/helper/time_tools.dart';
import 'package:threshold/model.dart';
import 'package:threshold/helper/usage_stats.dart';
import 'package:threshold/screen/app_timers.dart';
import 'package:threshold/screen/app_usage_breakdown.dart';
import 'package:threshold/screen/app_options_sheet.dart';
import 'package:threshold/screen/app_usage_item.dart';
import 'package:threshold/screen/permission_sheet.dart';
import 'package:threshold/screen/ignored_apps.dart';

class UsageStatsHome extends StatefulWidget {
  const UsageStatsHome({super.key});

  @override
  State<UsageStatsHome> createState() => _UsageStatsHomeState();
}

class _UsageStatsHomeState extends State<UsageStatsHome>
    with WidgetsBindingObserver {
  DateTime _selectedDate = DateTime.now();
  DateTime? _earliestDate;
  DateTime? _currentDate;

  List<AppUsageStat> _stats = [];
  Set<String> _ignoredPackages = {};
  Map<String, int> _appTimers = {};
  final Map<String, Map<String, dynamic>?> _appInfoCache = {};

  // Weekly chart: start-of-day → total ms
  Map<DateTime, int> _weeklyStats = {};
  bool _isLoadingWeekly = false;

  // Average daily usage in ms
  int _averageDailyMs = 0;
  bool _isLoadingAverage = false;

  bool _isLoading = false;
  bool _hasPermission = false;
  bool _hasUsageStats = false;
  bool _hasAccessibility = false;
  bool _hasOverlay = false;
  bool _hasDeviceAdmin = false;
  bool _isRequestingPermissions = false;

  static const int _minUsageTime = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkAllPermissions();
    _loadAppTimers();
    _loadIgnoredPackages();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _isRequestingPermissions) {
      _continuePermissionFlow();
    }
  }

  Future<void> _loadIgnoredPackages() async {
    final ignored = await UsageStatsHelper.getIgnoredPackages();
    setState(() => _ignoredPackages = Set<String>.from(ignored));
  }

  Future<void> _checkAllPermissions() async {
    final usageStats = await UsageStatsHelper.hasPermission();
    final accessibility = await UsageStatsHelper.hasAccessibilityPermission();
    final overlay = await UsageStatsHelper.hasOverlayPermission();
    final deviceAdmin = await UsageStatsHelper.hasDeviceAdminPermission();

    setState(() {
      _hasUsageStats = usageStats;
      _hasAccessibility = accessibility;
      _hasOverlay = overlay;
      _hasDeviceAdmin = deviceAdmin;
      _hasPermission = usageStats;
    });

    if (!usageStats) {
      _openAllPermissionsSheet();
      return;
    }

    await _findDataAvailabilityRange();
    await _loadUsageStats();
    await _loadWeeklyStats();
    await _loadAverageDailyUsage();
  }

  Future<void> _continuePermissionFlow() async {
    await _checkAllPermissions();
    if (_isRequestingPermissions) {
      if (!_hasUsageStats ||
          !_hasAccessibility ||
          !_hasOverlay ||
          !_hasDeviceAdmin) {
        await _requestAllPermissions();
      } else {
        _isRequestingPermissions = false;
      }
    }
  }

  Future<void> _requestAllPermissions() async {
    if (!_hasUsageStats) {
      await UsageStatsHelper.requestPermission();
      return;
    }
    if (!_hasAccessibility) {
      await UsageStatsHelper.requestAccessibilityPermission();
      return;
    }
    if (!_hasOverlay) {
      await UsageStatsHelper.requestOverlayPermission();
      return;
    }
    if (!_hasDeviceAdmin) {
      await UsageStatsHelper.requestDeviceAdminPermission();
      return;
    }
    await _checkAllPermissions();
  }

  void _openAllPermissionsSheet() {
    showAllPermissionsSheet(
      context,
      onGrantPressed: () {
        _isRequestingPermissions = true;
        _requestAllPermissions();
      },
    );
  }

  Future<void> _loadUsageStats() async {
    setState(() => _isLoading = true);
    try {
      final stats = await UsageStatsHelper.getStatsByDate(_selectedDate);
      final filtered =
          stats
              .where(
                (s) =>
                    s.totalTime >= _minUsageTime &&
                    !_ignoredPackages.contains(s.packageName),
              )
              .toList()
            ..sort((a, b) => b.totalTime.compareTo(a.totalTime));

      for (final stat in filtered) {
        _appInfoCache[stat.packageName] ??= await AppInfoCache.getAppInfo(
          stat.packageName,
        );
      }

      setState(() {
        _stats = filtered;
        _isLoading = false;
      });
    } catch (_) {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _loadWeeklyStats() async {
    setState(() => _isLoadingWeekly = true);
    try {
      final weekly = await UsageStatsHelper.getWeeklyStats();
      setState(() {
        _weeklyStats = weekly;
        _isLoadingWeekly = false;
      });
    } catch (_) {
      setState(() => _isLoadingWeekly = false);
    }
  }

  Future<void> _loadAverageDailyUsage() async {
    setState(() => _isLoadingAverage = true);
    try {
      final avg = await UsageStatsHelper.getAverageDailyUsage();
      setState(() {
        _averageDailyMs = avg;
        _isLoadingAverage = false;
      });
    } catch (_) {
      setState(() => _isLoadingAverage = false);
    }
  }

  Future<void> _loadAppTimers() async {
    final timers = await UsageStatsHelper.getAppTimers();
    setState(() => _appTimers = timers);
  }

  Future<void> _findDataAvailabilityRange() async {
    final earliest = await UsageStatsHelper.getEarliestDataTimestamp();
    if (earliest != null) {
      setState(() {
        _earliestDate = DateTime.fromMillisecondsSinceEpoch(earliest);
        _currentDate = DateTime.now();
      });
    }
  }

  Future<void> _syncIgnoredPackages() async {
    await UsageStatsHelper.setIgnoredPackages(_ignoredPackages.toList());
  }

  void _navigateToBreakdown(AppUsageStat stat) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => AppUsageBreakdownScreen(
          stat: stat,
          selectedDate: _selectedDate,
          hasTimer: _appTimers.containsKey(stat.packageName),
          timerLimit: _appTimers[stat.packageName],
          onTimerSet: (limit) async {
            if (limit != null) {
              await UsageStatsHelper.setAppTimer(stat.packageName, limit);
            } else {
              await UsageStatsHelper.removeAppTimer(stat.packageName);
            }
            await _loadAppTimers();
          },
        ),
      ),
    );
  }

  Future<void> _selectDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate,
      firstDate:
          _earliestDate ?? DateTime.now().subtract(const Duration(days: 365)),
      lastDate: _currentDate ?? DateTime.now(),
    );
    if (picked != null && picked != _selectedDate) {
      setState(() => _selectedDate = picked);
      await _loadUsageStats();
    }
  }

  // ── Select day from weekly chart ───────────────────────────────────────────

  Future<void> _selectDayFromChart(DateTime day) async {
    final normalised = DateTime(day.year, day.month, day.day);
    if (_isSameDay(normalised, _selectedDate)) return;
    setState(() => _selectedDate = normalised);
    await _loadUsageStats();
  }

  // ── Menu bottom sheet ──────────────────────────────────────────────────────

  void _showMenu() {
    final theme = Theme.of(context);
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 32,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 20),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.onSurfaceVariant.withOpacity(0.4),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                ListTile(
                  leading: Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer.withOpacity(
                        0.5,
                      ),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      Icons.block,
                      color: theme.colorScheme.primary,
                      size: 20,
                    ),
                  ),
                  title: const Text('Manage Ignored Apps'),
                  onTap: () {
                    Navigator.pop(context);
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => IgnoredAppsScreen(
                          ignoredPackages: _ignoredPackages,
                          onChanged: (updated) async {
                            setState(() => _ignoredPackages = updated);
                            await _syncIgnoredPackages();
                            await _loadUsageStats();
                          },
                        ),
                      ),
                    );
                  },
                ),
                ListTile(
                  leading: Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer.withOpacity(
                        0.5,
                      ),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      Icons.timer,
                      color: theme.colorScheme.primary,
                      size: 20,
                    ),
                  ),
                  title: const Text('App Timers'),
                  onTap: () {
                    Navigator.pop(context);
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => AppTimersScreen(
                          appTimers: _appTimers,
                          onChanged: (updated) async {
                            setState(() => _appTimers = updated);
                            await _loadAppTimers();
                          },
                        ),
                      ),
                    );
                  },
                ),
                ListTile(
                  leading: Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer.withOpacity(
                        0.5,
                      ),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      Icons.security,
                      color: theme.colorScheme.primary,
                      size: 20,
                    ),
                  ),
                  title: const Text('Check Permissions'),
                  onTap: () {
                    Navigator.pop(context);
                    showPermissionsStatusSheet(
                      context,
                      onGrantPressed: () {
                        _isRequestingPermissions = true;
                        _requestAllPermissions();
                      },
                    );
                  },
                ),
                ListTile(
                  leading: Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer.withOpacity(
                        0.5,
                      ),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      Icons.info_outline,
                      color: theme.colorScheme.primary,
                      size: 20,
                    ),
                  ),
                  title: const Text('About'),
                  onTap: () {
                    Navigator.pop(context);
                    _showAboutSheet();
                  },
                ),
                ListTile(
                  leading: Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer.withOpacity(
                        0.5,
                      ),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      Icons.description_outlined,
                      color: theme.colorScheme.primary,
                      size: 20,
                    ),
                  ),
                  title: const Text('Licenses'),
                  onTap: () {
                    Navigator.pop(context);
                    showLicensePage(
                      context: context,
                      applicationName: 'Threshold',
                      applicationVersion: '1.2.0',
                      applicationLegalese: '© 2026 Threshold\nGPL v3.0 License',
                    );
                  },
                ),
                ListTile(
                  leading: Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      Icons.close,
                      color: theme.colorScheme.onSurfaceVariant,
                      size: 20,
                    ),
                  ),
                  title: const Text('Cancel'),
                  onTap: () => Navigator.pop(context),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // ── About bottom sheet ─────────────────────────────────────────────────────

  void _showAboutSheet() {
    final theme = Theme.of(context);
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => Container(
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        ),
        child: SingleChildScrollView(
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
                  'About Threshold',
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
                          width: 48,
                          height: 48,
                          decoration: BoxDecoration(
                            color: theme.colorScheme.primary.withOpacity(0.15),
                            borderRadius: BorderRadius.circular(14),
                          ),
                          child: Icon(
                            Icons.hourglass_bottom_rounded,
                            color: theme.colorScheme.primary,
                            size: 24,
                          ),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Threshold',
                                style: theme.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              Text(
                                'Version 1.2.0',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'A comprehensive screen time management app that helps you understand and control your digital habits.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 16),
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
                            color: Colors.green.withOpacity(0.12),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: const Icon(
                            Icons.lock_outline,
                            color: Colors.green,
                            size: 20,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Private by Design',
                                style: theme.textTheme.titleSmall?.copyWith(
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              Text(
                                'All data stays on your device',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  '© 2026 Threshold · Licensed under GPL v3.0',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  bool get _hasMissingPermissions =>
      !_hasUsageStats || !_hasAccessibility || !_hasOverlay || !_hasDeviceAdmin;

  String get _totalUsageTime {
    final ms = _stats.fold<int>(0, (sum, s) => sum + s.totalTime);
    return TimeTools.formatTime(ms);
  }

  bool _isSameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  String _weekdayShort(int weekday) {
    const labels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    return labels[(weekday - 1).clamp(0, 6)];
  }

  // ── Build ──────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: NestedScrollView(
        headerSliverBuilder: (context, _) => [
          SliverAppBar(
            expandedHeight: 120.0,
            floating: false,
            pinned: true,
            flexibleSpace: FlexibleSpaceBar(
              title: Text(
                'Threshold',
                style: theme.textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              titlePadding: const EdgeInsets.only(left: 16, bottom: 16),
              expandedTitleScale: 1.5,
            ),
            backgroundColor: theme.colorScheme.surface,
            foregroundColor: theme.colorScheme.onSurface,
            actions: [
              IconButton(
                icon: const Icon(Icons.calendar_today),
                tooltip: TimeTools.getDateLabel(_selectedDate),
                onPressed: _selectDate,
              ),
              IconButton(
                icon: const Icon(Icons.more_vert),
                onPressed: _showMenu,
              ),
            ],
          ),
        ],
        body: RefreshIndicator(
          onRefresh: () async {
            await _checkAllPermissions();
            await _loadUsageStats();
            await _loadWeeklyStats();
            await _loadAverageDailyUsage();
          },
          child: _isLoading
              ? const Center(child: CircularProgressIndicator())
              : _buildContent(),
        ),
      ),
    );
  }

  Widget _buildContent() {
    if (!_hasPermission) return _buildNoPermissionState();
    if (_stats.isEmpty && _weeklyStats.isEmpty) return _buildEmptyState();

    return CustomScrollView(
      slivers: [
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (_hasMissingPermissions) ...[
                  _buildMissingPermissionsCard(),
                  const SizedBox(height: 16),
                ],

                // ── Selected day total ──
                _buildDayTotalCard(Theme.of(context)),

                const SizedBox(height: 20),

                // ── Weekly chart + average ──
                _buildWeeklySection(Theme.of(context)),

                const SizedBox(height: 16),

                // ── App list header ──
                if (_stats.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Text(
                      'Apps',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),

        // ── App list ──
        if (_stats.isNotEmpty)
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildBuilderDelegate((context, index) {
                final stat = _stats[index];
                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: AppUsageItem(
                    stat: stat,
                    appInfo: _appInfoCache[stat.packageName],
                    hasTimer: _appTimers.containsKey(stat.packageName),
                    timerLimit: _appTimers[stat.packageName],
                    onTap: () => _navigateToBreakdown(stat),
                    onLongPress: () => showAppOptionsSheet(
                      context,
                      stat: stat,
                      appInfo: _appInfoCache[stat.packageName],
                      hasTimer: _appTimers.containsKey(stat.packageName),
                      timerLimit: _appTimers[stat.packageName],
                      ignoredPackages: _ignoredPackages,
                      onTimersChanged: _loadAppTimers,
                      onAppIgnored: (updated) async {
                        setState(() => _ignoredPackages = updated);
                        await _syncIgnoredPackages();
                        await _loadUsageStats();
                      },
                    ),
                  ),
                );
              }, childCount: _stats.length),
            ),
          ),

        if (_stats.isEmpty) SliverToBoxAdapter(child: _buildEmptyDayState()),

        const SliverToBoxAdapter(child: SizedBox(height: 100)),
      ],
    );
  }

  // ── Day total card ─────────────────────────────────────────────────────────

  Widget _buildDayTotalCard(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          TimeTools.getDateLabel(_selectedDate),
          style: theme.textTheme.labelLarge?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
            letterSpacing: 0.5,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          _stats.isEmpty ? '—' : _totalUsageTime,
          style: theme.textTheme.displayMedium?.copyWith(
            fontWeight: FontWeight.w800,
            color: theme.colorScheme.onSurface,
          ),
        ),
      ],
    );
  }

  // ── Weekly section ─────────────────────────────────────────────────────────

  Widget _buildWeeklySection(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Header row: title + average
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              'Last 7 Days',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w700,
              ),
            ),
            if (_isLoadingAverage)
              SizedBox(
                width: 14,
                height: 14,
                child: CircularProgressIndicator(
                  strokeWidth: 1.5,
                  color: theme.colorScheme.primary,
                ),
              )
            else if (_averageDailyMs > 0)
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    'All-time daily avg',
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  Text(
                    TimeTools.formatTime(_averageDailyMs),
                    style: theme.textTheme.labelMedium?.copyWith(
                      fontWeight: FontWeight.w700,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                ],
              ),
          ],
        ),
        const SizedBox(height: 10),

        // Chart card
        Material(
          color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
          borderRadius: BorderRadius.circular(20),
          clipBehavior: Clip.antiAlias,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 16, 12, 12),
            child: _isLoadingWeekly
                ? const SizedBox(
                    height: 120,
                    child: Center(child: CircularProgressIndicator()),
                  )
                : _buildWeeklyBarChart(theme),
          ),
        ),
      ],
    );
  }

  Widget _buildWeeklyBarChart(ThemeData theme) {
    if (_weeklyStats.isEmpty) {
      return SizedBox(
        height: 120,
        child: Center(
          child: Text(
            'No data available',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      );
    }

    final days = _weeklyStats.keys.toList()..sort();
    final maxMs = _weeklyStats.values.fold<int>(0, (m, v) => v > m ? v : m);
    const double barMaxHeight = 80.0;
    const double itemWidth = 44.0;

    return SizedBox(
      height: 140,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: days.map((day) {
          final ms = _weeklyStats[day] ?? 0;
          final isSelected = _isSameDay(day, _selectedDate);
          final isToday = _isSameDay(day, DateTime.now());
          final isEmpty = ms == 0;

          final barH = (maxMs > 0 && !isEmpty)
              ? (ms / maxMs * barMaxHeight).clamp(4.0, barMaxHeight)
              : 4.0;

          final barColor = isSelected
              ? theme.colorScheme.primary
              : isEmpty
              ? theme.colorScheme.outlineVariant.withOpacity(0.25)
              : theme.colorScheme.primary.withOpacity(0.45);

          final labelColor = isSelected
              ? theme.colorScheme.primary
              : theme.colorScheme.onSurfaceVariant;

          return GestureDetector(
            onTap: () => _selectDayFromChart(day),
            behavior: HitTestBehavior.opaque,
            child: SizedBox(
              width: itemWidth,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  // Duration label above bar
                  SizedBox(
                    height: 18,
                    child: !isEmpty
                        ? Text(
                            TimeTools.formatTime(ms),
                            textAlign: TextAlign.center,
                            style: theme.textTheme.labelSmall?.copyWith(
                              fontSize: 8,
                              color: labelColor,
                              fontWeight: isSelected
                                  ? FontWeight.w700
                                  : FontWeight.normal,
                            ),
                          )
                        : const SizedBox.shrink(),
                  ),
                  const SizedBox(height: 3),

                  // Bar
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 350),
                    curve: Curves.easeOut,
                    height: barH,
                    width: itemWidth - 12,
                    decoration: BoxDecoration(
                      color: barColor,
                      borderRadius: BorderRadius.circular(6),
                      boxShadow: isSelected && !isEmpty
                          ? [
                              BoxShadow(
                                color: theme.colorScheme.primary.withOpacity(
                                  0.35,
                                ),
                                blurRadius: 8,
                                offset: const Offset(0, 2),
                              ),
                            ]
                          : null,
                    ),
                  ),
                  const SizedBox(height: 6),

                  // Weekday label
                  Text(
                    _weekdayShort(day.weekday),
                    style: theme.textTheme.labelSmall?.copyWith(
                      fontSize: 10,
                      color: labelColor,
                      fontWeight: isSelected || isToday
                          ? FontWeight.w700
                          : FontWeight.w500,
                    ),
                  ),

                  // Small dot under today's column
                  SizedBox(
                    height: 6,
                    child: isToday
                        ? Center(
                            child: Container(
                              width: 4,
                              height: 4,
                              decoration: BoxDecoration(
                                color: isSelected
                                    ? theme.colorScheme.primary
                                    : theme.colorScheme.onSurfaceVariant
                                          .withOpacity(0.5),
                                shape: BoxShape.circle,
                              ),
                            ),
                          )
                        : const SizedBox.shrink(),
                  ),
                ],
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  // ── Empty / no-permission states ───────────────────────────────────────────

  Widget _buildNoPermissionState() {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: theme.colorScheme.primaryContainer.withOpacity(0.5),
                borderRadius: BorderRadius.circular(24),
              ),
              child: Icon(
                Icons.security,
                size: 40,
                color: theme.colorScheme.primary,
              ),
            ),
            const SizedBox(height: 24),
            Text(
              'Permissions Required',
              style: theme.textTheme.headlineSmall?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Please grant all required permissions to use this app',
              style: theme.textTheme.bodyLarge?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 32),
            FilledButton.icon(
              onPressed: _openAllPermissionsSheet,
              icon: const Icon(Icons.settings),
              label: const Text('Grant Permissions'),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 16,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// Shown when the selected day has no app data but other days do.
  Widget _buildEmptyDayState() {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 32),
      child: Column(
        children: [
          Container(
            width: 64,
            height: 64,
            decoration: BoxDecoration(
              color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Icon(
              Icons.inbox_outlined,
              size: 30,
              color: theme.colorScheme.onSecondaryContainer,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            'No usage data for this day',
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Tap a bar above to switch days',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant.withOpacity(0.6),
            ),
          ),
        ],
      ),
    );
  }

  /// Shown when there is absolutely no data at all.
  Widget _buildEmptyState() {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 48),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
                borderRadius: BorderRadius.circular(22),
              ),
              child: Icon(
                Icons.inbox_outlined,
                size: 36,
                color: theme.colorScheme.onSecondaryContainer,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'No usage data available',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Try selecting a different date',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant.withOpacity(0.6),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMissingPermissionsCard() {
    final theme = Theme.of(context);
    final missing = <Map<String, dynamic>>[
      if (!_hasUsageStats) {'name': 'Usage Stats', 'icon': Icons.bar_chart},
      if (!_hasAccessibility)
        {'name': 'Accessibility', 'icon': Icons.accessibility},
      if (!_hasOverlay) {'name': 'Display Overlay', 'icon': Icons.layers},
      if (!_hasDeviceAdmin)
        {'name': 'Device Admin', 'icon': Icons.admin_panel_settings},
    ];

    return Material(
      color: theme.colorScheme.errorContainer.withOpacity(0.5),
      borderRadius: BorderRadius.circular(20),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.error.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    Icons.warning_amber_rounded,
                    color: theme.colorScheme.error,
                    size: 20,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Missing Permissions',
                        style: theme.textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                          color: theme.colorScheme.onErrorContainer,
                        ),
                      ),
                      Text(
                        '${missing.length} permission${missing.length > 1 ? 's' : ''} needed',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.onErrorContainer.withOpacity(
                            0.7,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            ...missing.map(
              (perm) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  children: [
                    Icon(
                      perm['icon'] as IconData,
                      size: 16,
                      color: theme.colorScheme.onErrorContainer.withOpacity(
                        0.8,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Text(
                      perm['name'] as String,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onErrorContainer,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => showPermissionHelpSheet(
                      context,
                      onTryAgainPressed: () {
                        _isRequestingPermissions = true;
                        _requestAllPermissions();
                      },
                    ),
                    icon: Icon(
                      Icons.help_outline,
                      size: 16,
                      color: theme.colorScheme.onErrorContainer,
                    ),
                    label: Text(
                      'Help',
                      style: TextStyle(
                        color: theme.colorScheme.onErrorContainer,
                      ),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(
                        color: theme.colorScheme.onErrorContainer.withOpacity(
                          0.4,
                        ),
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  flex: 2,
                  child: FilledButton(
                    onPressed: () {
                      _isRequestingPermissions = true;
                      _requestAllPermissions();
                    },
                    style: FilledButton.styleFrom(
                      backgroundColor: theme.colorScheme.error,
                      foregroundColor: theme.colorScheme.onError,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: const Text('Grant Permissions'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
