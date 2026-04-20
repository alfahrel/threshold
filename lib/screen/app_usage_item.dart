import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:threshold/model.dart';
import 'package:threshold/helper/time_tools.dart';

class AppUsageItem extends StatelessWidget {
  final AppUsageStat stat;
  final Map<String, dynamic>? appInfo;
  final bool hasTimer;
  final int? timerLimit;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  const AppUsageItem({
    super.key,
    required this.stat,
    required this.appInfo,
    required this.hasTimer,
    required this.timerLimit,
    required this.onTap,
    required this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final iconBytes = appInfo?['icon'] as List<int>?;
    final appName = appInfo?['appName'] as String?;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 0, vertical: 4),
      child: Card(
        margin: EdgeInsets.zero,
        color: theme.colorScheme.secondaryContainer.withOpacity(0.5),
        child: InkWell(
          onTap: onTap,
          onLongPress: onLongPress,
          borderRadius: BorderRadius.circular(20),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                _AppIcon(iconBytes: iconBytes, hasTimer: hasTimer),
                const SizedBox(width: 16),
                Expanded(
                  child: _AppInfo(
                    appName: appName ?? stat.packageName.split('.').last,
                    totalTime: stat.totalTime,
                    sessionCount: stat.sessionCount,
                  ),
                ),
                Icon(
                  Icons.chevron_right,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _AppIcon extends StatelessWidget {
  final List<int>? iconBytes;
  final bool hasTimer;

  const _AppIcon({required this.iconBytes, required this.hasTimer});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Stack(
      children: [
        iconBytes != null && iconBytes!.isNotEmpty
            ? Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(48),
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(48),
                  child: Image.memory(
                    Uint8List.fromList(iconBytes!),
                    fit: BoxFit.cover,
                    gaplessPlayback: true,
                  ),
                ),
              )
            : Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: theme.colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(48),
                ),
                child: Icon(
                  Icons.apps,
                  color: theme.colorScheme.onPrimaryContainer,
                ),
              ),
        if (hasTimer)
          Positioned(
            right: 0,
            bottom: 0,
            child: Container(
              padding: const EdgeInsets.all(3),
              decoration: BoxDecoration(
                color: theme.colorScheme.primary,
                shape: BoxShape.circle,
                border: Border.all(color: theme.colorScheme.surface, width: 2),
              ),
              child: Icon(
                Icons.timer,
                color: theme.colorScheme.onPrimary,
                size: 12,
              ),
            ),
          ),
      ],
    );
  }
}

class _AppInfo extends StatelessWidget {
  final String appName;
  final int totalTime;
  final int sessionCount;

  const _AppInfo({
    required this.appName,
    required this.totalTime,
    required this.sessionCount,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          appName,
          style: theme.textTheme.titleSmall?.copyWith(
            fontWeight: FontWeight.bold,
          ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        const SizedBox(height: 4),
        Row(
          children: [
            Icon(
              Icons.access_time,
              size: 14,
              color: theme.colorScheme.onSurfaceVariant,
            ),
            const SizedBox(width: 4),
            Text(
              TimeTools.formatTime(totalTime),
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(width: 12),
            Icon(
              Icons.refresh,
              size: 14,
              color: theme.colorScheme.onSurfaceVariant,
            ),
            const SizedBox(width: 4),
            Text(
              '$sessionCount sessions',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ],
    );
  }
}
