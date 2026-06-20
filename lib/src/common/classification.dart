enum Classification {
  confidential("C"),
  secret("S"),
  topSecret("T");

  final String value;

  const Classification(this.value);

  factory Classification.parse(String value) {
    return Classification.values
        .firstWhere((element) => element.value == value);
  }
}
