/// Un cambio de umbrales de acceso esperando aprobación (FR-026).
///
/// Lo importante de este modelo son las [escalas]: quien aprueba desde el móvil
/// tiene que ver **qué cambia** antes del botón. Aprobar un umbral de seguridad
/// sin ver qué dice es exactamente el descuido que FR-026 existe para evitar, y
/// un botón suelto en una notificación sería construir la puerta y dejar la
/// llave puesta.
class CambioUmbral {
  const CambioUmbral({
    required this.id,
    required this.nivelSocioId,
    required this.nivelSocioNombre,
    required this.solicitadoPorNombre,
    required this.solicitadoEn,
    required this.estado,
    required this.escalas,
    required this.puedoAprobar,
    required this.puedoCancelar,
    this.motivoRechazo,
  });

  final String id;
  final String nivelSocioId;
  final String nivelSocioNombre;
  final String solicitadoPorNombre;
  final DateTime? solicitadoEn;
  final String estado;

  /// Las siete escalas, cambien o no: si solo se toca una, hay que poder ver
  /// que las otras seis se quedan como están.
  final List<CambioEscala> escalas;

  /// Lo decide el **servidor**. El móvil no calcula permisos, los acata: quien
  /// pidió el cambio no puede aprobarlo, y esa regla vive en un solo sitio.
  final bool puedoAprobar;
  final bool puedoCancelar;

  final String? motivoRechazo;

  /// Cuántas escalas cambian de verdad. Si de siete solo cambia una, conviene
  /// decirlo antes de que alguien recorra la tabla buscándola.
  int get cuantasCambian => escalas.where((e) => e.sentido != 'IGUAL').length;

  /// Si alguna relaja. Es la distinción que importa para decidir, aunque el
  /// flujo de aprobación sea el mismo para relajar y para endurecer.
  bool get algunaRelaja => escalas.any((e) => e.sentido == 'RELAJA');

  /// «hace 3 días». Los cambios no caducan, así que su antigüedad es el aviso.
  String get antiguedad {
    if (solicitadoEn == null) return '';
    final dias = DateTime.now().difference(solicitadoEn!).inDays;
    if (dias <= 0) return 'hoy';
    if (dias == 1) return 'hace 1 día';
    return 'hace $dias días';
  }

  factory CambioUmbral.fromJson(Map<String, dynamic> j) => CambioUmbral(
        id: j['id'] as String,
        nivelSocioId: j['nivelSocioId'] as String? ?? '',
        nivelSocioNombre: j['nivelSocioNombre'] as String? ?? '',
        solicitadoPorNombre: j['solicitadoPorNombre'] as String? ?? '',
        solicitadoEn: j['solicitadoEn'] == null
            ? null
            : DateTime.tryParse(j['solicitadoEn'] as String),
        estado: j['estado'] as String? ?? 'PENDIENTE',
        escalas: (j['escalas'] as List<dynamic>? ?? const [])
            .map((e) => CambioEscala.fromJson(e as Map<String, dynamic>))
            .toList(),
        // Falla cerrado: si el servidor no lo manda, no se puede aprobar.
        puedoAprobar: j['puedoAprobar'] as bool? ?? false,
        puedoCancelar: j['puedoCancelar'] as bool? ?? false,
        motivoRechazo: j['motivoRechazo'] as String?,
      );
}

class CambioEscala {
  const CambioEscala({
    required this.escala,
    required this.valorPropuesto,
    required this.sentido,
    this.valorActual,
  });

  final String escala;
  final String? valorActual;
  final String valorPropuesto;

  /// `IGUAL`, `RELAJA` o `ENDURECE`. Lo calcula el servidor porque depende del
  /// orden de cada escala, que el cliente no conoce.
  final String sentido;

  bool get cambia => sentido != 'IGUAL';

  factory CambioEscala.fromJson(Map<String, dynamic> j) => CambioEscala(
        escala: j['escala'] as String? ?? '',
        valorActual: j['valorActual'] as String?,
        valorPropuesto: j['valorPropuesto'] as String? ?? '',
        sentido: j['sentido'] as String? ?? 'IGUAL',
      );
}
