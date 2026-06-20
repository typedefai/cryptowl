import 'package:flutter/material.dart';

class DropdownMenuFormField<T> extends FormField<T> {
  DropdownMenuFormField({
    super.key,
    bool enabled = true,
    double? width,
    double? menuHeight,
    Widget? leadingIcon,
    Widget? trailingIcon,
    Widget? label,
    String? hintText,
    String? helperText,
    String? errorText,
    Widget? selectedTrailingIcon,
    bool enableFilter = false,
    bool enableSearch = true,
    TextStyle? textStyle,
    InputDecorationTheme? inputDecorationTheme,
    MenuStyle? menuStyle,
    this.controller,
    T? initialSelection,
    this.onSelected,
    bool? requestFocusOnTap,
    EdgeInsets? expandedInsets,
    required List<DropdownMenuEntry<T>> dropdownMenuEntries,
    super.autovalidateMode = AutovalidateMode.disabled,
    super.validator,
  }) : super(
            initialValue: initialSelection,
            builder: (FormFieldState<T> field) {
              final _DropdownMenuFormFieldState<T> state =
                  field as _DropdownMenuFormFieldState<T>;
              void onSelectedHandler(T? value) {
                field.didChange(value);
                onSelected?.call(value);
              }

              return DropdownMenu<T>(
                key: key,
                enabled: enabled,
                width: width,
                menuHeight: menuHeight,
                leadingIcon: leadingIcon,
                trailingIcon: trailingIcon,
                label: label,
                hintText: hintText,
                helperText: helperText,
                errorText: state.errorText,
                selectedTrailingIcon: selectedTrailingIcon,
                enableFilter: enableFilter,
                enableSearch: enableSearch,
                textStyle: textStyle,
                inputDecorationTheme: inputDecorationTheme,
                menuStyle: menuStyle,
                controller: controller,
                initialSelection: state.value,
                onSelected: onSelectedHandler,
                requestFocusOnTap: requestFocusOnTap,
                expandedInsets: expandedInsets,
                dropdownMenuEntries: dropdownMenuEntries,
              );
            });

  final ValueChanged<T?>? onSelected;

  final TextEditingController? controller;

  @override
  FormFieldState<T> createState() => _DropdownMenuFormFieldState<T>();
}

class _DropdownMenuFormFieldState<T> extends FormFieldState<T> {
  DropdownMenuFormField<T> get _dropdownMenuFormField =>
      widget as DropdownMenuFormField<T>;

  @override
  void didChange(T? value) {
    super.didChange(value);
    // _dropdownMenuFormField.onSelected!(value);
  }

  @override
  void didUpdateWidget(DropdownMenuFormField<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.initialValue != widget.initialValue) {
      setValue(widget.initialValue);
    }
  }

  @override
  void reset() {
    super.reset();
    _dropdownMenuFormField.onSelected!(value);
  }
}
