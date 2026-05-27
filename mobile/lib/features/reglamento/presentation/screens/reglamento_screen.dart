import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import '../../../../core/theme/app_colors.dart';

class ReglamentoScreen extends StatefulWidget {
  const ReglamentoScreen({super.key});

  @override
  State<ReglamentoScreen> createState() => _ReglamentoScreenState();
}

class _ReglamentoScreenState extends State<ReglamentoScreen> {
  late final Future<String> _content;

  @override
  void initState() {
    super.initState();
    _content = rootBundle.loadString('assets/docs/reglamento.md');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Reglamento General'),
        centerTitle: false,
      ),
      body: FutureBuilder<String>(
        future: _content,
        builder: (context, snap) {
          if (!snap.hasData) {
            return const Center(child: CircularProgressIndicator());
          }
          return Markdown(
            data: snap.data!,
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
            styleSheet: MarkdownStyleSheet(
              h1: const TextStyle(
                color: AppColors.primary,
                fontSize: 20,
                fontWeight: FontWeight.w700,
                height: 1.4,
              ),
              h2: const TextStyle(
                color: AppColors.foreground,
                fontSize: 16,
                fontWeight: FontWeight.w700,
                height: 1.4,
              ),
              h3: const TextStyle(
                color: AppColors.foreground,
                fontSize: 14,
                fontWeight: FontWeight.w600,
                height: 1.4,
              ),
              h4: const TextStyle(
                color: AppColors.mutedFg,
                fontSize: 13,
                fontWeight: FontWeight.w600,
              ),
              p: const TextStyle(
                color: AppColors.foreground,
                fontSize: 14,
                height: 1.6,
              ),
              listBullet: const TextStyle(
                color: AppColors.mutedFg,
                fontSize: 14,
              ),
              blockquote: const TextStyle(
                color: AppColors.mutedFg,
                fontSize: 13,
                fontStyle: FontStyle.italic,
              ),
              blockquoteDecoration: BoxDecoration(
                border: Border(
                  left: BorderSide(color: AppColors.primary, width: 3),
                ),
                color: AppColors.secondary.withValues(alpha: 0.3),
              ),
              horizontalRuleDecoration: BoxDecoration(
                border: Border(
                  top: BorderSide(color: AppColors.border, width: 1),
                ),
              ),
              strong: const TextStyle(
                color: AppColors.foreground,
                fontWeight: FontWeight.w700,
              ),
              blockquotePadding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
            ),
          );
        },
      ),
    );
  }
}
