import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A password field widget that handles all CRUD states.
///
/// Modes:
/// - [PasswordDisplayMode.view]: Read-only, masked by default, reveal/copy buttons
/// - [PasswordDisplayMode.edit]: Editable, pre-filled with current value
/// - [PasswordDisplayMode.create]: Editable, empty, with generate button
/// - [PasswordDisplayMode.locked]: Shows "Locked" with unlock button
enum PasswordDisplayMode { view, edit, create, locked }

class PasswordField extends StatefulWidget {
  /// Current mode of the field
  final PasswordDisplayMode mode;

  /// The password value (plaintext). Null if locked.
  final String? value;

  /// Controller for edit/create modes
  final TextEditingController? controller;

  /// Called when the user taps "Unlock" in locked mode
  final VoidCallback? onUnlock;

  /// Called when the user taps "Generate" to fill a random password
  final ValueChanged<String>? onGenerate;

  /// Label text
  final String label;

  /// Whether the field is required (for form validation)
  final bool required;

  const PasswordField({
    super.key,
    required this.mode,
    this.value,
    this.controller,
    this.onUnlock,
    this.onGenerate,
    this.label = 'Password',
    this.required = false,
  });

  @override
  State<PasswordField> createState() => _PasswordFieldState();
}

class _PasswordFieldState extends State<PasswordField> {
  bool _obscured = true;
  bool _copied = false;

  TextEditingController? _internalController;

  TextEditingController get _controller =>
      widget.controller ?? _internalController!;

  @override
  void initState() {
    super.initState();
    if (widget.controller == null &&
        (widget.mode == PasswordDisplayMode.edit ||
            widget.mode == PasswordDisplayMode.create)) {
      _internalController = TextEditingController(text: widget.value ?? '');
    }
  }

  @override
  void dispose() {
    _internalController?.dispose();
    super.dispose();
  }

  String get _displayText {
    if (widget.mode == PasswordDisplayMode.locked) return '';
    if (widget.mode == PasswordDisplayMode.view) return widget.value ?? '';
    return _controller.text;
  }

  bool get _isEditable =>
      widget.mode == PasswordDisplayMode.edit ||
      widget.mode == PasswordDisplayMode.create;

  void _copy() async {
    final text = widget.mode == PasswordDisplayMode.view
        ? widget.value
        : _controller.text;
    if (text == null || text.isEmpty) return;

    await Clipboard.setData(ClipboardData(text: text));
    setState(() => _copied = true);
    Future.delayed(const Duration(seconds: 2), () {
      if (mounted) setState(() => _copied = false);
    });
  }

  void _generate() {
    const chars =
        'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#\$%^&*';
    final random = Random.secure();
    final password =
        List.generate(20, (_) => chars[random.nextInt(chars.length)]).join();
    _controller.text = password;
    widget.onGenerate?.call(password);
  }

  @override
  Widget build(BuildContext context) {
    // Locked mode: show unlock button
    if (widget.mode == PasswordDisplayMode.locked) {
      return InputDecorator(
        decoration: InputDecoration(
          labelText: widget.label,
          prefixIcon: const Icon(Icons.lock, color: Colors.orange),
          suffixIcon: widget.onUnlock != null
              ? TextButton.icon(
                  onPressed: widget.onUnlock,
                  icon: const Icon(Icons.lock_open, size: 18),
                  label: const Text('Unlock'),
                )
              : null,
          border: const OutlineInputBorder(),
        ),
        child: const Text(
          'Encrypted — authenticate to view',
          style: TextStyle(color: Colors.grey),
        ),
      );
    }

    // View/Edit/Create modes
    return TextFormField(
      controller: _isEditable ? _controller : null,
      initialValue:
          widget.mode == PasswordDisplayMode.view ? widget.value : null,
      obscureText: _obscured,
      readOnly: !_isEditable,
      style: const TextStyle(fontFamily: 'monospace'),
      decoration: InputDecoration(
        labelText: widget.label,
        prefixIcon: Icon(
          widget.mode == PasswordDisplayMode.view
              ? Icons.shield
              : Icons.key,
        ),
        suffixIcon: _buildSuffix(),
        border: const OutlineInputBorder(),
      ),
      validator: widget.required
          ? (value) {
              if (value == null || value.isEmpty) {
                return 'Please enter ${widget.label.toLowerCase()}';
              }
              return null;
            }
          : null,
    );
  }

  Widget _buildSuffix() {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        // Reveal/hide toggle
        IconButton(
          onPressed: () => setState(() => _obscured = !_obscured),
          icon: Icon(
            _obscured ? Icons.visibility_off : Icons.visibility,
            size: 20,
          ),
          tooltip: _obscured ? 'Show' : 'Hide',
        ),
        // Copy button
        IconButton(
          onPressed: _copy,
          icon: Icon(
            _copied ? Icons.check : Icons.copy,
            size: 20,
            color: _copied ? Colors.green : null,
          ),
          tooltip: _copied ? 'Copied!' : 'Copy',
        ),
        // Generate button (edit/create only)
        if (_isEditable)
          IconButton(
            onPressed: _generate,
            icon: const Icon(Icons.autorenew, size: 20),
            tooltip: 'Generate password',
          ),
      ],
    );
  }
}
