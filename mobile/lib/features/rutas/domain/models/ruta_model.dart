class Ruta {
  const Ruta({
    required this.id,
    required this.nombre,
    this.descripcion,
    this.montanaId,
    this.montanaNombre,
    this.tipoActividad,
    this.lugarReferencia,
    this.sectorZona,
    this.longitudKm,
    this.desnivelM,
    this.duracionDias,
    this.duracionHoras,
    this.nivelMinimo,
    this.requierePermisos = false,
    this.estado,
    this.motivoRechazo,
    this.dificultadResumen,
  });

  final int id;
  final String nombre;
  final String? descripcion;
  final int? montanaId;
  final String? montanaNombre;
  final String? tipoActividad;
  final String? lugarReferencia;
  final String? sectorZona;
  final double? longitudKm;
  final int? desnivelM;
  final int? duracionDias;
  final int? duracionHoras;
  final String? nivelMinimo;
  final bool requierePermisos;
  final String? estado;
  final String? motivoRechazo;
  final String? dificultadResumen;

  double? get longitud => longitudKm;
  double? get desnivel => desnivelM?.toDouble();
  String? get duracion {
    if (duracionDias == null && duracionHoras == null) return null;
    final d = duracionDias ?? 0;
    final h = duracionHoras ?? 0;
    if (d > 0 && h > 0) return '${d}d ${h}h';
    if (d > 0) return '${d}d';
    return '${h}h';
  }

  bool get isAprobada => estado == 'APROBADA';
  bool get isRechazada => estado == 'RECHAZADA';
  bool get isPendiente => estado == null || estado == 'PENDIENTE';

  factory Ruta.fromJson(Map<String, dynamic> j) => Ruta(
        id: (j['id'] as num?)?.toInt() ?? 0,
        nombre: j['nombre'] as String? ?? '',
        descripcion: j['descripcion'] as String? ?? j['peligrosNotas'] as String?,
        montanaId: (j['mountainId'] as num?)?.toInt(),
        montanaNombre: j['mountainNombre'] as String?,
        tipoActividad: j['tipoActividad'] as String?,
        lugarReferencia: j['lugarReferencia'] as String?,
        sectorZona: j['sectorZona'] as String?,
        longitudKm: (j['longitudKm'] as num?)?.toDouble(),
        desnivelM: (j['desnivelM'] as num?)?.toInt(),
        duracionDias: (j['duracionDias'] as num?)?.toInt(),
        duracionHoras: (j['duracionHoras'] as num?)?.toInt(),
        nivelMinimo: j['nivelMinimoSocioNombre'] as String?,
        requierePermisos: j['requierePermisos'] as bool? ?? false,
        estado: j['estado'] as String?,
        motivoRechazo: j['motivoRechazo'] as String?,
        dificultadResumen: j['dificultadResumen'] as String?,
      );
}
