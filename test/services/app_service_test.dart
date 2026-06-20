import 'package:cryptowl/src/service/app_service.dart';
import 'package:cryptowl/src/service/config_service.dart';
import 'package:cryptowl/src/service/file_service.dart';
import 'package:cryptowl/src/service/kdf_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

@GenerateNiceMocks([
  MockSpec<FileService>(),
  MockSpec<KdfService>(),
  MockSpec<ConfigService>()
])
import 'app_service_test.mocks.dart';

const String kTemporaryPath = 'temporaryPath';
const String kApplicationSupportPath = 'applicationSupportPath';
const String kDownloadsPath = 'downloadsPath';
const String kLibraryPath = 'libraryPath';
const String kApplicationDocumentsPath = 'applicationDocumentsPath';
const String kExternalCachePath = 'externalCachePath';
const String kExternalStoragePath = 'externalStoragePath';

/// see: https://stackoverflow.com/questions/62597011/mock-getexternalstoragedirectory-on-flutter
class MockPathProviderPlatform extends Mock
    with MockPlatformInterfaceMixin
    implements PathProviderPlatform {
  @override
  Future<String> getTemporaryPath() async {
    return kTemporaryPath;
  }

  @override
  Future<String> getApplicationSupportPath() async {
    return kApplicationSupportPath;
  }

  @override
  Future<String> getLibraryPath() async {
    return kLibraryPath;
  }

  @override
  Future<String> getApplicationDocumentsPath() async {
    return kApplicationDocumentsPath;
  }

  @override
  Future<String> getExternalStoragePath() async {
    return kExternalStoragePath;
  }

  @override
  Future<List<String>> getExternalCachePaths() async {
    return <String>[kExternalCachePath];
  }

  @override
  Future<List<String>> getExternalStoragePaths({
    StorageDirectory? type,
  }) async {
    return <String>[kExternalStoragePath];
  }

  @override
  Future<String> getDownloadsPath() async {
    return kDownloadsPath;
  }
}

void main() {
  final mockFileService = MockFileService();
  final mockKdfService = MockKdfService();
  final mockConfigService = MockConfigService();

  final service =
      AppService(mockFileService, mockKdfService, mockConfigService);

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    PathProviderPlatform.instance = MockPathProviderPlatform();
  });

  group("check app initialization", () {
    test('should return false if .enc file not exists', () async {
      when(mockFileService.getSqlcipherInstances())
          .thenAnswer((_) async => List.empty());
      final initialized = await service.isInitialized();

      expect(initialized, false);
    });

    test('should return true if .enc file exists', () async {
      when(mockFileService.getSqlcipherInstances())
          .thenAnswer((_) async => List.of(["1.enc"]));
      final initialized = await service.isInitialized();

      expect(initialized, true);
    });
  });
}
