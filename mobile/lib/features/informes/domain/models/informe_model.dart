class SegmentoViajeRequest {
  SegmentoViajeRequest({
    this.origen = 'Club Sadday',
    this.destino = '',
    this.alquiloTransporte = false,
    this.tipoTransporte,
    this.costoIndividual,
  });

  String origen;
  String destino;
  bool alquiloTransporte;
  String? tipoTransporte; // CAMIONETA | FURGONETA | BUS_MEDIANO | BUS_GRANDE
  double? costoIndividual;

  Map<String, dynamic> toJson() => {
        'origen': origen,
        'destino': destino,
        'alquiloTransporte': alquiloTransporte,
        if (alquiloTransporte && tipoTransporte != null)
          'tipoTransporte': tipoTransporte,
        if (alquiloTransporte && costoIndividual != null)
          'costoIndividual': costoIndividual,
      };
}

class CreateInformeRequest {
  CreateInformeRequest({
    this.seRealizo = true,
    this.lograronCumbre = true,
    this.alquiloGuia = false,
    this.guiaSocioId,
    this.alquiloRefugio = false,
    this.acampo = false,
    List<SegmentoViajeRequest>? segmentos,
    this.horaSalidaClub,
    this.horaLlegadaMontana,
    this.horaCumbre,
    this.horaInicioDescenso,
    this.horaLlegadaAutos,
    this.horaRegresoClub,
    this.condicionesMeteorologicas,
    this.cronica,
    this.observaciones,
    this.comentariosVarios,
    this.costoGuia,
    this.costoTotal,
    this.costoPorPersona,
    this.nombreRefugio,
    this.costoRefugio,
    this.nombreCamping,
    this.costoCamping,
    this.dondeAutos,
    this.autosDescripcion,
  }) : segmentos = segmentos ?? [SegmentoViajeRequest()];

  bool seRealizo;
  bool lograronCumbre;
  bool alquiloGuia;
  String? guiaSocioId; // UUID del socio que actuó como guía (solo si !alquiloGuia)
  bool alquiloRefugio;
  bool acampo;
  List<SegmentoViajeRequest> segmentos;
  String? horaSalidaClub;
  String? horaLlegadaMontana;
  String? horaCumbre;
  String? horaInicioDescenso;
  String? horaLlegadaAutos;
  String? horaRegresoClub;
  String? condicionesMeteorologicas;
  String? cronica;
  String? observaciones;
  String? comentariosVarios;
  double? costoGuia;
  double? costoTotal;
  double? costoPorPersona;
  String? nombreRefugio;
  double? costoRefugio;
  String? nombreCamping;
  double? costoCamping;
  String? dondeAutos;
  String? autosDescripcion;

  Map<String, dynamic> toJson() {
    final m = <String, dynamic>{
      'seRealizo': seRealizo,
      'lograronCumbre': lograronCumbre,
      'alquiloGuia': alquiloGuia,
      'alquiloRefugio': alquiloRefugio,
      'acampo': acampo,
      'segmentos': segmentos.map((s) => s.toJson()).toList(),
    };
    _putIfNotEmpty(m, 'horaSalidaClub', horaSalidaClub);
    _putIfNotEmpty(m, 'horaLlegadaMontana', horaLlegadaMontana);
    _putIfNotEmpty(m, 'horaCumbre', horaCumbre);
    _putIfNotEmpty(m, 'horaInicioDescenso', horaInicioDescenso);
    _putIfNotEmpty(m, 'horaLlegadaAutos', horaLlegadaAutos);
    _putIfNotEmpty(m, 'horaRegresoClub', horaRegresoClub);
    // El backend tiene un typo en condicionesMeteorologicas
    _putIfNotEmpty(m, 'condicionesMeterologicas', condicionesMeteorologicas);
    _putIfNotEmpty(m, 'cronica', cronica);
    _putIfNotEmpty(m, 'observaciones', observaciones);
    _putIfNotEmpty(m, 'comentariosVarios', comentariosVarios);
    if (alquiloGuia && costoGuia != null) m['costoGuia'] = costoGuia;
    // Guía socio: solo aplica cuando NO se contrató guía externo
    if (!alquiloGuia && guiaSocioId != null) m['guiaSocioId'] = guiaSocioId;
    if (costoTotal != null) m['costoTotal'] = costoTotal;
    if (costoPorPersona != null) m['costoPorPersona'] = costoPorPersona;
    if (alquiloRefugio) {
      _putIfNotEmpty(m, 'nombreRefugio', nombreRefugio);
      if (costoRefugio != null) m['costoRefugio'] = costoRefugio;
    }
    if (acampo) {
      _putIfNotEmpty(m, 'nombreCamping', nombreCamping);
      if (costoCamping != null) m['costoCamping'] = costoCamping;
    }
    if (dondeAutos != null) m['dondeAutos'] = dondeAutos;
    _putIfNotEmpty(m, 'autosDescripcion', autosDescripcion);
    return m;
  }

