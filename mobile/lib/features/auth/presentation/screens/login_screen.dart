import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:reactive_forms/reactive_forms.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_loading_overlay.dart';
import '../providers/login_notifier.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  late final FormGroup _form;
  bool _obscurePassword = true;

  @override
  void initState() {
    super.initState();
    _form = FormGroup({
      'username': FormControl<String>(validators: [Validators.required]),
      'password': FormControl<String>(validators: [Validators.required]),
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

    // Escuchar cambios en AuthState para navegar después del login
    ref.listen(authNotifierProvider, (_, next) {
      final auth = next.asData?.value;
      if (auth is AuthPendingMfa) {
        context.go('/mfa', extra: auth.challengeToken);
      } else if (auth is AuthPendingCountryChallenge) {
        context.go('/country-challenge', extra: auth.token);
      }
      // AuthAuthenticated → el router redirige a /dashboard automáticamente
    });

    return AppLoadingOverlay(
      isLoading: isLoading,
      child: Scaffold(
        backgroundColor: AppColors.background,
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 64),
                _buildHeader(),
                const SizedBox(height: 48),
                _buildForm(uiState),
                const SizedBox(height: 8),
                _buildForgotPassword(context),
                const SizedBox(height: 32),
                _buildSubmitButton(isLoading),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Column(
      children: [
        Image.asset(
          'assets/icons/logo_sadday.png',
          width: double.infinity,
          height: 160,
          fit: BoxFit.contain,
        ),
        const SizedBox(height: 24),
        Text('Bienvenido', style: AppTextStyles.headlineLarge),
      ],
    );
  }

  Widget _buildForm(LoginUiState uiState) {
    return ReactiveForm(
      formGroup: _form,
      child: Column(
        children: [
          ReactiveTextField<String>(
            formControlName: 'username',
            keyboardType: TextInputType.text,
            textInputAction: TextInputAction.next,
            autocorrect: false,
            enableSuggestions: false,
            decoration: const InputDecoration(
              labelText: 'Usuario',
              prefixIcon: Icon(Icons.person_outline),
            ),
            validationMessages: {
              ValidationMessage.required: (_) => 'El usuario es requerido',
            },
          ),
          const SizedBox(height: 16),
          ReactiveTextField<String>(
            formControlName: 'password',
            obscureText: _obscurePassword,
            enableSuggestions: false,
            autocorrect: false,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _submit(),
            decoration: InputDecoration(
              labelText: 'Contraseña',
              prefixIcon: const Icon(Icons.lock_outline),
              suffixIcon: IconButton(
                icon: Icon(
                  _obscurePassword ? Icons.visibility_off : Icons.visibility,
                  color: AppColors.mutedFg,
                ),
                onPressed: () =>
                    setState(() => _obscurePassword = !_obscurePassword),
              ),
            ),
            validationMessages: {
              ValidationMessage.required: (_) => 'La contraseña es requerida',
            },
          ),
          if (uiState is LoginError) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.destructive.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                    color: AppColors.destructive.withValues(alpha: 0.3)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.error_outline,
                      color: AppColors.destructive, size: 16),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(uiState.message,
                        style: const TextStyle(
                            color: AppColors.destructive, fontSize: 13)),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildForgotPassword(BuildContext context) {
    return Align(
      alignment: Alignment.centerRight,
      child: TextButton(
        onPressed: () => context.go('/forgot-password'),
        child: const Text('¿Olvidaste tu contraseña?',
            style: TextStyle(color: AppColors.primary, fontSize: 13)),
      ),
    );
  }

  Widget _buildSubmitButton(bool isLoading) {
    return AppButton(
      label: 'Iniciar sesión',
      fullWidth: true,
      loading: isLoading,
      onPressed: _submit,
    );
  }

  void _submit() {
    _form.markAllAsTouched();
    if (_form.invalid) return;
    final username = _form.control('username').value as String;
    final password = _form.control('password').value as String;
    ref.read(loginNotifierProvider.notifier).login(username.trim(), password);
  }
}
