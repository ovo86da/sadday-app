import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_loading_overlay.dart';
import '../providers/login_notifier.dart';

// Lista de países latinoamericanos más comunes — ampliar según el backend.
const _countries = [
  'Ecuador', 'Colombia', 'Perú', 'Bolivia', 'Chile', 'Argentina',
  'Venezuela', 'México', 'España', 'Estados Unidos', 'Otro',
];

class CountryChallengeScreen extends ConsumerStatefulWidget {
  const CountryChallengeScreen({required this.token, super.key});
  final String token;

  @override
  ConsumerState<CountryChallengeScreen> createState() =>
      _CountryChallengeScreenState();
}

class _CountryChallengeScreenState
    extends ConsumerState<CountryChallengeScreen> {
  String? _selected;

  @override
  Widget build(BuildContext context) {
    final uiState = ref.watch(loginNotifierProvider);
    final isLoading = uiState is LoginLoading;

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
                const SizedBox(height: 16),
                const Icon(Icons.public, color: AppColors.primary, size: 48),
                const SizedBox(height: 24),
                Text('Verificación de identidad',
                    style: AppTextStyles.headlineLarge,
                    textAlign: TextAlign.center),
                const SizedBox(height: 8),
                Text(
                  'Selecciona tu país de nacimiento\npara confirmar tu identidad',
                  style: AppTextStyles.bodyMedium
                      .copyWith(color: AppColors.mutedFg),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 32),
                DropdownButtonFormField<String>(
                  initialValue: _selected,
                  hint: const Text('Selecciona tu país'),
                  dropdownColor: AppColors.sidebar,
                  decoration: const InputDecoration(
                    prefixIcon: Icon(Icons.flag_outlined),
                  ),
                  items: _countries
                      .map((c) =>
                          DropdownMenuItem<String>(value: c, child: Text(c)))
                      .toList(),
                  onChanged: (v) => setState(() => _selected = v),
                ),
                if (uiState is LoginError) ...[
                  const SizedBox(height: 12),
                  Text(uiState.message,
                      style: const TextStyle(
                          color: AppColors.destructive, fontSize: 13),
                      textAlign: TextAlign.center),
                ],
                const Spacer(),
                AppButton(
                  label: 'Confirmar',
                  fullWidth: true,
                  loading: isLoading,
                  onPressed: _selected != null ? _submit : null,
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
    if (_selected == null) return;
    ref
        .read(loginNotifierProvider.notifier)
        .verifyCountryChallenge(widget.token, _selected!);
  }
}
