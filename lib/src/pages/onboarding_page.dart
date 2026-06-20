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

    final isPasswordVisible = useState(false);
    final isConfirmPasswordVisible = useState(false);
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

    void submitForm() {
      if (formKey.currentState!.validate()) {
        onboardingNotifier.completeOnboarding(
            ProtectedValue.fromString(masterPasswordController.text),
            passwordHintController.text);
      }
    }

    return Scaffold(
      body: Padding(
        padding: EdgeInsets.all(32.0),
        child: Form(
          key: formKey,
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
    );
  }
}
