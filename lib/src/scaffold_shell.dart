// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'package:flutter/material.dart';
import 'package:flutter_adaptive_scaffold/flutter_adaptive_scaffold.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_svg/svg.dart';
import 'package:go_router/go_router.dart';
import 'package:remixicon/remixicon.dart';
import 'package:vector_graphics/vector_graphics.dart';

import 'localization/app_localizations.dart';
import 'pages/notes_page.dart';
import 'pages/passwords.dart';
import 'pages/photos_page.dart';
import 'pages/settings_page.dart';
import 'providers/providers.dart';

/// The [ScaffoldShell] is a [StatelessWidget] that uses the [AdaptiveScaffold]
/// to create a shell for the application.
class ScaffoldShell extends ConsumerWidget {
  /// Create a new instance of [AppScaffoldShell]
  const ScaffoldShell({
    required this.navigationShell,
    super.key,
  });

  /// The navigation shell to use with the navigation.
  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final loginNotifier = ref.watch(asyncLoginProvider.notifier);

    return AdaptiveScaffold(
      useDrawer: false,
      internalAnimations: false,
      leadingExtendedNavRail: Padding(
        padding: EdgeInsetsGeometry.all(16),
        child: Column(
          children: [
            Padding(
              padding: EdgeInsetsGeometry.only(bottom: 10),
              child: Row(
                children: [
                  Expanded(
                      child: Row(
                    children: [
                      SvgPicture(
                        AssetBytesLoader("assets/images/cryptowl.svg.vec"),
                        height: 40,
                      ),
                      Text(AppLocalizations.of(context)!.appTitle),
                    ],
                  )),
                  IconButton(
                    onPressed: () {},
                    icon: Icon(RemixIcons.user_settings_line),
                    tooltip: "User settings",
                  )
                ],
              ),
            ),
            Divider(
                height: 1,
                thickness: 1,
                color: Theme.of(context).colorScheme.outlineVariant),
          ],
        ),
      ),
      leadingUnextendedNavRail: IconButton(
        onPressed: () {},
        icon: Icon(RemixIcons.user_settings_line),
        tooltip: "User settings",
      ),
      trailingNavRail: IconButton(
        onPressed: () async {
          await loginNotifier.logout();
        },
        icon: Icon(RemixIcons.logout_circle_r_line,
            color: Theme.of(context).colorScheme.error),
        tooltip: "Logout",
      ),
      body: (BuildContext context) => navigationShell,
      selectedIndex: navigationShell.currentIndex,
      onSelectedIndexChange: (int index) {
        navigationShell.goBranch(
          index,
          initialLocation: index == navigationShell.currentIndex,
        );
      },
      destinations: navigationShell.route.branches.map(
        (StatefulShellBranch e) {
          return switch (e.defaultRoute?.name) {
            SettingsPage.name => NavigationDestination(
                icon: Icon(RemixIcons.camera_lens_ai_line),
                label: AppLocalizations.of(context)!.moments),
            PhotosPage.name => NavigationDestination(
                icon: Icon(RemixIcons.camera_ai_line),
                label: AppLocalizations.of(context)!.photos),
            NotesPage.name => NavigationDestination(
                icon: Icon(RemixIcons.list_check_3),
                label: AppLocalizations.of(context)!.notes),
            PasswordsPage.name => NavigationDestination(
                icon: Icon(RemixIcons.shield_keyhole_line),
                label: AppLocalizations.of(context)!.passwords),
            _ => throw UnimplementedError(
                'The route ${e.defaultRoute?.name} is not implemented.',
              ),
          };
        },
      ).toList(),
    );
  }
}
