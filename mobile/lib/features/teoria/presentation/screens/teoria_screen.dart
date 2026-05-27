import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';

// ─── Paleta de colores de escala ──────────────────────────────────────────────

class _C {
  final Color bg;
  final Color fg;
  const _C(this.bg, this.fg);
  static const verde    = _C(Color(0x264CAF8A), Color(0xFF4CAF8A));
  static const lima     = _C(Color(0x268BC34A), Color(0xFF7CB342));
  static const amarillo = _C(Color(0x26D4A84B), Color(0xFFD4A84B));
  static const naranja  = _C(Color(0x26E07B39), Color(0xFFE07B39));
  static const rojo     = _C(Color(0x26E5534B), Color(0xFFE5534B));
  static const morado   = _C(Color(0x269B6BD4), Color(0xFF9B6BD4));
  static const gris     = _C(Color(0xFF2A2A2A), Color(0xFFAAAAAA));
}

// ─── Modelos de datos (privados a este archivo) ───────────────────────────────

class _SE {
  final String badge;
  final _C color;
  final String? label;
  final String desc;
  const _SE(this.badge, this.color, this.desc, {this.label});
}

class _SS {
  final String title;
  final String? subtitle;
  final List<_SE> entries;
  const _SS(this.title, this.entries, {this.subtitle});
}

class _Det {
  final String label;
  final String value;
  const _Det(this.label, this.value);
}

class _Act {
  final IconData icon;
  final Color iconColor;
  final Color iconBg;
  final String title;
  final String desc;
  final List<_Det> details;
  const _Act({
    required this.icon,
    required this.iconColor,
    required this.iconBg,
    required this.title,
    required this.desc,
    required this.details,
  });
}

class _Niv {
  final int nivel;
  final String nombre;
  final _C color;
  final List<String> items;
  const _Niv(this.nivel, this.nombre, this.color, this.items);
}

class _Eq {
  final String nombre;
  final String desc;
  final _C color;
  final List<String> items;
  const _Eq(this.nombre, this.desc, this.color, this.items);
}

// ─── Datos estáticos ──────────────────────────────────────────────────────────

