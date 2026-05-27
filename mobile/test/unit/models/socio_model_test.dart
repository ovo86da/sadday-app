import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/features/socios/domain/models/socio_model.dart';

void main() {
  group('Socio.fromJson', () {
    const baseJson = {
      'id': 'a1b2c3d4-0000-0000-0000-000000000001',
      'nombre': 'Ana',
      'apellido': 'Torres',
      'correo': 'ana@example.com',
      'rolSistema': 'SOCIO',
      'estadoHabilitacion': 'HABILITADO',
      'tipoSocio': 'ACTIVO',
      'esJefeMontana': false,
    };

    test('parses required fields', () {
      final socio = Socio.fromJson(baseJson);
      expect(socio.id, 'a1b2c3d4-0000-0000-0000-000000000001');
      expect(socio.nombre, 'Ana');
      expect(socio.apellido, 'Torres');
      expect(socio.correo, 'ana@example.com');
      expect(socio.rol, 'SOCIO');
      expect(socio.estadoHabilitacion, 'HABILITADO');
      expect(socio.esJefeMontana, isFalse);
    });

    test('nombreCompleto concatenates nombre + apellido', () {
      final socio = Socio.fromJson(baseJson);
      expect(socio.nombreCompleto, 'Ana Torres');
    });

    test('initials returns first letters uppercased', () {
      final socio = Socio.fromJson(baseJson);
      expect(socio.initials, 'AT');
    });

    test('optional fields are null when absent', () {
      final socio = Socio.fromJson(baseJson);
      expect(socio.cedula, isNull);
      expect(socio.telefono, isNull);
      expect(socio.nivelTecnico, isNull);
    });

    test('falls back to "rol" key when "rolSistema" is absent', () {
      final json = {...baseJson};
      json.remove('rolSistema');
      json['rol'] = 'ADMIN';
      final socio = Socio.fromJson(json);
      expect(socio.rol, 'ADMIN');
    });

    test('handles empty initials gracefully', () {
      final json = {...baseJson, 'nombre': '', 'apellido': ''};
      final socio = Socio.fromJson(json);
      expect(socio.initials, '');
    });
  });

  group('HabilitacionLogEntry.fromJson', () {
    test('parses backend-shape fields', () {
      final entry = HabilitacionLogEntry.fromJson({
        'id': 10,
        'estadoAnterior': 'HABILITADO',
        'estadoNuevo': 'INHABILITADO',
        'cambiadoPorNombre': 'Secretaria Demo',
        'cambiadoEn': '2025-03-15T10:00:00Z',
        'notas': 'Mora en cuotas',
      });
      expect(entry.id, 10);
      expect(entry.estado, 'INHABILITADO');
      expect(entry.estadoAnterior, 'HABILITADO');
      expect(entry.motivo, 'Mora en cuotas');
      expect(entry.actor, 'Secretaria Demo');
      expect(entry.fecha.year, 2025);
    });

    test('notas (motivo) is nullable', () {
      final entry = HabilitacionLogEntry.fromJson({
        'id': 1,
        'estadoNuevo': 'HABILITADO',
        'cambiadoPorNombre': 'Admin',
        'cambiadoEn': '2025-01-01T00:00:00Z',
      });
      expect(entry.motivo, isNull);
    });
  });

  group('Cuota.fromJson', () {
    test('parses paid cuota (estado=PAGADA)', () {
      final cuota = Cuota.fromJson({
        'id': 5,
        'valor': 25.0,
        'fecha': '2025-01-10',
        'estado': 'PAGADA',
      });
      expect(cuota.pagado, isTrue);
      expect(cuota.valor, 25.0);
      expect(cuota.monto, 25.0);
      expect(cuota.fechaPago, isNotNull);
    });

    test('parses pending cuota (no fechaPago when not paid)', () {
      final cuota = Cuota.fromJson({
        'id': 6,
        'valor': 25.0,
        'fecha': '2025-02-01',
        'estado': 'PENDIENTE',
      });
      expect(cuota.pagado, isFalse);
      expect(cuota.fechaPago, isNull);
    });
  });
}
