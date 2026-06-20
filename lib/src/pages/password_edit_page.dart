import 'dart:convert';

import 'package:cryptowl/src/components/password_field.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../components/form_input.dart';
import '../crypto/protected_value.dart';
import '../providers/providers.dart';

class PasswordEditPage extends ConsumerStatefulWidget {
  const PasswordEditPage({super.key});

  static const String path = '/edit/:id';
  static const String name = 'Password Edit';

  @override
  ConsumerState<PasswordEditPage> createState() => _PasswordEditPageState();
}

class _PasswordEditPageState extends ConsumerState<PasswordEditPage> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _usernameController = TextEditingController();
  final _uriController = TextEditingController();
  final _passwordController = TextEditingController();
  final _remarkController = TextEditingController();

  bool _initialized = false;

  @override
  void dispose() {
    _titleController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    _uriController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  void _initControllers(dynamic value) {
    if (_initialized) return;
    _initialized = true;
    _titleController.text = value.title ?? "";
    _usernameController.text = value.getUser()?.plainValue() ?? "";
    _passwordController.text =
        utf8.decode(value.value.binaryValue, allowMalformed: true);
    _remarkController.text = value.getRemark()?.plainValue() ?? "";
  }

  @override
  Widget build(BuildContext context) {
    final id = GoRouterState.of(context).pathParameters["id"]!;
    final detailFuture = ref.watch(passwordDetailProvider(id));
    final passwordRepository = ref.read(passwordRepositoryProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text('Edit password'),
        actions: [
          TextButton(
              onPressed: () async {
                if (_formKey.currentState!.validate()) {
                  await passwordRepository.update(id,
                      title: _titleController.text,
                      value:
                          ProtectedValue.fromString(_passwordController.text),
                      username: _usernameController.text,
                      url: _uriController.text,
                      remark: _remarkController.text);
                  ref.invalidate(passwordsProvider);
                  ref.invalidate(passwordDetailProvider(id));
                  if (context.mounted) {
                    context.pop();
                  }
                }
              },
              child: const Text("Save"))
        ],
      ),
      body: detailFuture.when(
        data: (detail) {
          _initControllers(detail);
          return Padding(
            padding: EdgeInsets.all(12),
            child: Form(
              key: _formKey,
              child: Column(
                children: <Widget>[
                  SizedBox(height: 10),
                  FormInput(
                    controller: _titleController,
                    name: "Name",
                    protected: false,
                    required: true,
                  ),
                  SizedBox(height: 10),
                  FormInput(
                    controller: _usernameController,
                    name: "Username",
                    protected: false,
                    required: false,
                  ),
                  SizedBox(height: 10),
                  PasswordField(
                    mode: PasswordDisplayMode.edit,
                    controller: _passwordController,
                    label: 'Password',
                    required: true,
                  ),
                  SizedBox(height: 10),
                  FormInput(
                    controller: _uriController,
                    name: "URI",
                    protected: false,
                    required: false,
                  ),
                  SizedBox(height: 10),
                  FormInput(
                    controller: _remarkController,
                    name: "Remark",
                    protected: false,
                    required: false,
                  ),
                ],
              ),
            ),
          );
        },
        loading: () => const Center(
          child: CircularProgressIndicator(),
        ),
        error: (e, _) => ErrorWidget(e),
      ),
    );
  }
}
