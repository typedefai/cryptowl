import 'package:flutter/material.dart';
import 'package:flutter_hooks/flutter_hooks.dart';

class SettingDialog<T> extends HookWidget {
  final String title;
  final List<T> options;
  final T initialValue;

  const SettingDialog(this.title, this.options, this.initialValue, {super.key});

  @override
  Widget build(BuildContext context) {
    final valueNotifier = useState(initialValue);

    return AlertDialog(
      title: Text(title),
      content: SingleChildScrollView(
        child: ListBody(
          children: options.map((o) {
            return ListTile(
              dense: true,
              title: Text(o.toString()),
              leading: Radio<T>(
                value: o,
                groupValue: valueNotifier.value,
                onChanged: (T? value) {
                  valueNotifier.value = value!;
                },
              ),
            );
          }).toList(),
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.pop(context, valueNotifier.value),
          child: const Text('Ok'),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context, null),
          child: const Text('Cancel'),
        ),
      ],
    );
  }
}