  static void _putIfNotEmpty(Map<String, dynamic> m, String key, String? v) {
    if (v != null && v.isNotEmpty) m[key] = v;
  }
}

class InformeResumen {
  const InformeResumen({
    required this.salidaId,
    required this.salidaNombre,
    this.fechaSalida,
    this.estado,
    this.esJefe = false,
    this.tieneInforme = false,
  });

  final String salidaId; // UUID
  final String salidaNombre;
  final DateTime? fechaSalida;
  final String? estado;
  final bool esJefe;
  final bool tieneInforme;

  factory InformeResumen.fromJson(Map<String, dynamic> j) {
    final salida = j['salida'] as Map<String, dynamic>?;
    return InformeResumen(
      salidaId: (j['salidaId'] ?? salida?['id'])?.toString() ?? '',
      salidaNombre:
          j['salidaNombre'] as String? ?? salida?['nombre'] as String? ?? '',
      fechaSalida: _parseDate(j['fechaFin'] ?? j['fechaSalida']),
      estado: j['estado'] as String?,
      esJefe: j['esJefe'] as bool? ?? true, // /pendientes-jefe → siempre es jefe
      tieneInforme: j['tieneInforme'] as bool? ?? (j['estado'] != null),
    );
  }

  static DateTime? _parseDate(dynamic v) {
    if (v == null) return null;
    return DateTime.tryParse(v as String);
  }
}

class TramoTransporte {
  const TramoTransporte({
    this.id,
    this.orden,
    this.origen,
    this.destino,
    this.alquiloTransporte = false,
    this.tipoTransporte,
    this.costoIndividual,
  });
  final int? id;
  final int? orden;
  final String? origen;
  final String? destino;
  final bool alquiloTransporte;
  final String? tipoTransporte;
  final double? costoIndividual;

  factory TramoTransporte.fromJson(Map<String, dynamic> j) => TramoTransporte(
        id: (j['id'] as num?)?.toInt(),
        orden: (j['orden'] as num?)?.toInt(),
        origen: j['origen'] as String?,
        destino: j['destino'] as String?,
        alquiloTransporte:
            j['alquiloTransporte'] as bool? ?? (j['tipoTransporte'] != null),
        tipoTransporte: j['tipoTransporte'] as String?,
        costoIndividual: (j['costoIndividual'] as num?)?.toDouble(),
      );
}

class Reconocimiento {
  const Reconocimiento({
    required this.id,
    required this.socioNombre,
    required this.tipo,
    this.motivo,
  });
  final int id; // Long
  final String socioNombre;
  final String tipo;
  final String? motivo;

  factory Reconocimiento.fromJson(Map<String, dynamic> j) {
    final nombre = j['socioNombre'] as String? ?? '';
    final apellido = j['socioApellido'] as String? ?? '';
    final full = '$nombre $apellido'.trim();
    return Reconocimiento(
      id: (j['id'] as num?)?.toInt() ?? 0,
      socioNombre: full.isEmpty ? (j['socioNombre'] as String? ?? '') : full,
      tipo: j['tipo'] as String? ?? '',
      motivo: j['motivo'] as String?,
    );
  }
}

