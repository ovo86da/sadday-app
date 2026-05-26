import 'package:flutter/material.dart';
import '../theme/app_colors.dart';

class AppTableColumn<T> {
  const AppTableColumn({
    required this.label,
    required this.cell,
    this.width,
    this.numeric = false,
  });

  final String label;
  final Widget Function(T item) cell;
  final double? width;
  final bool numeric;
}

class AppTable<T> extends StatelessWidget {
  const AppTable({
    required this.columns,
    required this.rows,
    this.onRowTap,
    this.emptyLabel = 'Sin resultados',
    super.key,
  });

  final List<AppTableColumn<T>> columns;
  final List<T> rows;
  final void Function(T item)? onRowTap;
  final String emptyLabel;

  @override
  Widget build(BuildContext context) {
    if (rows.isEmpty) {
      return Center(
        child: Text(emptyLabel,
            style: const TextStyle(color: AppColors.mutedFg, fontSize: 14)),
      );
    }
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: DataTable(
        headingRowColor: WidgetStateProperty.all(AppColors.sidebar),
        dataRowColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.hovered)) return AppColors.secondary;
          return AppColors.background;
        }),
        dividerThickness: 1,
        border: TableBorder.all(color: AppColors.border, width: 1),
        columns: columns
            .map((c) => DataColumn(
                  label: Text(c.label,
                      style: const TextStyle(
                          color: AppColors.mutedFg,
                          fontSize: 12,
                          fontWeight: FontWeight.w500)),
                  numeric: c.numeric,
                ))
            .toList(),
        rows: rows.asMap().entries.map((entry) {
          final item = entry.value;
          return DataRow(
            onSelectChanged: onRowTap != null ? (_) => onRowTap!(item) : null,
            cells: columns.map((c) => DataCell(c.cell(item))).toList(),
          );
        }).toList(),
      ),
    );
  }
}
