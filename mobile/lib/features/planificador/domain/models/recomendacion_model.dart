class DatoTecnico {
  const DatoTecnico(this.label, this.value);
  final String label;
  final String value;
}

class Recomendacion {
  const Recomendacion({
    required this.rutaId,
    required this.rutaNombre,
    this.tipoActividad,
    this.montanaNombre,
    this.sectorZona,
    this.requierePermisos = false,
    this.trackUrl,
    this.datosTecnicos = const [],
    this.totalSalidasPrevias = 0,
    this.datosInsuficientes = true,
    this.tasaExitoPct,
    this.horaSalidaPromedioClub,
    this.pctAlquiloTransporte,
    this.costoPromedioTransporte,
    this.pctContratoGuia,
    this.costoPromedioGuia,
    this.costoTotalPromedio,
  });

  final int rutaId;
  final String rutaNombre;
  final String? tipoActividad;
  final String? montanaNombre;
  final String? sectorZona;
  final bool requierePermisos;
  final String? trackUrl;
  final List<DatoTecnico> datosTecnicos;

  // Estadísticas históricas (de informes de salidas previas).
  final int totalSalidasPrevias;
  final bool datosInsuficientes;
  final double? tasaExitoPct;
  final String? horaSalidaPromedioClub;
  final double? pctAlquiloTransporte;
  final double? costoPromedioTransporte;
  final double? pctContratoGuia;
  final double? costoPromedioGuia;
  final double? costoTotalPromedio;

  factory Recomendacion.fromJson(Map<String, dynamic> j) {
    final tecnicos = <DatoTecnico>[];
    void add(String label, dynamic value) {
      if (value is String && value.isNotEmpty) {
        tecnicos.add(DatoTecnico(label, value));
      }
    }

    add('Nivel técnico Sadday', j['saddayNivelTecnicoEscala']);
    add('Nivel físico Sadday', j['saddayNivelFisicoEscala']);
    add('Escala alpina (IFAS)', j['escalaAlpinaIfasGrado']);
    add('Dificultad roca (UIAA)',
        j['dificultadRocaUiaa'] ?? j['escaladaDificultadRocaUiaa']);
    add('Dificultad hielo', j['dificultadHieloGrado']);
    add('Equipo de montaña', j['equipoMontanaNombre']);
    add('Tipo de escalada', j['escaladaTipoEscalada']);
    add('Tipo de roca', j['escaladaTipoRoca']);
    add('Dificultad trekking', j['trekkingDificultadNombre']);
    add('Tipo de terreno', j['trekkingTipoTerreno']);
    add('Tipo de bicicleta', j['ciclismoTipoBicicleta']);
    add('Dificultad técnica', j['ciclismoDificultadTecnica']);
    add('Superficie predominante', j['ciclismoSuperficiePredominante']);

    return Recomendacion(
      rutaId: (j['rutaId'] as num?)?.toInt() ?? 0,
      rutaNombre: j['rutaNombre'] as String? ?? '',
      tipoActividad: j['tipoActividad'] as String?,
      montanaNombre: j['mountainNombre'] as String?,
      sectorZona: j['sectorZona'] as String?,
      requierePermisos: j['requierePermisos'] as bool? ?? false,
      trackUrl: j['trackUrl'] as String?,
      datosTecnicos: tecnicos,
      totalSalidasPrevias: (j['totalSalidasPrevias'] as num?)?.toInt() ?? 0,
      datosInsuficientes: j['datosInsuficientes'] as bool? ?? true,
      tasaExitoPct: (j['tasaExitoPct'] as num?)?.toDouble(),
      horaSalidaPromedioClub: j['horaSalidaPromedioClub'] as String?,
      pctAlquiloTransporte: (j['pctAlquiloTransporte'] as num?)?.toDouble(),
      costoPromedioTransporte:
          (j['costoPromedioTransporte'] as num?)?.toDouble(),
      pctContratoGuia: (j['pctContratoGuia'] as num?)?.toDouble(),
      costoPromedioGuia: (j['costoPromedioGuia'] as num?)?.toDouble(),
      costoTotalPromedio: (j['costoTotalPromedio'] as num?)?.toDouble(),
    );
  }
}
