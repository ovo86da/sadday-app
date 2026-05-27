import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:reactive_forms/reactive_forms.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_loading_overlay.dart';
import '../providers/login_notifier.dart';

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  ConsumerState<ForgotPasswordScreen> createState() =>
      _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState
    extends ConsumerState<ForgotPasswordScreen> {
  late final FormGroup _form;
  bool _sent = false;

  @override
  void initState() {
    super.initState();
    _form = FormGroup({
      'email': FormControl<String>(
        validators: [Validators.required, Validators.email],
      ),
    });
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
            child: _sent ? _buildSuccess(context) : _buildForm(uiState),
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
        const Icon(Icons.mail_outline, color: AppColors.primary, size: 48),
        const SizedBox(height: 24),
        Text('¿Olvidaste tu contraseña?', style: AppTextStyles.headlineLarge),
        const SizedBox(height: 8),
        Text(
          'Ingresa tu correo y te enviaremos\nun enlace para restablecerla.',
          style: AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg),
        ),
        const SizedBox(height: 32),
        ReactiveForm(
          formGroup: _form,
          child: ReactiveTextField<String>(
            formControlName: 'email',
            keyboardType: TextInputType.emailAddress,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _submit(),
            decoration: const InputDecoration(
              labelText: 'Correo electrónico',
              prefixIcon: Icon(Icons.email_outlined),
            ),
            validationMessages: {
              ValidationMessage.required: (_) => 'El correo es requerido',
              ValidationMessage.email: (_) => 'Ingresa un correo válido',
            },
          ),
        ),
        if (uiState is LoginError) ...[
          const SizedBox(height: 12),
          Text(uiState.message,
              style: const TextStyle(color: AppColors.destructive, fontSize: 13)),
        ],
        const SizedBox(height: 32),
        AppButton(label: 'Enviar enlace', fullWidth: true, onPressed: _submit),
      ],
    );
  }

  Widget _buildSuccess(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 32),
        const Icon(Icons.check_circle_outline,
            color: AppColors.salidaRealizada, size: 64),
        const SizedBox(height: 24),
        Text('¡Correo enviado!', style: AppTextStyles.headlineLarge,
            textAlign: TextAlign.center),
        const SizedBox(height: 8),
        Text(
          'Revisa tu bandeja de entrada y sigue\nlas instrucciones para restablecer\ntu contraseña.',
          style: AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 40),
        AppButton(
          label: 'Volver al inicio de sesión',
          variant: AppButtonVariant.secondary,
          fullWidth: true,
          onPressed: () => context.go('/login'),
        ),
      ],
    );
  }

  void _submit() {
    _form.markAllAsTouched();
    if (_form.invalid) return;
    final email = _form.control('email').value as String;
    ref
        .read(loginNotifierProvider.notifier)
        .forgotPassword(email.trim())
        .then((_) {
      if (mounted && ref.read(loginNotifierProvider) is LoginIdle) {
        setState(() => _sent = true);
      }
    });
  }
}
