import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../auth/auth_provider.dart';
import '../auth/auth_state.dart';
import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';

/// Banner compacto que muestra el nivel técnico del usuario autenticado.
/// No se renderiza si el usuario no tiene nivel asignado.
class NivelTecnicoBanner extends ConsumerWidget {
  const NivelTecnicoBanner({super.key, this.padding});

  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authNotifierProvider).asData?.value;
    if (auth is! AuthAuthenticated) return const SizedBox.shrink();
    final nivel = auth.user.nivelTecnico;
    if (nivel == null) return const SizedBox.shrink();

    Widget banner = Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.primary.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.primary.withValues(alpha: 0.25)),
      ),
      child: Row(children: [
        const Icon(Icons.signal_cellular_alt,
            size: 16, color: AppColors.primary),
        const SizedBox(width: 8),
        Text('Tu nivel técnico: ',
            style: AppTextStyles.bodySmall
                .copyWith(color: AppColors.mutedFg)),
        Text(nivel,
            style: AppTextStyles.bodyMedium.copyWith(
                color: AppColors.primary, fontWeight: FontWeight.w700)),
      ]),
    );

    if (padding != null) {
      return Padding(padding: padding!, child: banner);
    }
    return banner;
  }
}
