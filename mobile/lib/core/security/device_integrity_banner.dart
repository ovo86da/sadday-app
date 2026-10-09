import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';

/// Aviso no bloqueante de dispositivo comprometido (K-01).
///
/// Se dibuja sobre la app, respetando el área segura, y el socio lo puede
/// descartar. No impide usar la aplicación — ver [DeviceIntegrity] para el
/// razonamiento.
class DeviceIntegrityBanner extends StatelessWidget {
  const DeviceIntegrityBanner({required this.onDismiss, super.key});

  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: 0,
      left: 0,
      right: 0,
      child: SafeArea(
        bottom: false,
        child: Material(
          color: AppColors.destructive,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.warning_amber_rounded,
                    color: Colors.white, size: 20),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Dispositivo no seguro',
                        style: AppTextStyles.bodyMedium.copyWith(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Detectamos que este dispositivo tiene root o jailbreak. '
                        'Tus datos podrían quedar expuestos a otras apps.',
                        style: AppTextStyles.bodySmall
                            .copyWith(color: Colors.white),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close, color: Colors.white, size: 20),
                  tooltip: 'Descartar',
                  onPressed: onDismiss,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
