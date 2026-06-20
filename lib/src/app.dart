import 'package:cryptowl/src/pages/note_detail_page.dart';
import 'package:cryptowl/src/pages/note_edit_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:responsive_framework/responsive_framework.dart';

import '../main.dart';
import 'app_scaffold.dart';
import 'localization/app_localizations.dart';
import 'pages/introduction_page.dart';
import 'pages/login_page.dart';
import 'pages/note_create_page.dart';
import 'pages/notes_page.dart';
import 'pages/onboarding_page.dart';
import 'pages/password_create_page.dart';
import 'pages/password_detail_page.dart';
import 'pages/password_edit_page.dart';
import 'pages/passwords.dart';
import 'pages/photos_page.dart';
import 'pages/settings_page.dart';
import 'pages/splash_page.dart';
import 'providers/providers.dart';
import 'scaffold_shell.dart';
import 'theme.dart';

final GlobalKey<NavigatorState> rootNavigatorKey =
    GlobalKey<NavigatorState>(debugLabel: 'root');

final GlobalKey<NavigatorState> diaryNavigatorKey =
    GlobalKey<NavigatorState>(debugLabel: 'diary');
final GlobalKey<NavigatorState> photosNavigatorKey =
    GlobalKey<NavigatorState>(debugLabel: 'photos');
final GlobalKey<NavigatorState> notesNavigatorKey =
    GlobalKey<NavigatorState>(debugLabel: 'notes');
final GlobalKey<NavigatorState> passwordsNavigatorKey =
    GlobalKey<NavigatorState>(debugLabel: 'passwords');