const _kSections = <_SS>[
  _SS(
    'Escala Alpina IFAS',
    [
      _SE('F',  _C.verde,    'Caminata técnica. Terreno fácil, glaciar poco complejo. Manos para equilibrio ocasional.',  label: 'Fácil'),
      _SE('PD', _C.lima,     'Pendientes de nieve/hielo hasta ~45°. Manejo básico de cuerda y crampones.',               label: 'Poco difícil'),
      _SE('AD', _C.amarillo, 'Pasos de roca aprox. III, pendientes 45°–65°. Requiere experiencia.',                      label: 'Algo difícil'),
      _SE('D',  _C.naranja,  'Escalada sostenida (IV/V) y hielo hasta ~70°. Nivel avanzado.',                            label: 'Difícil'),
      _SE('TD', _C.rojo,     'Rutas largas, técnicas y con peligros objetivos (avalanchas, desprendimientos).',          label: 'Muy difícil'),
      _SE('ED', _C.morado,   'Élite. Terreno muy vertical, mucha exposición, gran exigencia.',                           label: 'Extremadamente difícil'),
    ],
    subtitle: 'Sistema Francés — rutas de Alpinismo',
  ),
  _SS(
    'Dificultad en Roca — UIAA / Francesa',
    [
      _SE('NA',   _C.gris,     'Sin partes de escalada en roca.',                                                                     label: '—'),
      _SE('I',    _C.verde,    'Trepada fácil. Manos para el equilibrio. Normalmente sin cuerda.',                                    label: '1'),
      _SE('II',   _C.verde,    'Trepada/paso de bloque. Manos necesarias. Exposición baja.',                                         label: '2'),
      _SE('III',  _C.lima,     'Escalada simple. Los principiantes suelen necesitar cuerda.',                                         label: '3'),
      _SE('IV',   _C.amarillo, 'Técnica clásica. Cuerda y arnés obligatorios.',                                                       label: '4a–4c'),
      _SE('V',    _C.naranja,  'Dificultad media. Vertical con buenas presas.',                                                       label: '5a–5c'),
      _SE('VI',   _C.rojo,     'Dificultad alta. Presas pequeñas, fuerza y técnica.',                                                label: '6a–6b'),
      _SE('VII+', _C.rojo,     'Movimientos técnicos continuos, presas pequeñas, secciones verticales o desplomadas.',               label: '6c–7a+'),
      _SE('VIII', _C.morado,   'Nivel experto: escalada atlética con movimientos dinámicos y alta exigencia física.',                label: '7b–8a'),
      _SE('IX',   _C.morado,   'Élite mundial: movimientos extremadamente técnicos. Reservado para escaladores de alto rendimiento.', label: '8b–9a'),
    ],
    subtitle: 'Sistema internacional — Escalada y Alpinismo',
  ),
  _SS(
    'Dificultad en Hielo — WI (Water Ice)',
    [
      _SE('NA',  _C.gris,    'Sin escalada en hielo.'),
      _SE('WI1', _C.verde,   '~45°. Se progresa casi caminando con crampones.'),
      _SE('WI2', _C.verde,   '~60°. Hielo sólido, técnica básica (1 piolet posible).'),
      _SE('WI3', _C.amarillo,'70°–80°. Largo y consistente. Normalmente dos piolets.'),
      _SE('WI4', _C.naranja, '90° vertical, buena calidad, tramos verticales. Muy físico.'),
      _SE('WI5', _C.rojo,    'Verticalidad sostenida, hielo más frágil (columnas/coliflor).'),
      _SE('WI6', _C.rojo,    'Muy técnico, vertical/desplomado, hielo delgado o inestable.'),
      _SE('WI7', _C.morado,  'Límite. Hielo extremadamente fino, estructuras colgantes frágiles. Solo expertos top.'),
    ],
    subtitle: 'Escala de hielo cascada — Alpinismo con hielo',
  ),
  _SS(
    'Compromiso',
    [
      _SE('I',   _C.verde,   'Ruta corta, retirada evidente, cerca de civilización.'),
      _SE('II',  _C.verde,   'Media jornada, retirada sencilla, acceso a rescate rápido.'),
      _SE('III', _C.amarillo,'Jornada completa. El descenso puede requerir varios rápeles.'),
      _SE('IV',  _C.naranja, 'Jornada larga e intensa. Retirada difícil; mal tiempo = problemas serios.'),
      _SE('V',   _C.rojo,    'Varios días. Vivac. Retirada extremadamente compleja una vez adentro.'),
      _SE('VI',  _C.rojo,    'Gran pared/expedición remota. Autosuficiencia total.'),
      _SE('VII', _C.morado,  'Máximo. Himalaya/Antártida, gran altitud, rescate casi imposible.'),
    ],
    subtitle: 'Riesgo objetivo y complejidad de la retirada',
  ),
  _SS(
    'Sistema de Clases Yosemite',
    [
      _SE('Clase 1', _C.verde,   'Senderismo por camino.'),
      _SE('Clase 2', _C.lima,    'Terreno irregular, manos para equilibrio.'),
      _SE('Clase 3', _C.amarillo,'Trepada (scrambling). Riesgo de caída; algunos usan cuerda.'),
      _SE('Clase 4', _C.naranja, 'Muy expuesto. Cuerda casi siempre.'),
      _SE('Clase 5', _C.rojo,    'Escalada técnica (5.5, 5.10…).'),
    ],
    subtitle: 'Naturaleza del terreno según la forma de progresar',
  ),
  _SS(
    'Escala Sadday — Nivel de riesgo y exigencia',
    [
      _SE('1', _C.verde,    'El riesgo que se asume es mínimo y no supondrá un imprevisto de gran importancia si se desencadena.', label: 'Mínimo'),
      _SE('2', _C.lima,     'El riesgo es bajo. Si el peligro se desencadena puede suponer un retraso importante en el horario.', label: 'Bajo'),
      _SE('3', _C.amarillo, 'El riesgo es moderado. Si el peligro se materializa probablemente la actividad no se finalizará según lo previsto.', label: 'Moderado'),
      _SE('4', _C.naranja,  'El riesgo es alto. Si el peligro se materializa puede haber daños personales importantes.', label: 'Alto'),
      _SE('5', _C.rojo,     'El riesgo es extremo. Si el peligro se desencadena la integridad física corre grave peligro.', label: 'Extremo'),
    ],
    subtitle: 'Escala propia del club',
  ),
  _SS(
    'Dificultad Senderismo',
    [
      _SE('Fácil',        _C.verde,   'Camino bien señalizado, terreno llano o con pendientes suaves. Apto para todos.'),
      _SE('Moderado',     _C.amarillo,'Terreno variado con algunas pendientes. Requiere buena condición física básica.'),
      _SE('Exigente',     _C.naranja, 'Pendientes pronunciadas, terreno irregular. Requiere experiencia y buena condición.'),
      _SE('Muy exigente', _C.rojo,    'Alta exigencia física y técnica. Solo para personas con experiencia consolidada.'),
    ],
    subtitle: 'Exigencia de rutas de Trekking',
  ),
  _SS(
    'Escala Técnica Ciclismo — S',
    [
      _SE('S0', _C.verde,   'Sin obstáculos técnicos. Terreno liso o compacto. Apto para cualquier ciclista.'),
      _SE('S1', _C.lima,    'Raíces y piedras pequeñas, inclinaciones leves. Velocidad moderada.'),
      _SE('S2', _C.amarillo,'Obstáculos naturales hasta 20 cm, terreno irregular. Requiere técnica básica de MTB.'),
      _SE('S3', _C.naranja, 'Grandes raíces, drops hasta 60 cm, alta velocidad. Requiere experiencia y buena técnica.'),
      _SE('S4', _C.rojo,    'Drops grandes, características técnicas complejas. Solo para ciclistas expertos.'),
    ],
    subtitle: 'IMBA trail difficulty rating — Ciclismo de montaña',
  ),
];

