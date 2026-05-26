class PagedResponse<T> {
  const PagedResponse({
    required this.items,
    required this.totalElements,
    required this.totalPages,
    required this.currentPage,
  });

  final List<T> items;
  final int totalElements;
  final int totalPages;
  final int currentPage;

  bool get isLast => currentPage >= totalPages - 1;

  factory PagedResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) fromJson,
  ) {
    final content = (json['content'] as List<dynamic>? ?? [])
        .map((e) => fromJson(e as Map<String, dynamic>))
        .toList();
    // Spring Boot 3.x serializa la metadata de paginación dentro de `page`.
    // Versiones anteriores la ponían plana en la raíz — soportamos ambas.
    final meta = json['page'] as Map<String, dynamic>? ?? json;
    return PagedResponse(
      items: content,
      totalElements: (meta['totalElements'] as num?)?.toInt() ?? content.length,
      totalPages: (meta['totalPages'] as num?)?.toInt() ?? 1,
      currentPage: (meta['number'] as num?)?.toInt() ?? 0,
    );
  }
}