final routerProvider = Provider<GoRouter>((ref) {
  final onboardingState = ref.watch(onboardingProvider);
  final loginState = ref.watch(asyncLoginProvider);
  logger.fine(
      "Router rebuilding ---> onboardingState=$onboardingState loginState=$loginState");

  final GoRoute unauthenticatedRoutes = GoRoute(
    name: LoginPage.name,
    path: LoginPage.path,
    pageBuilder: (BuildContext context, GoRouterState state) {
      return const MaterialPage<void>(child: LoginPage());
    },
    routes: <RouteBase>[
      GoRoute(
        name: OnboardingPage.name,
        path: OnboardingPage.path,
        pageBuilder: (BuildContext context, GoRouterState state) {
          return const MaterialPage<void>(
            child: OnboardingPage(),
          );
        },
      ),
    ],
  );

  final StatefulShellRoute authenticatedRoutes =
      StatefulShellRoute.indexedStack(
    parentNavigatorKey: rootNavigatorKey,
    builder: (
      BuildContext context,
      GoRouterState state,
      StatefulNavigationShell navigationShell,
    ) {
      return ScaffoldShell(navigationShell: navigationShell);
    },
    redirect: (BuildContext context, GoRouterState state) {
      if (loginState.unwrapPrevious().valueOrNull == null) {
        return LoginPage.path;
      }
      return null;
    },
    branches: <StatefulShellBranch>[
      StatefulShellBranch(
        navigatorKey: diaryNavigatorKey,
        routes: <RouteBase>[
          GoRoute(
            name: SettingsPage.name,
            path: SettingsPage.path,
            pageBuilder: (BuildContext context, GoRouterState state) {
              return const NoTransitionPage<void>(
                key: ValueKey<String>(SettingsPage.name),
                child: SettingsPage(),
              );
            },
            routes: <RouteBase>[],
          ),
        ],
      ),
      StatefulShellBranch(
        navigatorKey: photosNavigatorKey,
        routes: <RouteBase>[
          GoRoute(
            name: PhotosPage.name,
            path: PhotosPage.path,
            pageBuilder: (BuildContext context, GoRouterState state) {
              return const NoTransitionPage<void>(child: PhotosPage());
            },
          ),
        ],
      ),
      StatefulShellBranch(
        navigatorKey: notesNavigatorKey,
        routes: <RouteBase>[
          GoRoute(
              name: NotesPage.name,
              path: NotesPage.path,
              pageBuilder: (BuildContext context, GoRouterState state) {
                return const NoTransitionPage<void>(
                    child: AppScaffold(
                  body: NotesPage(),
                ));
              },
              routes: <RouteBase>[
                GoRoute(
                  name: NoteCreatePage.name,
                  path: NoteCreatePage.path,
                  parentNavigatorKey: notesNavigatorKey,
                  pageBuilder: (BuildContext context, GoRouterState state) =>
                      NoTransitionPage(
                    child: AppScaffold(
                      body: NotesPage(),
                      secondaryBody: NoteCreatePage(),
                    ),
                  ),
                ),
                GoRoute(
                  name: NoteDetailPage.name,
                  path: NoteDetailPage.path,
                  parentNavigatorKey: notesNavigatorKey,
                  pageBuilder: (BuildContext context, GoRouterState state) =>
                      NoTransitionPage(
                    child: AppScaffold(
                      body: NotesPage(),
                      secondaryBody: NoteDetailPage(),
                    ),
                  ),
                ),
                GoRoute(
                  name: NoteEditPage.name,
                  path: NoteEditPage.path,
                  parentNavigatorKey: notesNavigatorKey,
                  pageBuilder: (BuildContext context, GoRouterState state) =>
                      NoTransitionPage(
                    child: AppScaffold(
                      body: NotesPage(),
                      secondaryBody: NoteEditPage(),
                    ),
                  ),
                ),
              ]),
        ],
      ),
      StatefulShellBranch(
        navigatorKey: passwordsNavigatorKey,
        routes: <RouteBase>[
          GoRoute(
            name: PasswordsPage.name,
            path: PasswordsPage.path,
            pageBuilder: (BuildContext context, GoRouterState state) =>
                NoTransitionPage(
              child: AppScaffold(
                body: PasswordsPage(),
              ),
            ),
            routes: <RouteBase>[
              GoRoute(
                name: PasswordDetailPage.name,
                path: PasswordDetailPage.path,
                parentNavigatorKey: passwordsNavigatorKey,
                pageBuilder: (BuildContext context, GoRouterState state) =>
                    NoTransitionPage(
                  child: AppScaffold(
                    body: PasswordsPage(),
                    secondaryBody: PasswordDetailPage(),
                  ),
                ),
              ),
              GoRoute(
                name: PasswordEditPage.name,
                path: PasswordEditPage.path,
                parentNavigatorKey: passwordsNavigatorKey,
                pageBuilder: (BuildContext context, GoRouterState state) =>
                    NoTransitionPage(
                  child: AppScaffold(
                    body: PasswordsPage(),
                    secondaryBody: PasswordEditPage(),
                  ),
                ),
              ),
              GoRoute(
                name: PasswordCreatePage.name,
                path: PasswordCreatePage.path,
                parentNavigatorKey: passwordsNavigatorKey,
                pageBuilder: (BuildContext context, GoRouterState state) =>
                    NoTransitionPage(
                  child: AppScaffold(
                    body: PasswordsPage(),
                    secondaryBody: PasswordCreatePage(),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    ],
  );

  final List<GoRoute> openRoutes = <GoRoute>[
    GoRoute(
      name: SplashPage.name,
      path: SplashPage.path,
      pageBuilder: (BuildContext context, GoRouterState state) {
        return const MaterialPage<void>(
          child: SplashPage(),
        );
      },
    ),
    GoRoute(
      name: IntroductionPage.name,
      path: IntroductionPage.path,
      pageBuilder: (BuildContext context, GoRouterState state) {
        return const MaterialPage<void>(
          child: IntroductionPage(),
        );
      },
    ),
  ];

  return GoRouter(
    navigatorKey: rootNavigatorKey,
    debugLogDiagnostics: false,
    initialLocation: NotesPage.path,
    redirect: (BuildContext context, GoRouterState state) {
      print("-----> root redirect");
      final skip = state.uri.queryParameters["skip"];

      // fixme: if create table failed, then we should not redirect
      if (onboardingState.isLoading) {
        return SplashPage.path;
      } else if (onboardingState.unwrapPrevious().valueOrNull == false) {
        if (skip == "true") {
          return null;
        } else {
          return IntroductionPage.path;
        }
      } else {
        return null;
      }
    },
    routes: <RouteBase>[
      unauthenticatedRoutes,
      authenticatedRoutes,
      ...openRoutes,
    ],
  );
});

class CryptowlApp extends ConsumerWidget {
  const CryptowlApp({
    super.key,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    final themeMode = ref.watch(themeProvider);

    return MaterialApp.router(
      routerConfig: router,
      debugShowCheckedModeBanner: false,
      restorationScopeId: 'app',
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      locale: Locale('zh', 'CN'),
      supportedLocales: const [Locale("en"), Locale('zh', 'CN')],
      onGenerateTitle: (BuildContext context) =>
          AppLocalizations.of(context)!.appTitle,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: themeMode,
      builder: (context, child) => ResponsiveBreakpoints.builder(
        child: child!,
        breakpoints: [
          const Breakpoint(start: 0, end: 450, name: MOBILE),
          const Breakpoint(start: 451, end: 800, name: TABLET),
          const Breakpoint(start: 801, end: 1920, name: DESKTOP),
          const Breakpoint(start: 1921, end: double.infinity, name: '4K'),
        ],
      ),
    );
  }
}
