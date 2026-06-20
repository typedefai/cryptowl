import 'package:cryptowl/src/components/setting_dialog.dart';
import 'package:cryptowl/src/providers/settings.dart';
import 'package:flutter/material.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

enum ThemeOptions { system, dark, light }

class SettingsPage extends HookConsumerWidget {
  const SettingsPage({super.key});

  static const String path = '/settings';
  static const String name = 'Settings';

  static const borderStyle = Border(
    bottom: BorderSide(
      color: Color.fromARGB(255, 233, 231, 231),
    ),
  );

  Widget _renderMenu(BuildContext context, String title,
      {void Function()? onTap, Widget? trailing}) {
    return ListTile(
      dense: true,
      contentPadding: const EdgeInsets.only(left: 10, right: 10),
      title: Text(title),
      trailing: trailing ?? Icon(Icons.navigate_next),
      onTap: onTap,
      shape: borderStyle,
    );
  }

  Future<ThemeMode?> _showThemeSelection(BuildContext context) async {
    return showDialog<ThemeMode>(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) {
        return SettingDialog<ThemeMode>(
            "Theme", ThemeMode.values, ThemeMode.system);
      },
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeNotifier = ref.watch(themeProvider.notifier);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(8),
        children: <Widget>[
          _renderMenu(context, "Storage"),
          _renderMenu(context, "Language", trailing: Text("English")),
          _renderMenu(context, "Theme", trailing: Text("Default(System)"),
              onTap: () async {
            final mode = await _showThemeSelection(context);
            if (mode != null) {
              themeNotifier.setTheme(mode);
            }
          }),
          _renderMenu(context, "Change master password"),
          _renderMenu(context, "Unlock with Biometrics"),
          _renderMenu(context, "Session timeout"),
          _renderMenu(context, "Clear clipboard"),
          _renderMenu(context, "Allow screen capture"),
        ],
      ),
    );
  }
}
