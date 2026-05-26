import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/features/contactos/domain/models/contacto_model.dart';

void main() {
  group('Contacto.fromJson', () {
    test('parses all fields', () {
      final c = Contacto.fromJson({
        'id': 1,
        'nombre': 'José Transportes',
        'telefono': '0991234567',
        'email': 'jose@trans.com',
        'tipo': 'TRANSPORTE',
        'notas': 'Furgoneta de 15 pax',
      });
      expect(c.id, 1);
      expect(c.nombre, 'José Transportes');
      expect(c.telefono, '0991234567');
      expect(c.email, 'jose@trans.com');
      expect(c.tipo, 'TRANSPORTE');
      expect(c.notas, 'Furgoneta de 15 pax');
    });

    test('optional fields default to null', () {
      final c = Contacto.fromJson({'id': 2, 'nombre': 'Guía', 'tipo': 'GUIA'});
      expect(c.telefono, isNull);
      expect(c.email, isNull);
      expect(c.notas, isNull);
    });

    test('tipo defaults to OTRO when absent', () {
      final c =
          Contacto.fromJson({'id': 3, 'nombre': 'Refugio Los Pinos'});
      expect(c.tipo, 'OTRO');
    });
  });
}
