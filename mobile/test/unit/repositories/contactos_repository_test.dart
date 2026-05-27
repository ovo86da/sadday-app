import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:sadday_app/core/api/paged_response.dart';
import 'package:sadday_app/features/contactos/data/contactos_remote_data_source.dart';
import 'package:sadday_app/features/contactos/data/contactos_repository.dart';
import 'package:sadday_app/features/contactos/domain/models/contacto_model.dart';

class MockContactosRemoteDataSource extends Mock
    implements ContactosRemoteDataSource {}

void main() {
  late MockContactosRemoteDataSource mockDs;
  late ContactosRepository repository;

  setUp(() {
    mockDs = MockContactosRemoteDataSource();
    repository = ContactosRepository(mockDs);
  });

  final pagedEmpty = PagedResponse<Contacto>(
    items: [],
    totalElements: 0,
    totalPages: 0,
    currentPage: 0,
  );

  group('ContactosRepository.getContactos', () {
    test('returns empty list from data source', () async {
      when(() => mockDs.getContactos(page: 0))
          .thenAnswer((_) async => pagedEmpty);

      final result = await repository.getContactos(page: 0);

      expect(result.items, isEmpty);
    });

    test('passes query param', () async {
      when(() => mockDs.getContactos(page: 0, q: 'José'))
          .thenAnswer((_) async => pagedEmpty);

      await repository.getContactos(page: 0, q: 'José');

      verify(() => mockDs.getContactos(page: 0, q: 'José')).called(1);
    });
  });

  group('ContactosRepository.crear', () {
    test('delegates to data source', () async {
      final data = {'nombre': 'Nuevo', 'tipo': 'GUIA'};
      when(() => mockDs.crear(data)).thenAnswer((_) async {});

      await repository.crear(data);

      verify(() => mockDs.crear(data)).called(1);
    });
  });

  group('ContactosRepository.editar', () {
    test('delegates with id to data source', () async {
      final data = {'nombre': 'Editado'};
      when(() => mockDs.editar(3, data)).thenAnswer((_) async {});

      await repository.editar(3, data);

      verify(() => mockDs.editar(3, data)).called(1);
    });
  });

  group('ContactosRepository.eliminar', () {
    test('delegates to data source', () async {
      when(() => mockDs.eliminar(7)).thenAnswer((_) async {});

      await repository.eliminar(7);

      verify(() => mockDs.eliminar(7)).called(1);
    });
  });
}
