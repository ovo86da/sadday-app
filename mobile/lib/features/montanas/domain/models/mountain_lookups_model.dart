class EscalaAlpina {
  const EscalaAlpina({required this.id, required this.grado, required this.nombre});
  final String id;
  final String grado;
  final String nombre;
  factory EscalaAlpina.fromJson(Map<String, dynamic> j) =>
      EscalaAlpina(id: j['id'] as String, grado: j['grado'] as String, nombre: j['nombre'] as String);
}

class DificultadRoca {
  const DificultadRoca({required this.id, required this.uiaa, required this.francesa});
  final String id;
  final String uiaa;
  final String francesa;
  factory DificultadRoca.fromJson(Map<String, dynamic> j) =>
      DificultadRoca(id: j['id'] as String, uiaa: j['uiaa'] as String, francesa: j['francesa'] as String);
}

class DificultadHielo {
  const DificultadHielo({required this.id, required this.grado});
  final String id;
  final String grado;
  factory DificultadHielo.fromJson(Map<String, dynamic> j) =>
      DificultadHielo(id: j['id'] as String, grado: j['grado'] as String);
}

class Compromiso {
  const Compromiso({required this.id, required this.tipo});
  final String id;
  final String tipo;
  factory Compromiso.fromJson(Map<String, dynamic> j) =>
      Compromiso(id: j['id'] as String, tipo: j['tipo'] as String);
}

class YosemiteClase {
  const YosemiteClase({required this.id, required this.tipo});
  final String id;
  final String tipo;
  factory YosemiteClase.fromJson(Map<String, dynamic> j) =>
      YosemiteClase(id: j['id'] as String, tipo: j['tipo'] as String);
}

class SaddayRiesgo {
  const SaddayRiesgo({required this.id, required this.escala});
  final String id;
  final String escala;
  factory SaddayRiesgo.fromJson(Map<String, dynamic> j) =>
      SaddayRiesgo(id: j['id'] as String, escala: j['escala'] as String);
}

class EquipoMontana {
  const EquipoMontana({required this.id, required this.nombre});
  final int id;
  final String nombre;
  factory EquipoMontana.fromJson(Map<String, dynamic> j) =>
      EquipoMontana(id: j['id'] as int, nombre: j['nombre'] as String);
}

class DificultadSenderismo {
  const DificultadSenderismo({required this.id, required this.nombre});
  final String id;
  final String nombre;
  factory DificultadSenderismo.fromJson(Map<String, dynamic> j) =>
      DificultadSenderismo(id: j['id'] as String, nombre: j['nombre'] as String);
}

class MountainLookups {
  const MountainLookups({
    required this.escalasAlpina,
    required this.dificultadesRoca,
    required this.dificultadesHielo,
    required this.compromisos,
    required this.yosemiteClases,
    required this.saddayRiesgos,
    required this.equipos,
    required this.dificultadesSenderismo,
  });
  final List<EscalaAlpina> escalasAlpina;
  final List<DificultadRoca> dificultadesRoca;
  final List<DificultadHielo> dificultadesHielo;
  final List<Compromiso> compromisos;
  final List<YosemiteClase> yosemiteClases;
  final List<SaddayRiesgo> saddayRiesgos;
  final List<EquipoMontana> equipos;
  final List<DificultadSenderismo> dificultadesSenderismo;

  factory MountainLookups.fromJson(Map<String, dynamic> j) => MountainLookups(
        escalasAlpina: (j['escalasAlpina'] as List).map((e) => EscalaAlpina.fromJson(e as Map<String, dynamic>)).toList(),
        dificultadesRoca: (j['dificultadesRoca'] as List).map((e) => DificultadRoca.fromJson(e as Map<String, dynamic>)).toList(),
        dificultadesHielo: (j['dificultadesHielo'] as List).map((e) => DificultadHielo.fromJson(e as Map<String, dynamic>)).toList(),
        compromisos: (j['compromisos'] as List).map((e) => Compromiso.fromJson(e as Map<String, dynamic>)).toList(),
        yosemiteClases: (j['yosemiteClases'] as List).map((e) => YosemiteClase.fromJson(e as Map<String, dynamic>)).toList(),
        saddayRiesgos: (j['saddayRiesgos'] as List).map((e) => SaddayRiesgo.fromJson(e as Map<String, dynamic>)).toList(),
        equipos: (j['equipos'] as List).map((e) => EquipoMontana.fromJson(e as Map<String, dynamic>)).toList(),
        dificultadesSenderismo: (j['dificultadesSenderismo'] as List).map((e) => DificultadSenderismo.fromJson(e as Map<String, dynamic>)).toList(),
      );
}
