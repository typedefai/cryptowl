import 'dart:typed_data';

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
    final isTopSecret = useState<bool>(false);

    String? mandatoryValidator(String? value) {
      if (value == null || value.isEmpty) {
        return 'Please enter a value';
      }
      return null;
    }

    void submitForm() async {
      if (formKey.currentState!.validate()) {
        final password = Password.create(
            titleController.text,
            ProtectedValue.fromString(passwordController.text),
            isTopSecret.value,
            userController.text,
            remarkController.text);
        final kek = ProtectedValue.fromBinary(Uint8List.sublistView(
            session.value!.symmetricKey.binaryValue, 0, 32));
        await passwordService.createPassword(password, kek);
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
                SwitchListTile(
                  dense: true,
                  visualDensity: VisualDensity.compact,
                  contentPadding: EdgeInsets.all(0),
                  title: Text(
                    'Top secret',
                  ),
                  subtitle: Text(
                    'Top secret will be encrypted with additional security',
                  ),
                  value: isTopSecret.value,
                  onChanged: (bool value) {
                    isTopSecret.value = value;
                  },
                ),
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