class Informe {
  const Informe({
    required this.salidaId,
    required this.salidaNombre,
    this.estado,
    this.seRealizo,
    this.lograronCumbre,
    this.alquiloGuia,
    this.guiaSocioId,
    this.costoGuia,
    this.alquiloRefugio,
    this.nombreRefugio,
    this.costoRefugio,
    this.acampo,
    this.nombreCamping,
    this.costoCamping,
    this.dondeAutos,
    this.autosDescripcion,
    this.horaSalidaClub,
    this.horaLlegadaMontana,
    this.horaCumbre,
    this.horaInicioDescenso,
    this.horaLlegadaAutos,
    this.horaRegresoClub,
    this.condicionesMeteorologicas,
    this.cronica,
    this.observaciones,
    this.comentariosVarios,
    this.costoTotal,
    this.costoPorPersona,
    this.validadoPorNombre,
    this.documentoId,
    this.tramos = const [],
    this.reconocimientos = const [],
  });

  final String salidaId; // UUID
  final String salidaNombre;
  final String? estado;
  final bool? seRealizo;
  final bool? lograronCumbre;
  final bool? alquiloGuia;
  final String? guiaSocioId;
  final double? costoGuia;
  final bool? alquiloRefugio;
  final String? nombreRefugio;
  final double? costoRefugio;
  final bool? acampo;
  final String? nombreCamping;
  final double? costoCamping;
  final String? dondeAutos;
  final String? autosDescripcion;
  final String? horaSalidaClub;
  final String? horaLlegadaMontana;
  final String? horaCumbre;
  final String? horaInicioDescenso;
  final String? horaLlegadaAutos;
  final String? horaRegresoClub;
  final String? condicionesMeteorologicas;
  final String? cronica;
  final String? observaciones;
  final String? comentariosVarios;
  final double? costoTotal;
  final double? costoPorPersona;
  final String? validadoPorNombre;
  final String? documentoId;
  final List<TramoTransporte> tramos;
  final List<Reconocimiento> reconocimientos;

  factory Informe.fromJson(Map<String, dynamic> j) => Informe(
        salidaId: j['salidaId']?.toString() ?? '',
        salidaNombre: j['salidaNombre'] as String? ?? '',
        estado: j['validadoEn'] != null ? 'VALIDADO' : 'COMPLETADO',
        seRealizo: j['seRealizo'] as bool?,
        lograronCumbre: j['lograronCumbre'] as bool?,
        alquiloGuia: j['alquiloGuia'] as bool?,
        guiaSocioId: j['guiaSocioId']?.toString(),
        costoGuia: (j['costoGuia'] as num?)?.toDouble(),
        alquiloRefugio: j['alquiloRefugio'] as bool?,
        nombreRefugio: j['nombreRefugio'] as String?,
        costoRefugio: (j['costoRefugio'] as num?)?.toDouble(),
        acampo: j['acampo'] as bool?,
        nombreCamping: j['nombreCamping'] as String?,
        costoCamping: (j['costoCamping'] as num?)?.toDouble(),
        dondeAutos: j['dondeAutos'] as String?,
        autosDescripcion: j['autosDescripcion'] as String?,
        horaSalidaClub: j['horaSalidaClub'] as String?,
        horaLlegadaMontana: j['horaLlegadaMontana'] as String?,
        horaCumbre: j['horaCumbre'] as String?,
        horaInicioDescenso: j['horaInicioDescenso'] as String?,
        horaLlegadaAutos: j['horaLlegadaAutos'] as String?,
        horaRegresoClub: j['horaRegresoClub'] as String?,
        condicionesMeteorologicas:
            j['condicionesMeterologicas'] as String? ?? // typo en el backend
                j['condicionesMeteorologicas'] as String?,
        cronica: j['cronica'] as String?,
        observaciones: j['observaciones'] as String?,
        comentariosVarios: j['comentariosVarios'] as String?,
        costoTotal: (j['costoTotal'] as num?)?.toDouble(),
        costoPorPersona: (j['costoPorPersona'] as num?)?.toDouble(),
        validadoPorNombre: j['validadoPorNombre'] as String?,
        documentoId: j['documentoId']?.toString(),
        tramos: (j['segmentos'] as List<dynamic>? ??
                j['tramos'] as List<dynamic>? ??
                [])
            .map((t) => TramoTransporte.fromJson(t as Map<String, dynamic>))
            .toList(),
        reconocimientos: (j['reconocimientos'] as List<dynamic>? ?? [])
            .map((r) => Reconocimiento.fromJson(r as Map<String, dynamic>))
            .toList(),
      );
}
