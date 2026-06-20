import 'package:cryptowl/src/crypto/protected_value.dart';
import 'package:cryptowl/src/providers/providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_hooks/flutter_hooks.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:toastification/toastification.dart';
import 'package:vector_graphics/vector_graphics.dart';

import '../common/exceptions.dart';

class LoginPage extends HookConsumerWidget {
  const LoginPage({super.key});

  static const String path = '/login';
  static const String name = 'Login';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inputController = useTextEditingController();
    final fileService = ref.watch(fileServiceProvider);
    final loginState = ref.watch(asyncLoginProvider);

    Future<void> onLoginSubmitted() async {
      if (inputController.text.isEmpty) {
        toastification.show(
          type: ToastificationType.error,
          context: context,
          title: Text('Please input your password!'),
          autoCloseDuration: const Duration(seconds: 3),
        );
        return;
      }
      final dbs = await fileService.getSqlcipherInstances();
      if (dbs.isEmpty) {
        toastification.show(
          type: ToastificationType.error,
          context: context,
          title: Text('No vault found. Please create one first.'),
          autoCloseDuration: const Duration(seconds: 3),
        );
        return;
      }
      if (dbs.length > 1) {
        throw Exception("Multiple instances found but not supported currently");
      }
      ref.read(asyncLoginProvider.notifier).login(
          dbs.first.replaceAll(RegExp(r'.enc$'), ''),
          ProtectedValue.fromString(inputController.text));
    }

    Widget loginButton() {
      return ElevatedButton(
        onPressed: onLoginSubmitted,
        child: const Text("Login"),
      );
    }

    String? getError(Object? error) {
      if (error == null) {
        return null;
      } else if (error is IncorrectPasswordException) {
        return "Password incorrect, please try again";
      } else {
        return "Unknown error: $error";
      }
    }

    return Scaffold(
      body: Padding(
        padding: EdgeInsets.all(32.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            SvgPicture(
              AssetBytesLoader("assets/images/cryptowl-full.svg.vec"),
              height: 50,
            ),
            const Text("Please login use you master password."),
            SizedBox(height: 20),
            TextField(
              autofocus: true,
              controller: inputController,
              obscureText: true,
              textInputAction: TextInputAction.go,
              onSubmitted: (_) => onLoginSubmitted(),
              decoration: InputDecoration(
                labelText: "Master password",
                errorText: getError(loginState.error),
              ),
            ),
            SizedBox(height: 10),
            loginState.when(
              loading: () => const CircularProgressIndicator(),
              error: (error, stack) => loginButton(),
              data: (kdbx) =>
                  kdbx != null ? const Text('Login success') : loginButton(),
            ),
          ],
        ),
      ),
    );
  }
}
