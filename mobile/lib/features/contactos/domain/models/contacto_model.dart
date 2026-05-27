class Contacto {
  const Contacto({
    required this.id,
    required this.nombre,
    required this.tipo,
    this.telefono,
    this.email,
    this.notas,
    this.tipos = const [],
  });

  final int id;
  final String nombre;
  final String? telefono;
  final String? email; // backend: correo
  final String tipo;
  final String? notas;
  final List<String> tipos;

  factory Contacto.fromJson(Map<String, dynamic> j) {
    final tiposList = (j['tiposContacto'] as List<dynamic>?)
            ?.map((e) => e.toString())
            .toList() ??
        const <String>[];
    return Contacto(
      id: (j['id'] as num?)?.toInt() ?? 0,
      nombre: j['nombre'] as String? ?? '',
      telefono: j['telefono'] as String?,
      email: j['correo'] as String? ?? j['email'] as String?,
      tipo: tiposList.isNotEmpty ? tiposList.first : (j['tipo'] as String? ?? 'OTRO'),
      tipos: tiposList,
      notas: j['notas'] as String?,
    );
  }
}
