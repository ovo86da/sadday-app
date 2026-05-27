import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../features/informes/presentation/providers/informes_provider.dart';
import '../../features/informes/presentation/screens/informe_crear_screen.dart';
import '../auth/auth_provider.dart';
import '../auth/auth_state.dart';
import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';
import 'app_button.dart';

/// Bloquea toda interacción con la app mientras el usuario (Jefe de Salida)
/// tenga informes de montaña pendientes de completar, igual que en la web.
class InformePendienteGuard extends ConsumerWidget {
  const InformePendienteGuard({required this.child, super.key});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authVal = ref.watch(authNotifierProvider).asData?.value;
    final isAuthenticated = authVal is AuthAuthenticated;

    final pendientesAsync = ref.watch(pendientesJefeProvider);
    final pendientes = pendientesAsync.asData?.value;
    final isLoading = pendientesAsync.isLoading;
    final bloqueado =
        isAuthenticated && !isLoading && (pendientes?.isNotEmpty ?? false);
    final primero = (pendientes?.isNotEmpty ?? false) ? pendientes!.first : null;

    return Stack(
      children: [
        // Contenido normal — absorbe taps cuando está bloqueado.
        AbsorbPointer(
          absorbing: bloqueado,
          child: child,
        ),

        if (bloqueado && primero != null) ...[
          // Capa semi-transparente que cubre todo
          Positioned.fill(
            child: ColoredBox(
              color: AppColors.background.withValues(alpha: 0.88),
            ),
          ),

          // Tarjeta de aviso centrada
          Positioned.fill(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.all(24),
                    decoration: BoxDecoration(
                      color: AppColors.card,
                      border: Border.all(color: AppColors.border),
                      borderRadius: BorderRadius.circular(14),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.4),
                          blurRadius: 24,
                          offset: const Offset(0, 8),
                        ),
                      ],
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: const Color(0xFFD4A84B)
                                    .withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(20),
                              ),
                              child: const Icon(
                                Icons.warning_amber_rounded,
                                color: Color(0xFFD4A84B),
                                size: 22,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Tienes un informe de salida pendiente',
                                    style: AppTextStyles.titleSmall.copyWith(
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  const SizedBox(height: 6),
                                  RichText(
                                    text: TextSpan(
                                      style: AppTextStyles.bodySmall
                                          .copyWith(color: AppColors.mutedFg),
                                      children: [
                                        const TextSpan(
                                            text: 'Eres Jefe de Salida de '),
                                        TextSpan(
                                          text: primero.salidaNombre,
                                          style: AppTextStyles.bodySmall
                                              .copyWith(
                                                  color: AppColors.foreground,
                                                  fontWeight: FontWeight.w600),
                                        ),
                                        const TextSpan(
                                            text:
                                                '. Debes completar el informe antes de continuar.'),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        if ((pendientes?.length ?? 0) > 1) ...[
                          const SizedBox(height: 12),
                          Text(
                            'Tienes ${pendientes!.length} informes pendientes en total. Completa uno a la vez.',
                            style: AppTextStyles.bodySmall
                                .copyWith(color: AppColors.mutedFg),
                          ),
                        ],
                        const SizedBox(height: 20),
                        AppButton(
                          label: 'Llenar informe ahora',
                          fullWidth: true,
                          icon: Icons.description_outlined,
                          onPressed: () async {
                            await Navigator.of(context, rootNavigator: true)
                                .push<void>(
                              MaterialPageRoute(
                                fullscreenDialog: true,
                                builder: (_) => InformeCrearScreen(
                                  salidaId: primero.salidaId,
                                  salidaNombre: primero.salidaNombre,
                                  onSaved: () {
                                    ref.invalidate(pendientesJefeProvider);
                                  },
                                ),
                              ),
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }
}
