import 'dart:typed_data';

import 'package:cryptowl/src/common/classification.dart';
import 'package:cryptowl/src/domain/password.dart';
import 'package:cryptowl/src/providers/providers.dart';
import 'package:flutter/material.dart' hide DropdownMenuFormField;
import 'package:flutter_hooks/flutter_hooks.dart';
import 'package:go_router/go_router.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

import '../crypto/protected_value.dart';

class PasswordCreatePage extends HookConsumerWidget {
  const PasswordCreatePage({super.key});

  static const String path = '/create';
  static const String name = 'Password Create';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(asyncLoginProvider);
    final passwordService = ref.watch(passwordServiceProvider);

    final formKey = useMemoized(() => GlobalKey<FormState>());
    final titleController = useTextEditingController();
    final passwordController = useTextEditingController();
    final userController = useTextEditingController();
    final remarkController = useTextEditingController();
    final classification = useState<Classification>(Classification.secret);

    String? mandatoryValidator(String? value) {
      if (value == null || value.isEmpty) {
        return 'Please enter a value';
      }
      return null;
    }

    String classificationLabel(Classification c) {
      switch (c) {
        case Classification.confidential:
          return 'Confidential — readable after login';
        case Classification.secret:
          return 'Secret — requires biometric to view';
        case Classification.topSecret:
          return 'Top Secret — requires secondary password';
      }
    }

    void submitForm() async {
      if (formKey.currentState!.validate()) {
        final sessionData = session.value!;
        final password = Password.create(
            titleController.text,
            ProtectedValue.fromString(passwordController.text),
            classification.value,
            userController.text,
            remarkController.text);
        final kek = ProtectedValue.fromBinary(Uint8List.sublistView(
            sessionData.symmetricKey.binaryValue, 0, 32));

        if (classification.value == Classification.topSecret &&
            !sessionData.hasSecondaryKey) {
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text(
                    'Unlock Top Secret first from the password list page.'),
                backgroundColor: Colors.red,
              ),
            );
          }
          return;
        }

        await passwordService.createPassword(
            password, kek, sessionData.secondaryKey);
        ref.invalidate(passwordsProvider);
        if (context.mounted) {
          context.pop();
        }
      }
    }

    return Scaffold(
      appBar: AppBar(
        centerTitle: false,
        title: Text('Create password'),
        actions: [TextButton(onPressed: submitForm, child: Text("Save"))],
      ),
      body: Padding(
        padding: EdgeInsets.all(12),
        child: SingleChildScrollView(
          child: Form(
            key: formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                TextFormField(
                  controller: titleController,
                  validator: mandatoryValidator,
                  decoration: const InputDecoration(
                    helperText: 'Title is plaintext and searchable',
                    labelText: 'Title *',
                  ),
                ),
                const SizedBox(height: 10),
                TextFormField(
                  controller: passwordController,
                  obscureText: true,
                  validator: mandatoryValidator,
                  decoration: const InputDecoration(
                      helperText: 'Password will be encrypted and protected',
                      labelText: 'Password *',
                      suffixIcon: Icon(
                        Icons.shield,
                        color: Colors.green,
                      )),
                ),
                const SizedBox(height: 20),

                // Classification selector
                const Text('Encryption Level',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                ...Classification.values.map((c) => RadioListTile<Classification>(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      title: Text(c.name),
                      subtitle: Text(classificationLabel(c),
                          style: const TextStyle(fontSize: 12)),
                      value: c,
                      groupValue: classification.value,
                      onChanged: (Classification? value) {
                        if (value != null) {
                          classification.value = value;
                        }
                      },
                    )),
                const SizedBox(height: 20),

                TextFormField(
                  controller: userController,
                  decoration: const InputDecoration(
                    helperText: 'User name of this login',
                    labelText: 'User name',
                  ),
                ),
                const SizedBox(height: 10),
                TextFormField(
                  controller: remarkController,
                  decoration: const InputDecoration(
                    helperText: 'Add any remark if you want',
                    labelText: 'Remark',
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
