import 'package:flutter/material.dart';

class FormSwitch extends StatelessWidget {
  final String name;
  final bool value;

  const FormSwitch({super.key, required this.name, required this.value});

  @override
  Widget build(BuildContext context) {
    return FormField(
      initialValue: value,
      builder: (FormFieldState<bool> field) {
        return Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: <Widget>[
            Text(name),
            Transform.scale(
              scale: 0.8,
              child: Switch(
                onChanged: (value) {
                  field.didChange(value);
                },
                value: field.value ?? false,
              ),
            ),
          ],
        );
      },
    );
  }
}
