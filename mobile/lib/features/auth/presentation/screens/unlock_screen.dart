import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_avatar.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_loading_overlay.dart';
import '../providers/unlock_notifier.dart';

class UnlockScreen extends ConsumerWidget {
  const UnlockScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(unlockNotifierProvider);
    final auth = ref.watch(authNotifierProvider).asData?.value;

    // Si el auth regresa a autenticado (refresh exitoso), el router navega solo.
    // Si regresa a unauthenticated (logout), el router va a /login.

    final userName = switch (auth) {
      AuthAuthenticated(:final user) => user.nombre,
      _ => '',
    };

    final remaining = UnlockState.maxAttempts - state.failedAttempts;

    return AppLoadingOverlay(
      isLoading: state.loading,
      child: Scaffold(
        backgroundColor: AppColors.background,
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('Sadday',
                    style: AppTextStyles.displayLarge
                        .copyWith(color: AppColors.primary),
                    textAlign: TextAlign.center),
                const SizedBox(height: 40),
                Center(
                  child: AppAvatar(name: userName, size: 72),
                ),
                const SizedBox(height: 16),
                if (userName.isNotEmpty)
                  Text('Hola, $userName',
                      style: AppTextStyles.headlineMedium,
                      textAlign: TextAlign.center),
                const SizedBox(height: 40),
                AppButton(
                  label: 'Desbloquear con biometría',
                  fullWidth: true,
                  icon: Icons.fingerprint,
                  onPressed: state.isLocked
                      ? null
                      : () => ref.read(unlockNotifierProvider.notifier).unlock(),
                ),
                if (state.error != null) ...[
                  const SizedBox(height: 12),
                  Text(state.error!,
                      style: const TextStyle(
                          color: AppColors.destructive, fontSize: 13),
                      textAlign: TextAlign.center),
                  if (state.failedAttempts > 0 && !state.isLocked)
                    Text(
                      'Intentos restantes: $remaining',
                      style: const TextStyle(
                          color: AppColors.mutedFg, fontSize: 12),
                      textAlign: TextAlign.center,
                    ),
                ],
                const SizedBox(height: 32),
                AppButton(
                  label: 'Cerrar sesión',
                  variant: AppButtonVariant.ghost,
                  fullWidth: true,
                  onPressed: () =>
                      ref.read(unlockNotifierProvider.notifier).logout(),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
