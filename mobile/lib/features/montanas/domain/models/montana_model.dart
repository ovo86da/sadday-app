class Montana {
  const Montana({
    required this.id,
    required this.nombre,
    this.descripcion,
    this.altitud,
    this.pais,
    this.region,
    this.numRutas = 0,
  });

  final int id;
  final String nombre;
  final String? descripcion;
  final double? altitud;
  final String? pais;
  final String? region;
  final int numRutas;

  factory Montana.fromJson(Map<String, dynamic> j) => Montana(
        id: (j['id'] as num?)?.toInt() ?? 0,
        nombre: j['nombre'] as String? ?? '',
        descripcion: j['descripcion'] as String?,
        altitud: (j['altitud'] as num?)?.toDouble(),
        pais: j['pais'] as String?,
        region: j['region'] as String?,
        numRutas: (j['numRutas'] as num?)?.toInt() ?? 0,
      );
}
