import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/l10n/locale_provider.dart';
import 'l10n/app_localizations.dart';
import 'core/security/app_lifecycle_observer.dart';
import 'core/security/inactivity_notifier.dart';
import 'core/theme/app_colors.dart';
import 'core/theme/app_theme.dart';
import 'router.dart';

class SaddayApp extends StatefulWidget {
  const SaddayApp({super.key});

  @override
  State<SaddayApp> createState() => _SaddayAppState();
}

class _SaddayAppState extends State<SaddayApp> {
  final _lifecycleObserver = AppLifecycleObserver();

  @override
  void dispose() {
    _lifecycleObserver.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer(
      builder: (context, ref, _) {
        final locale = ref.watch(localeProvider);
        final router = ref.watch(routerProvider);
        return GestureDetector(
          behavior: HitTestBehavior.translucent,
          onTap: () => ref.read(inactivityNotifierProvider.notifier).resetTimer(),
          onPanDown: (_) =>
              ref.read(inactivityNotifierProvider.notifier).resetTimer(),
          child: MaterialApp.router(
            title: 'Sadday',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.dark,
            routerConfig: router,
            locale: locale,
            supportedLocales: const [Locale('es'), Locale('en')],
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            // El privacy overlay debe vivir dentro del MaterialApp para tener
            // acceso a Directionality y MediaQuery.
            builder: (context, child) {
              return ValueListenableBuilder<bool>(
                valueListenable: _lifecycleObserver.obscured,
                builder: (context, obscured, _) {
                  return Stack(
                    children: [
                      child ?? const SizedBox.shrink(),
                      if (obscured) const _PrivacyOverlay(),
                    ],
                  );
                },
              );
            },
          ),
        );
      },
    );
  }
}

class _PrivacyOverlay extends StatelessWidget {
  const _PrivacyOverlay();

  @override
  Widget build(BuildContext context) {
    return const Positioned.fill(
      child: ColoredBox(
        color: AppColors.background,
        child: Center(
          child: Text(
            'Sadday',
            style: TextStyle(
              color: AppColors.primary,
              fontSize: 32,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
      ),
    );
  }
}