const _kActividades = <_Act>[
  _Act(
    icon: Icons.landscape,
    iconColor: Color(0xFFE07B39),
    iconBg: Color(0x26E07B39),
    title: 'Alpinismo',
    desc: 'Actividad que combina técnicas de escalada en roca, nieve y hielo para ascender cumbres de alta montaña. Requiere manejo de crampones, piolet y cuerda en ambientes de glaciar.',
    details: [
      _Det('Dificultad técnica', 'Escala IFAS, UIAA, WI, Compromiso'),
      _Det('Dificultad riesgo', 'Escala Sadday (nivel técnico y físico)'),
      _Det('Equipo', 'Crampones, piolet, cuerda, arnés, casco'),
      _Det('Nivel mínimo', 'Generalmente Semi-senior a Advanced'),
    ],
  ),
  _Act(
    icon: Icons.trending_up,
    iconColor: Color(0xFFE5534B),
    iconBg: Color(0x26E5534B),
    title: 'Escalada',
    desc: 'Ascenso por paredes de roca usando técnicas de progresión vertical con cuerda y equipamiento específico. Se divide en modalidades según el estilo de ascenso y el tipo de protecciones usadas.',
    details: [
      _Det('Deportiva', 'Chapas fijas en la pared. Énfasis en la dificultad técnica de los movimientos.'),
      _Det('Tradicional', 'El líder coloca y retira sus propias protecciones. Mayor compromiso.'),
      _Det('Mixta', 'Combina escalada en roca y en hielo/nieve en la misma vía.'),
      _Det('Boulder', 'Bloques de baja altura sin cuerda. Máxima dificultad en movimientos cortos.'),
    ],
  ),
  _Act(
    icon: Icons.directions_walk,
    iconColor: Color(0xFF4CAF8A),
    iconBg: Color(0x264CAF8A),
    title: 'Trekking',
    desc: 'Senderismo de montaña por caminos y senderos en terreno variado. No requiere técnicas de escalada, pero sí buena condición física y equipo adecuado según la altitud y duración.',
    details: [
      _Det('Dificultad', 'Escala propia de senderismo (Fácil → Muy exigente)'),
      _Det('Ruta circular', 'Salida y llegada al mismo punto. Más versátil logísticamente.'),
      _Det('Fuentes de agua', 'Indica si hay disponibilidad de agua en ruta. Clave para la planificación.'),
      _Det('Tipo de terreno', 'Sendero, quebrada, páramo, glaciar, bosque, etc.'),
    ],
  ),
  _Act(
    icon: Icons.pedal_bike,
    iconColor: AppColors.primary,
    iconBg: Color(0x266B7FD4),
    title: 'Ciclismo de Montaña',
    desc: 'Recorridos en bicicleta por caminos y senderos de montaña. Las características de la bicicleta y la dificultad técnica del terreno definen la exigencia de la salida.',
    details: [
      _Det('Rígida', 'Sin suspensión trasera. Liviana y eficiente en terreno suave.'),
      _Det('Doble suspensión', 'Suspensión delantera y trasera. Mayor comodidad y agarre en terreno técnico.'),
      _Det('Enduro', 'Diseñada para descensos técnicos con subidas largas.'),
      _Det('Gravel / Ruta', 'Aptas para caminos de tierra o asfalto. Menos técnicas pero más velocidad.'),
    ],
  ),
];

