import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:reactive_forms/reactive_forms.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_loading_overlay.dart';
import '../providers/login_notifier.dart';

class CompleteRegistrationScreen extends ConsumerStatefulWidget {
  const CompleteRegistrationScreen({required this.invitationToken, super.key});
  final String invitationToken;

  @override
  ConsumerState<CompleteRegistrationScreen> createState() =>
      _CompleteRegistrationScreenState();
}

class _CompleteRegistrationScreenState
    extends ConsumerState<CompleteRegistrationScreen> {
  late final FormGroup _form;
  bool _obscure1 = true;
  bool _obscure2 = true;

  @override
  void initState() {
    super.initState();
    _form = FormGroup(
      {
        'nombre': FormControl<String>(validators: [Validators.required]),
        'apellido': FormControl<String>(validators: [Validators.required]),
        'username': FormControl<String>(
          validators: [
            Validators.required,
            Validators.minLength(4),
            Validators.maxLength(100),
            Validators.pattern(r'^[a-z0-9._-]+$'),
          ],
        ),
        'password': FormControl<String>(
          validators: [Validators.required, Validators.minLength(12)],
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
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 32),
                Text('Sadday',
                    style: AppTextStyles.displayLarge
                        .copyWith(color: AppColors.primary),
                    textAlign: TextAlign.center),
                const SizedBox(height: 32),
                Text('Completa tu registro',
                    style: AppTextStyles.headlineLarge),
                const SizedBox(height: 8),
                Text(
                  'Configura tu cuenta para empezar.',
                  style: AppTextStyles.bodyMedium
                      .copyWith(color: AppColors.mutedFg),
                ),
                const SizedBox(height: 32),
                ReactiveForm(
                  formGroup: _form,
                  child: Column(
                    children: [
                      ReactiveTextField<String>(
                        formControlName: 'nombre',
                        textInputAction: TextInputAction.next,
                        textCapitalization: TextCapitalization.words,
                        decoration: const InputDecoration(
                          labelText: 'Nombre',
                          prefixIcon: Icon(Icons.person_outline),
                        ),
                        validationMessages: {
                          ValidationMessage.required: (_) => 'Requerido',
                        },
                      ),
                      const SizedBox(height: 16),
                      ReactiveTextField<String>(
                        formControlName: 'apellido',
                        textInputAction: TextInputAction.next,
                        textCapitalization: TextCapitalization.words,
                        decoration: const InputDecoration(
                          labelText: 'Apellido',
                          prefixIcon: Icon(Icons.person_outline),
                        ),
                        validationMessages: {
                          ValidationMessage.required: (_) => 'Requerido',
                        },
                      ),
                      const SizedBox(height: 16),
                      ReactiveTextField<String>(
                        formControlName: 'username',
                        textInputAction: TextInputAction.next,
                        autocorrect: false,
                        enableSuggestions: false,
                        decoration: const InputDecoration(
                          labelText: 'Nombre de usuario',
                          hintText: 'ej. juan.perez',
                          prefixIcon: Icon(Icons.alternate_email),
                          helperText:
                              'Solo letras minúsculas, números, puntos, guiones',
                        ),
                        validationMessages: {
                          ValidationMessage.required: (_) => 'Requerido',
                          ValidationMessage.minLength: (_) =>
                              'Mínimo 4 caracteres',
                          ValidationMessage.maxLength: (_) =>
                              'Máximo 100 caracteres',
                          ValidationMessage.pattern: (_) =>
                              'Solo letras minúsculas, números, . - _',
                        },
                      ),
                      const SizedBox(height: 16),
                      ReactiveTextField<String>(
                        formControlName: 'password',
                        obscureText: _obscure1,
                        enableSuggestions: false,
                        autocorrect: false,
                        textInputAction: TextInputAction.next,
                        decoration: InputDecoration(
                          labelText: 'Contraseña',
                          prefixIcon: const Icon(Icons.lock_outline),
                          suffixIcon: IconButton(
                            icon: Icon(
                                _obscure1
                                    ? Icons.visibility_off
                                    : Icons.visibility,
                                color: AppColors.mutedFg),
                            onPressed: () =>
                                setState(() => _obscure1 = !_obscure1),
                          ),
                        ),
                        validationMessages: {
                          ValidationMessage.required: (_) => 'Requerido',
                          ValidationMessage.minLength: (_) =>
                              'Mínimo 12 caracteres',
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
                            icon: Icon(
                                _obscure2
                                    ? Icons.visibility_off
                                    : Icons.visibility,
                                color: AppColors.mutedFg),
                            onPressed: () =>
                                setState(() => _obscure2 = !_obscure2),
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
                      style: const TextStyle(
                          color: AppColors.destructive, fontSize: 13)),
                ],
                const SizedBox(height: 32),
                AppButton(
                  label: 'Crear cuenta',
                  fullWidth: true,
                  loading: isLoading,
                  onPressed: _submit,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _submit() {
    _form.markAllAsTouched();
    if (_form.invalid) return;
    ref.read(loginNotifierProvider.notifier).completeRegistration(
          invitationToken: widget.invitationToken,
          username: _form.control('username').value as String,
          nombre: _form.control('nombre').value as String,
          apellido: _form.control('apellido').value as String,
          password: _form.control('password').value as String,
          passwordConfirmation: _form.control('confirmation').value as String,
        );
  }
}
