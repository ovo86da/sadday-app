import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:sadday_app/core/api/paged_response.dart';
import 'package:sadday_app/features/salidas/data/salidas_remote_data_source.dart';
import 'package:sadday_app/features/salidas/data/salidas_repository.dart';
import 'package:sadday_app/features/salidas/domain/models/salida_model.dart';

class MockSalidasRemoteDataSource extends Mock
    implements SalidasRemoteDataSource {}

const _testSalidaId = 'a1b2c3d4-0000-0000-0000-000000000001';

final _salidaJson = {
  'id': _testSalidaId,
  'nombre': 'Cerro Pichincha',
  'estado': 'PLANIFICADA',
  'fechaInicio': '2025-07-10',
  'capacidadMaxima': 15,
  'totalInscritos': 3,
};

final _emptySalidas = PagedResponse<Salida>(
  items: [],
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
);

final _oneSalida = PagedResponse<Salida>(
  items: [Salida.fromJson(_salidaJson)],
  totalElements: 1,
  totalPages: 1,
  currentPage: 0,
);

void main() {
  late MockSalidasRemoteDataSource mockDs;
  late SalidasRepository repository;

  setUp(() {
    mockDs = MockSalidasRemoteDataSource();
    repository = SalidasRepository(mockDs);
  });

  group('SalidasRepository.getSalidas', () {
    test('returns paged response from data source', () async {
      when(() => mockDs.getSalidas(page: 0))
          .thenAnswer((_) async => _oneSalida);

      final result = await repository.getSalidas(page: 0);

      expect(result.items, hasLength(1));
      expect(result.items.first.nombre, 'Cerro Pichincha');
    });

    test('passes estado filter to data source', () async {
      when(() =>
              mockDs.getSalidas(page: 0, estado: 'PLANIFICADA'))
          .thenAnswer((_) async => _oneSalida);

      final result =
          await repository.getSalidas(page: 0, estado: 'PLANIFICADA');

      expect(result.items, hasLength(1));
      verify(() => mockDs.getSalidas(page: 0, estado: 'PLANIFICADA'))
          .called(1);
    });

    test('returns empty list when no salidas', () async {
      when(() => mockDs.getSalidas(page: 0))
          .thenAnswer((_) async => _emptySalidas);

      final result = await repository.getSalidas(page: 0);

      expect(result.items, isEmpty);
    });
  });

  group('SalidasRepository.crearSalida', () {
    test('delegates to data source', () async {
      final data = {'nombre': 'Nueva salida', 'nivelMinimo': 'BASICO'};
      when(() => mockDs.crearSalida(data)).thenAnswer((_) async {});

      await repository.crearSalida(data);

      verify(() => mockDs.crearSalida(data)).called(1);
    });
  });

  group('SalidasRepository.editarSalida', () {
    test('delegates to data source with id', () async {
      final data = {'nombre': 'Actualizada'};
      when(() => mockDs.editarSalida(_testSalidaId, data))
          .thenAnswer((_) async {});

      await repository.editarSalida(_testSalidaId, data);

      verify(() => mockDs.editarSalida(_testSalidaId, data)).called(1);
    });
  });
}
