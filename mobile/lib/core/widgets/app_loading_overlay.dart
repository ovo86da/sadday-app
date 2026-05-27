import 'package:flutter/material.dart';
import '../theme/app_colors.dart';

class AppLoadingOverlay extends StatelessWidget {
  const AppLoadingOverlay({
    required this.isLoading,
    required this.child,
    this.message,
    super.key,
  });

  final bool isLoading;
  final Widget child;
  final String? message;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        child,
        if (isLoading)
          ModalBarrier(
            color: AppColors.background.withValues(alpha: 0.7),
            dismissible: false,
          ),
        if (isLoading)
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const CircularProgressIndicator(color: AppColors.primary),
                if (message != null) ...[
                  const SizedBox(height: 12),
                  Text(message!,
                      style: const TextStyle(
                          color: AppColors.foreground, fontSize: 14)),
                ],
              ],
            ),
          ),
      ],
    );
  }
}
