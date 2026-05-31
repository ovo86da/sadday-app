enum UserRole { socio, directivo, secretaria, admin }

class UserModel {
  const UserModel({
    required this.socioId,
    required this.username,
    required this.nombre,
    required this.rol,
    this.nivelTecnico,
    this.inhabilitado = false,
    this.esJefeMontana = false,
    this.esPresidenta = false,
    this.esJefeSalidaActivo = false,
    this.passwordMustChange = false,
  });

  final String socioId;
  final String username;
  final String nombre;
  final UserRole rol;
  final String? nivelTecnico;
  final bool inhabilitado;
  final bool esJefeMontana;
  final bool esPresidenta;
  /// True si el usuario es Jefe de Salida en alguna salida activa al momento del login.
  final bool esJefeSalidaActivo;
  final bool passwordMustChange;

  bool get puedeVerNotificaciones =>
      rol == UserRole.admin ||
      rol == UserRole.secretaria ||
      rol == UserRole.directivo ||
      esJefeSalidaActivo;

  factory UserModel.fromJson(Map<String, dynamic> json) => UserModel(
    socioId: json['socioId']?.toString() ?? '',
    username: json['username'] as String? ?? '',
    nombre: json['nombre'] as String? ?? '',
    rol: UserRole.values.firstWhere(
      (r) => r.name.toUpperCase() == ((json['rol'] as String?) ?? '').toUpperCase(),
      orElse: () => UserRole.socio,
    ),
    nivelTecnico: json['nivelTecnico'] as String?,
    inhabilitado: json['inhabilitado'] as bool? ?? false,
    esJefeMontana: json['esJefeMontana'] as bool? ?? false,
    esPresidenta: json['esPresidenta'] as bool? ?? false,
    esJefeSalidaActivo: json['esJefeSalidaActivo'] as bool? ?? false,
    passwordMustChange: json['passwordMustChange'] as bool? ?? false,
  );
}
