import 'dart:convert';

import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/components/password_field.dart';
import 'package:cryptowl/src/pages/password_edit_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../components/error.dart';
import '../components/form_input.dart';
import '../providers/providers.dart';

final DateFormat formatter = DateFormat('yyyy-MM-dd');

class PasswordDetailPage extends ConsumerWidget {
  const PasswordDetailPage({super.key});

  static const String path = '/detail/:id';
  static const String name = 'Password Detail';

  Future<bool?> _confirmDeletion(BuildContext context) async {
    return showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Delete password'),
          content: const SingleChildScrollView(
            child: ListBody(
              children: <Widget>[
                Text('Password will be removed'),
                Text('Are you sure you want to delete it?'),
              ],
            ),
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('OK'),
            ),
          ],
        );
      },
    );
  }

  String _classificationLabel(Classification c) {
    switch (c) {
      case Classification.confidential:
        return 'Confidential';
      case Classification.secret:
        return 'Secret';
      case Classification.topSecret:
        return 'Top Secret';
    }
  }

  IconData _classificationIcon(Classification c) {
    switch (c) {
      case Classification.confidential:
        return Icons.lock_open;
      case Classification.secret:
        return Icons.lock;
      case Classification.topSecret:
        return Icons.shield;
    }
  }

  Color _classificationColor(Classification c) {
    switch (c) {
      case Classification.confidential:
        return Colors.green;
      case Classification.secret:
        return Colors.orange;
      case Classification.topSecret:
        return Colors.red;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final id = GoRouterState.of(context).pathParameters["id"]!;
    final detailFuture = ref.watch(passwordDetailProvider(id));
    print("[Detail] build() called for $id, state: ${detailFuture.runtimeType}");

    final passwordRepository = ref.read(passwordRepositoryProvider);
    return Scaffold(
      appBar: AppBar(
        title: Text('Password detail'),
        actions: [
          IconButton(
              onPressed: () async {
                print("[Detail] Navigating to edit page for $id");
                final result = await context.pushNamed<bool>(
                  PasswordEditPage.name,
                  pathParameters: <String, String>{'id': id},
                );
                print("[Detail] Edit page returned: $result");
                if (result == true && context.mounted) {
                  print("[Detail] Invalidating providers for $id");
                  ref.invalidate(passwordDetailProvider(id));
                  ref.invalidate(passwordsProvider);
                  // Force immediate re-read
                  final _ = ref.read(passwordDetailProvider(id));
                  print("[Detail] Provider invalidated and re-read triggered");
                }
              },
              icon: Icon(Icons.edit_note)),
          IconButton(
              onPressed: () async {
                bool? confirm = await _confirmDeletion(context);
                if (confirm == true) {
                  final success = await passwordRepository.delete(id);
                  if (success) {
                    ref.invalidate(passwordsProvider);
                    if (context.mounted) {
                      context.pop();
                    }
                  }
                }
              },
              icon: Icon(Icons.delete_outline)),
        ],
      ),
      body: detailFuture.when(
        data: (password) {
          // Determine password field mode
          final isDecrypted = password.value.getText().isNotEmpty;
          final passwordMode = password.isEncrypted && !isDecrypted
              ? PasswordDisplayMode.locked
              : PasswordDisplayMode.view;

          final passwordText = isDecrypted
              ? utf8.decode(password.value.binaryValue, allowMalformed: true)
              : null;

          return Padding(
            padding: EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Classification badge
                Chip(
                  avatar: Icon(
                    _classificationIcon(password.classification),
                    size: 18,
                    color: _classificationColor(password.classification),
                  ),
                  label: Text(
                    _classificationLabel(password.classification),
                    style: TextStyle(
                      color: _classificationColor(password.classification),
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  side: BorderSide(
                      color: _classificationColor(password.classification)),
                ),
                SizedBox(height: 16),
                FormInput(
                  name: "Name",
                  readonly: true,
                  value: password.title,
                ),
                SizedBox(height: 16),
                FormInput(
                  name: "Username",
                  readonly: true,
                  value: password.getUser()?.plainValue(),
                ),
                SizedBox(height: 16),
                PasswordField(
                  mode: passwordMode,
                  value: passwordText,
                  onUnlock: password.isTopSecret
                      ? () {
                          // TODO: prompt for secondary password
                        }
                      : () {
                          // TODO: prompt for biometric/PIN
                        },
                ),
                SizedBox(height: 16),
                FormInput(
                  name: "URI",
                  readonly: true,
                ),
                SizedBox(height: 16),
                FormInput(
                  name: "Remark",
                  readonly: true,
                  value: password.getRemark()?.plainValue(),
                ),
                SizedBox(height: 20),
                Text(
                  "ID: ${password.id}",
                  style: TextStyle(fontSize: 10, color: Colors.grey),
                ),
                Text(
                  "Updated at ${formatter.format(password.updatedAt)}",
                  style: TextStyle(fontSize: 10, color: Colors.grey),
                ),
              ],
            ),
          );
        },
        loading: () => const Center(
          child: CircularProgressIndicator(),
        ),
        error: (e, _) => ErrorInfo(e.toString()),
      ),
    );
  }
}
