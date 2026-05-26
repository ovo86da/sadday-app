import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:sadday_app/core/api/paged_response.dart';
import 'package:sadday_app/features/socios/data/socios_remote_data_source.dart';
import 'package:sadday_app/features/socios/data/socios_repository.dart';
import 'package:sadday_app/features/socios/domain/models/socio_model.dart';

class MockSociosRemoteDataSource extends Mock
    implements SociosRemoteDataSource {}

const _testSocioId = 'b1c2d3e4-0000-0000-0000-000000000010';

final _socioJson = {
  'id': _testSocioId,
  'nombre': 'María',
  'apellido': 'López',
  'correo': 'maria@sadday.com',
  'rolSistema': 'SOCIO',
  'estadoHabilitacion': 'HABILITADO',
  'tipoSocio': 'ACTIVO',
  'esJefeMontana': false,
};

void main() {
  late MockSociosRemoteDataSource mockDs;
  late SociosRepository repository;

  setUp(() {
    mockDs = MockSociosRemoteDataSource();
    repository = SociosRepository(mockDs);
  });

  group('SociosRepository.getSocios', () {
    test('returns paged response from data source', () async {
      final paged = PagedResponse<Socio>(
        items: [Socio.fromJson(_socioJson)],
        totalElements: 1,
        totalPages: 1,
        currentPage: 0,
      );
      when(() => mockDs.getSocios(page: 0))
          .thenAnswer((_) async => paged);

      final result = await repository.getSocios(page: 0);

      expect(result.items, hasLength(1));
      expect(result.items.first.nombre, 'María');
    });

    test('passes lookup IDs to data source', () async {
      final paged = PagedResponse<Socio>(
        items: [],
        totalElements: 0,
        totalPages: 0,
        currentPage: 0,
      );
      when(() => mockDs.getSocios(
            page: 0,
            rolId: 1,
            estadoId: 2,
          )).thenAnswer((_) async => paged);

      await repository.getSocios(page: 0, rolId: 1, estadoId: 2);

      verify(() => mockDs.getSocios(
            page: 0,
            rolId: 1,
            estadoId: 2,
          )).called(1);
    });
  });

  group('SociosRepository.habilitar', () {
    test('delegates to data source', () async {
      when(() => mockDs.habilitar(_testSocioId)).thenAnswer((_) async {});

      await repository.habilitar(_testSocioId);

      verify(() => mockDs.habilitar(_testSocioId)).called(1);
    });
  });

  group('SociosRepository.inhabilitar', () {
    test('delegates to data source', () async {
      when(() => mockDs.inhabilitar(_testSocioId)).thenAnswer((_) async {});

      await repository.inhabilitar(_testSocioId);

      verify(() => mockDs.inhabilitar(_testSocioId)).called(1);
    });
  });

  group('SociosRepository.cambiarRol', () {
    test('delegates to data source with rolSistemaId', () async {
      when(() => mockDs.cambiarRol(_testSocioId, 3))
          .thenAnswer((_) async {});

      await repository.cambiarRol(_testSocioId, 3);

      verify(() => mockDs.cambiarRol(_testSocioId, 3)).called(1);
    });
  });

  group('SociosRepository.eliminarSocio', () {
    test('delegates to data source', () async {
      when(() => mockDs.eliminarSocio(_testSocioId)).thenAnswer((_) async {});

      await repository.eliminarSocio(_testSocioId);

      verify(() => mockDs.eliminarSocio(_testSocioId)).called(1);
    });
  });
}
