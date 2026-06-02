// ── Sub-type detail models ────────────────────────────────────────────────────

class CumbreItem {
  const CumbreItem({
    required this.secuencia,
    required this.mountainId,
    required this.mountainNombre,
    this.altitud,
  });
  final int secuencia;
  final int mountainId;
  final String mountainNombre;
  final int? altitud;

  factory CumbreItem.fromJson(Map<String, dynamic> j) => CumbreItem(
        secuencia: (j['secuencia'] as num?)?.toInt() ?? 0,
        mountainId: (j['mountainId'] as num?)?.toInt() ?? 0,
        mountainNombre: j['mountainNombre'] as String? ?? '',
        altitud: (j['altitud'] as num?)?.toInt(),
      );
}

class IntegralDetail {
  const IntegralDetail({
    this.dificultadMaxTipo,
    this.dificultadMaximaDescripcion,
    this.descripcionItinerario,
    required this.cumbres,
  });
  final String? dificultadMaxTipo;
  final String? dificultadMaximaDescripcion;
  final String? descripcionItinerario;
  final List<CumbreItem> cumbres;

  factory IntegralDetail.fromJson(Map<String, dynamic> j) => IntegralDetail(
        dificultadMaxTipo: j['dificultadMaxTipo'] as String?,
        dificultadMaximaDescripcion: j['dificultadMaximaDescripcion'] as String?,
        descripcionItinerario: j['descripcionItinerario'] as String?,
        cumbres: (j['cumbres'] as List<dynamic>?)
                ?.map((c) => CumbreItem.fromJson(c as Map<String, dynamic>))
                .toList() ??
            [],
      );
}

class AlpinismoDetail {
  const AlpinismoDetail({
    required this.escalaAlpinaIfasGrado,
    required this.dificultadRocaUiaa,
    required this.dificultadHieloGrado,
    required this.compromisoTipo,
    required this.yosemiteTipo,
    required this.saddayNivelTecnicoEscala,
    required this.saddayNivelFisicoEscala,
    this.equipoMontanaNombre,
  });
  final String escalaAlpinaIfasGrado;
  final String dificultadRocaUiaa;
  final String dificultadHieloGrado;
  final String compromisoTipo;
  final String yosemiteTipo;
  final String saddayNivelTecnicoEscala;
  final String saddayNivelFisicoEscala;
  final String? equipoMontanaNombre;

  factory AlpinismoDetail.fromJson(Map<String, dynamic> j) => AlpinismoDetail(
        escalaAlpinaIfasGrado: j['escalaAlpinaIfasGrado'] as String? ?? '',
        dificultadRocaUiaa: j['dificultadRocaUiaa'] as String? ?? '',
        dificultadHieloGrado: j['dificultadHieloGrado'] as String? ?? '',
        compromisoTipo: j['compromisoTipo'] as String? ?? '',
        yosemiteTipo: j['yosemiteTipo'] as String? ?? '',
        saddayNivelTecnicoEscala: j['saddayNivelTecnicoEscala'] as String? ?? '',
        saddayNivelFisicoEscala: j['saddayNivelFisicoEscala'] as String? ?? '',
        equipoMontanaNombre: j['equipoMontanaNombre'] as String?,
      );
}

class EscaladaDetail {
  const EscaladaDetail({
    required this.dificultadRocaUiaa,
    required this.tipoEscalada,
    this.numCintas,
    this.alturaViaM,
    this.tipoRoca,
  });
  final String dificultadRocaUiaa;
  final String tipoEscalada;
  final int? numCintas;
  final int? alturaViaM;
  final String? tipoRoca;

  factory EscaladaDetail.fromJson(Map<String, dynamic> j) => EscaladaDetail(
        dificultadRocaUiaa: j['dificultadRocaUiaa'] as String? ?? '',
        tipoEscalada: j['tipoEscalada'] as String? ?? '',
        numCintas: (j['numCintas'] as num?)?.toInt(),
        alturaViaM: (j['alturaViaM'] as num?)?.toInt(),
        tipoRoca: j['tipoRoca'] as String?,
      );
}

class TrekkingDetail {
  const TrekkingDetail({
    required this.dificultadNombre,
    required this.esCircular,
    required this.fuentesAgua,
    this.tipoTerreno,
  });
  final String dificultadNombre;
  final bool esCircular;
  final bool fuentesAgua;
  final String? tipoTerreno;

  factory TrekkingDetail.fromJson(Map<String, dynamic> j) => TrekkingDetail(
        dificultadNombre: j['dificultadNombre'] as String? ?? '',
        esCircular: j['esCircular'] as bool? ?? false,
        fuentesAgua: j['fuentesAgua'] as bool? ?? false,
        tipoTerreno: j['tipoTerreno'] as String?,
      );
}

class CiclismoDetail {
  const CiclismoDetail({
    required this.tipoBicicleta,
    this.dificultadTecnica,
    this.superficiePredominante,
    this.ciclabilidadPct,
  });
  final String tipoBicicleta;
  final String? dificultadTecnica;
  final String? superficiePredominante;
  final double? ciclabilidadPct;

  factory CiclismoDetail.fromJson(Map<String, dynamic> j) => CiclismoDetail(
        tipoBicicleta: j['tipoBicicleta'] as String? ?? '',
        dificultadTecnica: j['dificultadTecnica'] as String?,
        superficiePredominante: j['superficiePredominante'] as String?,
        ciclabilidadPct: (j['ciclabilidadPct'] as num?)?.toDouble(),
      );
}

// ── Main model ────────────────────────────────────────────────────────────────

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
    this.numeroCumbres,
    this.integral,
    this.alpinismo,
    this.escalada,
    this.trekking,
    this.ciclismo,
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
  final int? numeroCumbres;
  final IntegralDetail? integral;
  final AlpinismoDetail? alpinismo;
  final EscaladaDetail? escalada;
  final TrekkingDetail? trekking;
  final CiclismoDetail? ciclismo;

  bool get isIntegral => tipoActividad == 'INTEGRAL';

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

  String get lugarDisplay {
    if (isIntegral) {
      if (numeroCumbres != null) {
        final zona = lugarReferencia != null ? ' — $lugarReferencia' : '';
        return '$numeroCumbres cumbres$zona';
      }
      return lugarReferencia ?? 'Integral multi-cumbre';
    }
    return montanaNombre ?? lugarReferencia ?? '';
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
        numeroCumbres: (j['numeroCumbres'] as num?)?.toInt(),
        integral: j['integral'] != null
            ? IntegralDetail.fromJson(j['integral'] as Map<String, dynamic>)
            : null,
        alpinismo: j['alpinismo'] != null
            ? AlpinismoDetail.fromJson(j['alpinismo'] as Map<String, dynamic>)
            : null,
        escalada: j['escalada'] != null
            ? EscaladaDetail.fromJson(j['escalada'] as Map<String, dynamic>)
            : null,
        trekking: j['trekking'] != null
            ? TrekkingDetail.fromJson(j['trekking'] as Map<String, dynamic>)
            : null,
        ciclismo: j['ciclismo'] != null
            ? CiclismoDetail.fromJson(j['ciclismo'] as Map<String, dynamic>)
            : null,
      );
}