const _kNiveles = <_Niv>[
  _Niv(0, 'Externo', _C.gris, [
    'No es miembro del club.',
    'No se conocen sus capacidades técnicas ni físicas.',
  ]),
  _Niv(1, 'Principiante', _C.verde, [
    'Acaba de ingresar al club.',
    'Poca o nula experiencia comprobada en montaña.',
    'No puede guiar una salida.',
  ]),
  _Niv(2, 'Semi-senior', _C.amarillo, [
    'Varias participaciones en el club.',
    'Alguna experiencia en media montaña y alta montaña.',
    'Sabe hacer los nudos básicos y conoce las maniobras básicas de escalada.',
    'Puede guiar rutas de nivel Moderado.',
    'Puede ser puntero de cordada, pero no guía en alta montaña.',
    'No puede guiar rutas de nivel Alto o superior.',
  ]),
  _Niv(3, 'Senior', _C.naranja, [
    'Salidas a varias montañas, incluyendo alta montaña y escalada en roca.',
    'Puede ser puntero de cordada y guía.',
    'Tiene mucha experiencia en nudos y maniobras de escalada y alta montaña.',
    'Puede guiar rutas de nivel Alto.',
  ]),
  _Niv(4, 'Advanced', _C.rojo, [
    'Alta capacidad técnica y física.',
    'Puede guiar a otros en salidas de alta dificultad.',
  ]),
  _Niv(5, 'Expert', _C.morado, [
    'Muchos años de experiencia y gran capacidad física.',
    'Puede guiar en rutas desconocidas.',
    'Puede asumir cualquier rol de liderazgo en las salidas.',
  ]),
];

const _kEquipos = <_Eq>[
  _Eq(
    'Equipo Alta Montaña',
    'Para cumbres con nieve, hielo y glaciar. Implica técnicas de alpinismo.',
    _C.naranja,
    ['Crampones de 12 puntas', 'Piolet de montaña', 'Cuerda dinámica (40–60 m)',
     'Arnés de escalada', 'Casco de montaña', 'Gafas glaciar (UV)',
     'Ropa térmica y cortaviento', 'Polainas altas', 'Botas de alta montaña (doble bota o similar)'],
  ),
  _Eq(
    'Equipo Media Montaña',
    'Para salidas de trekking o cumbres sin glaciar. Sin técnicas de escalada.',
    _C.amarillo,
    ['Bastones de trekking', 'Botas de trekking impermeables',
     'Ropa en capas (base, intermedia, cortaviento)',
     'Mochila 30–50 L', 'Polainas bajas', 'Gafas de sol', 'Protector solar y labial'],
  ),
  _Eq(
    'Equipo Escalada en Roca',
    'Para vías de escalada deportiva, tradicional o boulder.',
    _C.rojo,
    ['Arnés de escalada', 'Pies de gato (zapatos de escalada)', 'Cuerda dinámica',
     'Cintas exprés y reuniones', 'Asegurador (Grigri, tubo, etc.)', 'Casco',
     'Magnesia y bolsa de magnesio'],
  ),
  _Eq(
    'Equipo Escalada en Hielo',
    'Para vías de hielo cascada o mixtas.',
    _C.verde,
    ['Crampones técnicos (puntas delanteras)', 'Dos piolets técnicos', 'Arnés',
     'Cuerda dinámica (50–60 m)', 'Casco', 'Guantes de escalada en hielo',
     'Tornillos de hielo y asegurador'],
  ),
  _Eq(
    'Equipo Selva / Cascadas',
    'Para salidas en ambientes selváticos, ríos y cascadas.',
    _C.lima,
    ['Ropa secado rápido', 'Botas de caucho o anfibias', 'Repelente de insectos',
     'Impermeable tipo poncho', 'Botiquín de primeros auxilios ampliado',
     'Purificador o pastillas de agua'],
  ),
  _Eq(
    'Sin Equipo Obligatorio',
    'Salidas que no requieren equipamiento técnico especializado.',
    _C.gris,
    ['Calzado cómodo (según terreno)', 'Ropa adecuada al clima',
     'Agua y alimentación', 'Protector solar', 'Mochila de día'],
  ),
];

