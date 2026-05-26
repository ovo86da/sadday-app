import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_loading_overlay.dart';
import '../../../../core/widgets/app_pin_input.dart';
import '../providers/login_notifier.dart';

class MfaScreen extends ConsumerStatefulWidget {
  const MfaScreen({required this.challengeToken, super.key});
  final String challengeToken;

  @override
  ConsumerState<MfaScreen> createState() => _MfaScreenState();
}

class _MfaScreenState extends ConsumerState<MfaScreen> {
  String _pin = '';

  @override
  Widget build(BuildContext context) {
    final uiState = ref.watch(loginNotifierProvider);
    final isLoading = uiState is LoginLoading;

    // Cuando pase a autenticado el router redirige automáticamente
    ref.listen(authNotifierProvider, (_, next) {
      // AuthAuthenticated → router navega a /dashboard
    });

    return AppLoadingOverlay(
      isLoading: isLoading,
      child: Scaffold(
        backgroundColor: AppColors.background,
        appBar: AppBar(
          backgroundColor: Colors.transparent,
          elevation: 0,
          leading: BackButton(color: AppColors.foreground),
        ),
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 32),
                const Icon(Icons.lock_outline,
                    color: AppColors.primary, size: 48),
                const SizedBox(height: 24),
                Text('Verificación en dos pasos',
                    style: AppTextStyles.headlineLarge,
                    textAlign: TextAlign.center),
                const SizedBox(height: 8),
                Text(
                  'Ingresa el código de 6 dígitos\nde tu app autenticadora',
                  style: AppTextStyles.bodyMedium
                      .copyWith(color: AppColors.mutedFg),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 40),
                AppPinInput(
                  length: 6,
                  onCompleted: (pin) => setState(() => _pin = pin),
                  onChanged: (pin) => setState(() => _pin = pin),
                  errorText: uiState is LoginError ? uiState.message : null,
                ),
                const Spacer(),
                AppButton(
                  label: 'Verificar',
                  fullWidth: true,
                  loading: isLoading,
                  onPressed: _pin.length == 6 ? _submit : null,
                ),
                const SizedBox(height: 16),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _submit() {
    ref
        .read(loginNotifierProvider.notifier)
        .verifyMfa(widget.challengeToken, _pin);
  }
}
