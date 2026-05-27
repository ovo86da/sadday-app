import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_theme.dart';
import 'app_button.dart';

Future<bool?> showAppDialog({
  required BuildContext context,
  required String title,
  required String message,
  String confirmLabel = 'Confirmar',
  String cancelLabel = 'Cancelar',
  AppButtonVariant confirmVariant = AppButtonVariant.primary,
  bool barrierDismissible = true,
}) {
  return showDialog<bool>(
    context: context,
    barrierDismissible: barrierDismissible,
    builder: (_) => AppDialog(
      title: title,
      message: message,
      confirmLabel: confirmLabel,
      cancelLabel: cancelLabel,
      confirmVariant: confirmVariant,
    ),
  );
}

class AppDialog extends StatelessWidget {
  const AppDialog({
    required this.title,
    required this.message,
    this.confirmLabel = 'Confirmar',
    this.cancelLabel = 'Cancelar',
    this.confirmVariant = AppButtonVariant.primary,
    super.key,
  });

  final String title;
  final String message;
  final String confirmLabel;
  final String cancelLabel;
  final AppButtonVariant confirmVariant;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.sidebar,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppTheme.radiusXl),
        side: const BorderSide(color: AppColors.border),
      ),
      title: Text(title,
          style: const TextStyle(
              color: AppColors.foreground, fontWeight: FontWeight.w600)),
      content: Text(message,
          style: const TextStyle(color: AppColors.mutedFg, fontSize: 14)),
      actions: [
        AppButton(
          label: cancelLabel,
          variant: AppButtonVariant.ghost,
          onPressed: () => Navigator.of(context).pop(false),
        ),
        AppButton(
          label: confirmLabel,
          variant: confirmVariant,
          onPressed: () => Navigator.of(context).pop(true),
        ),
      ],
    );
  }
}