// ─── Widgets compartidos ──────────────────────────────────────────────────────

class _GradeChip extends StatelessWidget {
  const _GradeChip(this.label, this.color);
  final String label;
  final _C color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.bg,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w700,
          color: color.fg,
        ),
      ),
    );
  }
}

// ─── Tab: Escalas ─────────────────────────────────────────────────────────────

class _ScaleSectionCard extends StatelessWidget {
  const _ScaleSectionCard(this.section);
  final _SS section;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(section.title, style: AppTextStyles.titleSmall),
            if (section.subtitle != null) ...[
              const SizedBox(height: 2),
              Text(section.subtitle!, style: AppTextStyles.bodySmall),
            ],
            const SizedBox(height: 10),
            for (var i = 0; i < section.entries.length; i++) ...[
              if (i > 0) const Divider(height: 1),
              _ScaleRowTile(section.entries[i]),
            ],
          ],
        ),
      ),
    );
  }
}

class _ScaleRowTile extends StatelessWidget {
  const _ScaleRowTile(this.entry);
  final _SE entry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _GradeChip(entry.badge, entry.color),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (entry.label != null && entry.label != '—') ...[
                  Text(
                    entry.label!,
                    style: AppTextStyles.bodyMedium.copyWith(fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 2),
                ],
                Text(
                  entry.desc,
                  style: AppTextStyles.bodySmall.copyWith(height: 1.4),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _EscalasTab extends StatelessWidget {
  const _EscalasTab();

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: _kSections.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (_, i) => _ScaleSectionCard(_kSections[i]),
    );
  }
}

// ─── Tab: Actividades ─────────────────────────────────────────────────────────

class _ActivityCard extends StatelessWidget {
  const _ActivityCard(this.act);
  final _Act act;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 14),
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: act.iconBg,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(act.icon, color: act.iconColor, size: 20),
                ),
                const SizedBox(width: 12),
                Text(act.title, style: AppTextStyles.titleSmall),
              ],
            ),
          ),
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(act.desc, style: AppTextStyles.bodySmall.copyWith(height: 1.5)),
                const SizedBox(height: 12),
                for (final d in act.details)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 6),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Icon(Icons.chevron_right, size: 16, color: AppColors.primary),
                        const SizedBox(width: 4),
                        Expanded(
                          child: Text.rich(
                            TextSpan(children: [
                              TextSpan(
                                text: '${d.label}: ',
                                style: AppTextStyles.bodySmall.copyWith(
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.foreground,
                                ),
                              ),
                              TextSpan(
                                text: d.value,
                                style: AppTextStyles.bodySmall.copyWith(height: 1.4),
                              ),
                            ]),
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ActividadesTab extends StatelessWidget {
  const _ActividadesTab();

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: _kActividades.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (_, i) => _ActivityCard(_kActividades[i]),
    );
  }
}

// ─── Tab: Niveles ─────────────────────────────────────────────────────────────

