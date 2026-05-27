import 'package:flutter/material.dart';
import 'package:pinput/pinput.dart';
import '../theme/app_colors.dart';
import '../theme/app_theme.dart';

class AppPinInput extends StatelessWidget {
  const AppPinInput({
    required this.onCompleted,
    this.length = 6,
    this.controller,
    this.focusNode,
    this.onChanged,
    this.errorText,
    super.key,
  });

  final void Function(String pin) onCompleted;
  final int length;
  final TextEditingController? controller;
  final FocusNode? focusNode;
  final ValueChanged<String>? onChanged;
  final String? errorText;

  @override
  Widget build(BuildContext context) {
    final defaultTheme = PinTheme(
      width: 48,
      height: 56,
      textStyle: const TextStyle(
          fontSize: 20, fontWeight: FontWeight.w600, color: AppColors.foreground),
      decoration: BoxDecoration(
        color: AppColors.secondary,
        borderRadius: BorderRadius.circular(AppTheme.radiusMd),
        border: Border.all(color: AppColors.border),
      ),
    );

    return Column(
      children: [
        Pinput(
          length: length,
          controller: controller,
          focusNode: focusNode,
          onCompleted: onCompleted,
          onChanged: onChanged,
          // Campo sensible — ocultar en screenshots y sugerencias
          obscureText: true,
          obscuringCharacter: '●',
          defaultPinTheme: defaultTheme,
          focusedPinTheme: defaultTheme.copyDecorationWith(
            border: Border.all(color: AppColors.primary, width: 1.5),
          ),
          errorPinTheme: defaultTheme.copyDecorationWith(
            border: Border.all(color: AppColors.destructive),
          ),
          pinputAutovalidateMode: PinputAutovalidateMode.onSubmit,
        ),
        if (errorText != null) ...[
          const SizedBox(height: 8),
          Text(errorText!,
              style: const TextStyle(
                  color: AppColors.destructive, fontSize: 12)),
        ],
      ],
    );
  }
}
