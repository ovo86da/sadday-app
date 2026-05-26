import 'package:flutter/material.dart';
import '../theme/app_colors.dart';

class AppTabItem {
  const AppTabItem({required this.label, required this.child, this.icon});
  final String label;
  final Widget child;
  final IconData? icon;
}

class AppTabs extends StatelessWidget {
  const AppTabs({required this.tabs, super.key});
  final List<AppTabItem> tabs;

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: tabs.length,
      child: Column(
        children: [
          TabBar(
            labelColor: AppColors.primary,
            unselectedLabelColor: AppColors.mutedFg,
            indicatorColor: AppColors.primary,
            indicatorSize: TabBarIndicatorSize.label,
            tabs: tabs
                .map((t) => Tab(
                      text: t.label,
                      icon: t.icon != null ? Icon(t.icon) : null,
                      iconMargin: const EdgeInsets.only(bottom: 2),
                    ))
                .toList(),
          ),
          Expanded(
            child: TabBarView(
              children: tabs.map((t) => t.child).toList(),
            ),
          ),
        ],
      ),
    );
  }
}
