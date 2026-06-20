import 'package:package_info_plus/package_info_plus.dart';

class VersionService {
  Future<String> getPackageVersion() async {
    final PackageInfo packageInfo = await PackageInfo.fromPlatform();
    return packageInfo.version;
  }
}