class _NivelCard extends StatelessWidget {
  const _NivelCard(this.niv);
  final _Niv niv;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: niv.color.bg,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Center(
                child: Text(
                  '${niv.nivel}',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: niv.color.fg,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(niv.nombre, style: AppTextStyles.titleSmall),
                  const SizedBox(height: 6),
                  for (final item in niv.items)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 4),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(Icons.chevron_right, size: 14, color: AppColors.mutedFg),
                          const SizedBox(width: 4),
                          Expanded(
                            child: Text(item, style: AppTextStyles.bodySmall.copyWith(height: 1.4)),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _NivelesTab extends StatelessWidget {
  const _NivelesTab();

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              'El club clasifica a sus socios en seis niveles de acuerdo a su experiencia y capacidad técnica. '
              'Este nivel determina qué rutas puede acceder cada socio y si puede asumir roles de liderazgo en las salidas. '
              'Los niveles los asigna la Secretaría o un Directivo desde el perfil del socio.',
              style: AppTextStyles.bodySmall.copyWith(height: 1.6),
            ),
          ),
        ),
        for (final n in _kNiveles) ...[
          const SizedBox(height: 12),
          _NivelCard(n),
        ],
      ],
    );
  }
}

// ─── Tab: Equipamiento ────────────────────────────────────────────────────────

class _EquipoTile extends StatelessWidget {
  const _EquipoTile(this.eq);
  final _Eq eq;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Theme(
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          tilePadding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
          childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          leading: Container(
            width: 8,
            decoration: BoxDecoration(
              color: eq.color.fg,
              borderRadius: BorderRadius.circular(4),
            ),
          ),
          title: Text(eq.nombre, style: AppTextStyles.titleSmall),
          subtitle: Padding(
            padding: const EdgeInsets.only(top: 2),
            child: Text(eq.desc, style: AppTextStyles.bodySmall.copyWith(height: 1.4)),
          ),
          iconColor: AppColors.mutedFg,
          collapsedIconColor: AppColors.mutedFg,
          children: [
            for (final item in eq.items)
              Padding(
                padding: const EdgeInsets.only(bottom: 6),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.chevron_right, size: 14, color: AppColors.primary),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(item, style: AppTextStyles.bodySmall.copyWith(height: 1.4)),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _EquipamientoTab extends StatelessWidget {
  const _EquipamientoTab();

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              'Cada ruta del club tiene asignado un tipo de equipamiento requerido. '
              'Esta clasificación orienta a los socios sobre qué deben llevar en función de las características técnicas de la salida. '
              'El detalle exacto puede variar según las condiciones del día y el criterio del Jefe de Salida.',
              style: AppTextStyles.bodySmall.copyWith(height: 1.6),
            ),
          ),
        ),
        for (final e in _kEquipos) ...[
          const SizedBox(height: 12),
          _EquipoTile(e),
        ],
      ],
    );
  }
}

// ─── Pantalla principal ───────────────────────────────────────────────────────

class TeoriaScreen extends StatelessWidget {
  const TeoriaScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 4,
      child: Scaffold(
        backgroundColor: AppColors.background,
        appBar: AppBar(
          title: const Text('Teoría'),
          bottom: const TabBar(
            isScrollable: true,
            tabAlignment: TabAlignment.start,
            tabs: [
              Tab(
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.layers_outlined, size: 16),
                    SizedBox(width: 6),
                    Text('Escalas'),
                  ],
                ),
              ),
              Tab(
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.terrain, size: 16),
                    SizedBox(width: 6),
                    Text('Actividades'),
                  ],
                ),
              ),
              Tab(
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.people_outline, size: 16),
                    SizedBox(width: 6),
                    Text('Niveles'),
                  ],
                ),
              ),
              Tab(
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.shopping_bag_outlined, size: 16),
                    SizedBox(width: 6),
                    Text('Equipo'),
                  ],
                ),
              ),
            ],
          ),
        ),
        body: const TabBarView(
          children: [
            _EscalasTab(),
            _ActividadesTab(),
            _NivelesTab(),
            _EquipamientoTab(),
          ],
        ),
      ),
    );
  }
}
