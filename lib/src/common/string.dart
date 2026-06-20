extension StringExtensions on String? {
  bool get isNotBlank {
    final self = this;
    return self != null && self.trim().isNotEmpty;
  }
}
