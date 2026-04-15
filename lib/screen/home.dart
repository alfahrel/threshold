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
import 'package:threshold/screen/usage_summary_card.dart';
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

  bool _isLoading = false;
  bool _hasPermission = false;
  bool _hasUsageStats = false;
  bool _hasAccessibility = false;
  bool _hasOverlay = false;
  bool _hasDeviceAdmin = false;
  bool _isRequestingPermissions = false;

  static const int _minUsageTime = 180000;

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
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Center(
              child: Container(
                width: 32,
                height: 4,
                margin: const EdgeInsets.only(top: 12, bottom: 20),
                decoration: BoxDecoration(
                  color: theme.colorScheme.onSurfaceVariant.withOpacity(0.4),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.block),
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
              leading: const Icon(Icons.timer),
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
              leading: const Icon(Icons.security),
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
              leading: const Icon(Icons.info_outline),
              title: const Text('About'),
              onTap: () {
                Navigator.pop(context);
                _showAboutSheet();
              },
            ),
            ListTile(
              leading: const Icon(Icons.description_outlined),
              title: const Text('Licenses'),
              onTap: () {
                Navigator.pop(context);
                showLicensePage(
                  context: context,
                  applicationName: 'Threshold',
                  applicationVersion: '1.0.0',
                  applicationLegalese: '© 2026 Threshold\nGPL v3.0 License',
                );
              },
            ),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

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
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Center(
                child: Container(
                  width: 32,
                  height: 4,
                  margin: const EdgeInsets.only(top: 12, bottom: 20),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.onSurfaceVariant.withOpacity(0.4),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'About Threshold',
                      style: theme.textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text(
                      'Threshold',
                      style: theme.textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Version 1.0.0',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'A comprehensive screen time management app that helps you understand and control your digital habits.',
                    ),
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.primaryContainer.withOpacity(
                          0.3,
                        ),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            Icons.lock_outline,
                            size: 20,
                            color: theme.colorScheme.primary,
                          ),
                          const SizedBox(width: 12),
                          const Expanded(
                            child: Text(
                              'All data stays on your device',
                              style: TextStyle(fontSize: 13),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      '© 2026 Threshold\nLicensed under GPL v3.0',
                      style: TextStyle(fontSize: 12),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }

  bool get _hasMissingPermissions =>
      !_hasUsageStats || !_hasAccessibility || !_hasOverlay || !_hasDeviceAdmin;

  String get _totalUsageTime {
    final ms = _stats.fold<int>(0, (sum, s) => sum + s.totalTime);
    return TimeTools.formatTime(ms);
  }

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
    if (_stats.isEmpty) return _buildEmptyState();

    return CustomScrollView(
      slivers: [
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (_hasMissingPermissions) _buildMissingPermissionsCard(),
                TotalUsageCard(
                  totalTime: _totalUsageTime,
                  appCount: _stats.length,
                ),
              ],
            ),
          ),
        ),
        SliverList(
          delegate: SliverChildBuilderDelegate((context, index) {
            final stat = _stats[index];
            return AppUsageItem(
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
            );
          }, childCount: _stats.length),
        ),
        const SliverToBoxAdapter(child: SizedBox(height: 100)),
      ],
    );
  }

  Widget _buildNoPermissionState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.security,
              size: 80,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(height: 24),
            Text(
              'Permissions Required',
              style: Theme.of(
                context,
              ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            Text(
              'Please grant all required permissions to use this app',
              style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
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
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.inbox_outlined,
            size: 80,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
          const SizedBox(height: 16),
          Text(
            'No usage data available',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            'Try selecting a different date',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
        ],
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

    return Card(
      color: theme.colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.warning_amber_rounded,
                  color: theme.colorScheme.onErrorContainer,
                  size: 28,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Missing Permissions',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: theme.colorScheme.onErrorContainer,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ...missing.map(
              (perm) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  children: [
                    Icon(
                      perm['icon'] as IconData,
                      size: 18,
                      color: theme.colorScheme.onErrorContainer.withOpacity(
                        0.8,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      perm['name'] as String,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onErrorContainer,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: FilledButton.tonal(
                onPressed: () {
                  _isRequestingPermissions = true;
                  _requestAllPermissions();
                },
                style: FilledButton.styleFrom(
                  backgroundColor: theme.colorScheme.onErrorContainer,
                  foregroundColor: theme.colorScheme.errorContainer,
                ),
                child: const Text('Grant Missing Permissions'),
              ),
            ),
            OutlinedButton.icon(
              onPressed: () => showPermissionHelpSheet(
                context,
                onTryAgainPressed: () {
                  _isRequestingPermissions = true;
                  _requestAllPermissions();
                },
              ),
              icon: Icon(
                Icons.help_outline,
                size: 18,
                color: theme.colorScheme.onErrorContainer,
              ),
              label: Text(
                'Need Help?',
                style: TextStyle(color: theme.colorScheme.onErrorContainer),
              ),
              style: OutlinedButton.styleFrom(
                side: BorderSide(color: theme.colorScheme.onErrorContainer),
                minimumSize: const Size(double.infinity, 36),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
