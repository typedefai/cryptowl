import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:vector_graphics/vector_graphics.dart';

import '../providers/providers.dart';

const infoTextStyle = TextStyle(fontSize: 10, color: Colors.white);

final packageInfoProvider = FutureProvider<PackageInfo>((ref) async {
  return PackageInfo.fromPlatform();
});

class VersionInfo extends ConsumerWidget {
  const VersionInfo({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final packageInfo = ref.watch(packageInfoProvider);

    return packageInfo.when(
      loading: () => const CircularProgressIndicator(),
      error: (error, stack) => Text("$error"),
      data: (info) => Text(info.version, style: infoTextStyle),
    );
  }
}

class MenuDrawer extends ConsumerWidget {
  const MenuDrawer({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final loginNotifier = ref.read(asyncLoginProvider.notifier);
    return Drawer(
      child: ListView(
        padding: EdgeInsets.zero,
        children: [
          const DrawerHeader(
            decoration: BoxDecoration(color: Colors.deepPurple),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    SvgPicture(
                      AssetBytesLoader(
                          "assets/images/cryptowl-full-dark.svg.vec"),
                      height: 40,
                    ),
                    VersionInfo(),
                  ],
                ),
                SizedBox(height: 20),
                Text(
                  "© by Riguz Lee, 2024-2025",
                  style: infoTextStyle,
                ),
                SizedBox(height: 10),
                Text(
                  "https://github.com/quillgen/cryptowl",
                  style: infoTextStyle,
                )
              ],
            ),
          ),
          ListTile(
            title: const Text('Backup'),
          ),
          ListTile(
            title: const Text('About'),
            onTap: () => _dialogBuilder(context),
          ),
          ListTile(
            title: const Text('Lock'),
            onTap: () async => loginNotifier.logout(),
          ),
          ListTile(
            title: const Text('Exit'),
          ),
        ],
      ),
    );
  }

  Future<void> _dialogBuilder(BuildContext context) {
    return showDialog<void>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Cryptowl'),
          content: const Text(
            'Cryptowl is a password manager,\n'
            'all your data is encrypted on local device.\n'
            'It is free opensource software,\n'
            'may you enjoy it!',
          ),
          actions: <Widget>[
            TextButton(
              style: TextButton.styleFrom(
                  textStyle: Theme.of(context).textTheme.labelLarge),
              child: const Text('Dismiss'),
              onPressed: () {
                Navigator.of(context).pop();
              },
            )
          ],
        );
      },
    );
  }
}
