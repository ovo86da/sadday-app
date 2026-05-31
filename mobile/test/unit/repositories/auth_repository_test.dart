import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:sadday_app/core/auth/auth_provider.dart';
import 'package:sadday_app/core/auth/user_model.dart';
import 'package:sadday_app/features/auth/data/auth_remote_data_source.dart';
import 'package:sadday_app/features/auth/data/auth_repository.dart';
import 'package:sadday_app/features/auth/domain/models/auth_models.dart';

class MockAuthRemoteDataSource extends Mock implements AuthRemoteDataSource {}

class MockAuthNotifier extends Mock implements AuthNotifier {}

void main() {
  late MockAuthRemoteDataSource mockDs;
  late MockAuthNotifier mockNotifier;
  late AuthRepository repository;

  const testEmail = 'testuser';
  const testPassword = 'secret123';
  const testToken = 'eyJhbGciOiJIUzI1NiJ9.test.sig';

  final testUserJson = {
    'socioId': 'a1b2c3d4-0000-0000-0000-000000000001',
    'username': testEmail,
    'nombre': 'Test',
    'rol': 'SOCIO',
    'inhabilitado': false,
    'esJefeMontana': false,
    'passwordMustChange': false,
  };

  setUpAll(() {
    registerFallbackValue(UserModel.fromJson({
      'socioId': 'fallback-uuid',
      'username': 'x',
      'nombre': 'x',
      'rol': 'SOCIO',
    }));
  });

  setUp(() {
    mockDs = MockAuthRemoteDataSource();
    mockNotifier = MockAuthNotifier();
    repository = AuthRepository(
      dataSource: mockDs,
      authNotifier: mockNotifier,
    );
  });

  group('AuthRepository.login', () {
    test('calls setAuthenticated on LoginSuccess', () async {
      when(() => mockDs.login(testEmail, testPassword)).thenAnswer(
        (_) async =>
            LoginSuccess(accessToken: testToken, refreshToken: 'rt', userJson: testUserJson),
      );
      when(() => mockNotifier.setAuthenticated(any(), any()))
          .thenReturn(null);

      await repository.login(testEmail, testPassword);

      verify(() => mockDs.login(testEmail, testPassword)).called(1);
      verify(() => mockNotifier.setAuthenticated(testToken, any())).called(1);
    });

    test('calls setPendingMfa on LoginMfaRequired', () async {
      const challengeToken = 'challenge_abc';
      when(() => mockDs.login(testEmail, testPassword)).thenAnswer(
        (_) async =>
            const LoginMfaRequired(challengeToken: challengeToken),
      );
      when(() => mockNotifier.setPendingMfa(any())).thenReturn(null);

      await repository.login(testEmail, testPassword);

      verify(() => mockNotifier.setPendingMfa(challengeToken)).called(1);
    });

    test('calls setPendingCountryChallenge on LoginCountryChallengeRequired',
        () async {
      const ccToken = 'cc_token_xyz';
      when(() => mockDs.login(testEmail, testPassword)).thenAnswer(
        (_) async =>
            const LoginCountryChallengeRequired(token: ccToken),
      );
      when(() => mockNotifier.setPendingCountryChallenge(any()))
          .thenReturn(null);

      await repository.login(testEmail, testPassword);

      verify(() => mockNotifier.setPendingCountryChallenge(ccToken)).called(1);
    });

    test('propagates exception from data source', () async {
      when(() => mockDs.login(testEmail, testPassword))
          .thenThrow(Exception('network error'));

      expect(
        () => repository.login(testEmail, testPassword),
        throwsException,
      );
    });
  });

  group('AuthRepository.forgotPassword', () {
    test('delegates to data source', () async {
      when(() => mockDs.forgotPassword(testEmail)).thenAnswer((_) async {});

      await repository.forgotPassword(testEmail);

      verify(() => mockDs.forgotPassword(testEmail)).called(1);
    });
  });

  group('AuthRepository.verifyMfa', () {
    test('calls setAuthenticated on success', () async {
      when(() => mockDs.verifyMfa('challenge', '123456')).thenAnswer(
        (_) async =>
            LoginSuccess(accessToken: testToken, refreshToken: 'rt', userJson: testUserJson),
      );
      when(() => mockNotifier.setAuthenticated(any(), any()))
          .thenReturn(null);

      await repository.verifyMfa('challenge', '123456');

      verify(() => mockNotifier.setAuthenticated(testToken, any())).called(1);
    });
  });
}
