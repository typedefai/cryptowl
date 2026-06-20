import 'package:cryptowl/src/providers/providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_hooks/flutter_hooks.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:vector_graphics/vector_graphics.dart';

import '../crypto/protected_value.dart';

class OnboardingPage extends HookConsumerWidget {
  const OnboardingPage({super.key});

  static const String path = '/onboarding';
  static const String name = 'Onboarding';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final masterPasswordController = useTextEditingController();
    final confirmPasswordController = useTextEditingController();
    final passwordHintController = useTextEditingController();
    final secondaryPasswordController = useTextEditingController();
    final confirmSecondaryController = useTextEditingController();

    final isPasswordVisible = useState(false);
    final isConfirmPasswordVisible = useState(false);
    final isSecondaryVisible = useState(false);
    final showSecondaryFields = useState(false);
    final formKey = useMemoized(() => GlobalKey<FormState>());

    final onboardingNotifier = ref.read(onboardingProvider.notifier);

    String? passwordValidator(String? value) {
      if (value == null || value.isEmpty) {
        return 'Please enter a password';
      }
      if (value.length < 8) {
        return 'Password must be at least 8 characters';
      }
      return null;
    }

    String? confirmPasswordValidator(String? value) {
      if (value != masterPasswordController.text) {
        return 'Passwords do not match';
      }
      return null;
    }

    String? secondaryConfirmValidator(String? value) {
      if (showSecondaryFields.value &&
          value != secondaryPasswordController.text) {
        return 'Passwords do not match';
      }
      return null;
    }

    void submitForm() {
      if (formKey.currentState!.validate()) {
        final secondaryPw = showSecondaryFields.value &&
                secondaryPasswordController.text.isNotEmpty
            ? ProtectedValue.fromString(secondaryPasswordController.text)
            : null;
        onboardingNotifier.completeOnboarding(
            ProtectedValue.fromString(masterPasswordController.text),
            passwordHintController.text,
            secondaryPassword: secondaryPw);
      }
    }

    return Scaffold(
      body: Padding(
        padding: EdgeInsets.all(32.0),
        child: Form(
          key: formKey,
          child: SingleChildScrollView(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                SvgPicture(
                  AssetBytesLoader("assets/images/cryptowl-full.svg.vec"),
                  height: 50,
                ),
                const Text("You need to set a master password to continue."),
                const SizedBox(height: 40),
                // Master Password Field
                TextFormField(
                  controller: masterPasswordController,
                  obscureText: !isPasswordVisible.value,
                  validator: passwordValidator,
                  decoration: InputDecoration(
                    labelText: 'Master Password',
                    suffixIcon: IconButton(
                      icon: Icon(
                        isPasswordVisible.value
                            ? Icons.visibility_off
                            : Icons.visibility,
                      ),
                      onPressed: () =>
                          isPasswordVisible.value = !isPasswordVisible.value,
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Confirm Password Field
                TextFormField(
                  controller: confirmPasswordController,
                  obscureText: !isConfirmPasswordVisible.value,
                  validator: confirmPasswordValidator,
                  decoration: InputDecoration(
                    labelText: 'Confirm Password',
                    suffixIcon: IconButton(
                      icon: Icon(
                        isConfirmPasswordVisible.value
                            ? Icons.visibility_off
                            : Icons.visibility,
                      ),
                      onPressed: () => isConfirmPasswordVisible.value =
                          !isConfirmPasswordVisible.value,
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Password Hint Field
                TextFormField(
                  controller: passwordHintController,
                  decoration: const InputDecoration(
                    labelText: 'Password Hint (Optional)',
                    helperText: 'A phrase to help you remember your password',
                  ),
                  maxLength: 100,
                ),
                const SizedBox(height: 24),

                // Secondary Password Section
                if (!showSecondaryFields.value)
                  TextButton.icon(
                    onPressed: () => showSecondaryFields.value = true,
                    icon: const Icon(Icons.add),
                    label: const Text(
                        'Set up secondary password for Top Secret items'),
                  ),

                if (showSecondaryFields.value) ...[
                  const Divider(),
                  const SizedBox(height: 16),
                  const Text(
                    'Secondary Password (for Top Secret items)',
                    style: TextStyle(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'This password protects your most sensitive items separately '
                    'from the master password. Even if the master password is '
                    'compromised, Top Secret items remain safe.',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: secondaryPasswordController,
                    obscureText: !isSecondaryVisible.value,
                    validator: showSecondaryFields.value
                        ? passwordValidator
                        : null,
                    decoration: InputDecoration(
                      labelText: 'Secondary Password',
                      suffixIcon: IconButton(
                        icon: Icon(
                          isSecondaryVisible.value
                              ? Icons.visibility_off
                              : Icons.visibility,
                        ),
                        onPressed: () => isSecondaryVisible.value =
                            !isSecondaryVisible.value,
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: confirmSecondaryController,
                    obscureText: !isSecondaryVisible.value,
                    validator: secondaryConfirmValidator,
                    decoration: const InputDecoration(
                      labelText: 'Confirm Secondary Password',
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextButton(
                    onPressed: () {
                      showSecondaryFields.value = false;
                      secondaryPasswordController.clear();
                      confirmSecondaryController.clear();
                    },
                    child: const Text('Remove secondary password'),
                  ),
                ],
                const SizedBox(height: 40),

                // Submit Button
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed: submitForm,
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                    ),
                    child: const Text(
                      'Continue',
                      style: TextStyle(fontSize: 16),
                    ),
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
