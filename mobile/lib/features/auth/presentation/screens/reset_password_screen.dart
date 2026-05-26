import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:reactive_forms/reactive_forms.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_loading_overlay.dart';
import '../providers/login_notifier.dart';

class ResetPasswordScreen extends ConsumerStatefulWidget {
  const ResetPasswordScreen({required this.token, super.key});
  final String token;

  @override
  ConsumerState<ResetPasswordScreen> createState() =>
      _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends ConsumerState<ResetPasswordScreen> {
  late final FormGroup _form;
  bool _done = false;
  bool _obscure1 = true;
  bool _obscure2 = true;

  @override
  void initState() {
    super.initState();
    _form = FormGroup(
      {
        'password': FormControl<String>(
          validators: [Validators.required, Validators.minLength(8)],
        ),
        'confirmation': FormControl<String>(validators: [Validators.required]),
      },
      validators: [Validators.mustMatch('password', 'confirmation')],
    );
  }

  @override
  void dispose() {
    _form.dispose();
    super.dispose();
  }

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
            child: _done ? _buildSuccess(context) : _buildForm(uiState),
          ),
        ),
      ),
    );
  }

  Widget _buildForm(LoginUiState uiState) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 16),
        Text('Nueva contraseña', style: AppTextStyles.headlineLarge),
        const SizedBox(height: 8),
        Text('Elige una contraseña segura de al menos 8 caracteres.',
            style: AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg)),
        const SizedBox(height: 32),
        ReactiveForm(
          formGroup: _form,
          child: Column(
            children: [
              ReactiveTextField<String>(
                formControlName: 'password',
                obscureText: _obscure1,
                enableSuggestions: false,
                autocorrect: false,
                textInputAction: TextInputAction.next,
                decoration: InputDecoration(
                  labelText: 'Nueva contraseña',
                  prefixIcon: const Icon(Icons.lock_outline),
                  suffixIcon: IconButton(
                    icon: Icon(_obscure1 ? Icons.visibility_off : Icons.visibility,
                        color: AppColors.mutedFg),
                    onPressed: () => setState(() => _obscure1 = !_obscure1),
                  ),
                ),
                validationMessages: {
                  ValidationMessage.required: (_) => 'Requerido',
                  ValidationMessage.minLength: (_) => 'Mínimo 8 caracteres',
                },
              ),
              const SizedBox(height: 16),
              ReactiveTextField<String>(
                formControlName: 'confirmation',
                obscureText: _obscure2,
                enableSuggestions: false,
                autocorrect: false,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _submit(),
                decoration: InputDecoration(
                  labelText: 'Confirmar contraseña',
                  prefixIcon: const Icon(Icons.lock_outline),
                  suffixIcon: IconButton(
                    icon: Icon(_obscure2 ? Icons.visibility_off : Icons.visibility,
                        color: AppColors.mutedFg),
                    onPressed: () => setState(() => _obscure2 = !_obscure2),
                  ),
                ),
                validationMessages: {
                  ValidationMessage.required: (_) => 'Requerido',
                  ValidationMessage.mustMatch: (_) =>
                      'Las contraseñas no coinciden',
                },
              ),
            ],
          ),
        ),
        if (uiState is LoginError) ...[
          const SizedBox(height: 12),
          Text(uiState.message,
              style:
                  const TextStyle(color: AppColors.destructive, fontSize: 13)),
        ],
        const SizedBox(height: 32),
        AppButton(
          label: 'Guardar contraseña',
          fullWidth: true,
          loading: uiState is LoginLoading,
          onPressed: _submit,
        ),
      ],
    );
  }

  Widget _buildSuccess(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 48),
        const Icon(Icons.check_circle_outline,
            color: AppColors.salidaRealizada, size: 64),
        const SizedBox(height: 24),
        Text('¡Contraseña actualizada!',
            style: AppTextStyles.headlineLarge, textAlign: TextAlign.center),
        const SizedBox(height: 40),
        AppButton(
          label: 'Iniciar sesión',
          fullWidth: true,
          onPressed: () => context.go('/login'),
        ),
      ],
    );
  }

  void _submit() {
    _form.markAllAsTouched();
    if (_form.invalid) return;
    ref
        .read(loginNotifierProvider.notifier)
        .resetPassword(
          token: widget.token,
          password: _form.control('password').value as String,
          passwordConfirmation: _form.control('confirmation').value as String,
        )
        .then((_) {
      if (mounted && ref.read(loginNotifierProvider) is LoginIdle) {
        setState(() => _done = true);
      }
    });
  }
}
