import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const formTextStyle = TextStyle();

enum Menu { copy, show, generate }

class FormInput extends StatefulWidget {
  final String name;
  final bool protected;
  final bool required;
  final bool readonly;
  final TextEditingController? controller;
  final String? value;

  const FormInput(
      {super.key,
      this.controller,
      required this.name,
      this.value,
      this.protected = false,
      this.required = false,
      this.readonly = false});

  @override
  State<FormInput> createState() => _FormInputState();
}

class _FormInputState extends State<FormInput> {
  late bool obscureText;

  @override
  void initState() {
    super.initState();
    obscureText = widget.protected;
  }

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      style: formTextStyle,
      controller: widget.controller,
      obscureText: obscureText,
      readOnly: widget.readonly,
      initialValue: widget.value,
      decoration: InputDecoration(
        prefixIcon: Icon(widget.protected ? Icons.shield : Icons.abc),
        labelText: widget.name,
        labelStyle: formTextStyle,
        suffixIcon: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (widget.protected)
              IconButton(
                onPressed: () {
                  setState(() {
                    obscureText = !obscureText;
                  });
                },
                icon:
                    Icon(obscureText ? Icons.visibility_off : Icons.visibility),
              ),
            if (widget.readonly && widget.value != null)
              IconButton(
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: widget.value!));
                },
                icon: Icon(Icons.copy),
              ),
            if (!widget.readonly)
              PopupMenuButton<Menu>(
                icon: const Icon(Icons.more_vert),
                onSelected: (Menu item) {},
                itemBuilder: (BuildContext context) => <PopupMenuEntry<Menu>>[
                  const PopupMenuItem<Menu>(
                    value: Menu.copy,
                    child: ListTile(
                      leading: Icon(Icons.copy),
                      title: Text('Copy'),
                    ),
                  ),
                  const PopupMenuItem<Menu>(
                    value: Menu.show,
                    child: ListTile(
                      leading: Icon(Icons.remove_red_eye),
                      title: Text('Show'),
                    ),
                  ),
                  const PopupMenuItem<Menu>(
                    value: Menu.generate,
                    child: ListTile(
                      leading: Icon(Icons.change_circle),
                      title: Text('Generate'),
                    ),
                  ),
                ],
              )
          ],
        ),
      ),
      validator: (value) {
        if (widget.required && (value == null || value.isEmpty)) {
          return 'Please enter some text';
        }
        return null;
      },
    );
  }
}
