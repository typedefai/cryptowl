String? validatePassword(String password) {
  if (password.length < 8) {
    return 'Password must be at least 8 characters long.';
  }

  if (!password.contains(RegExp(r'[A-Z]'))) {
    return 'Password must contain at least 1 uppercase letter.';
  }

  if (!password.contains(RegExp(r'[a-z]'))) {
    return 'Password must contain at least 1 lowercase letter.';
  }

  if (!password.contains(RegExp(r'[0-9]'))) {
    return 'Password must contain at least 1 number.';
  }

  if (!password.contains(RegExp(r'[!@#$%^&*(),.?":{}|<>]'))) {
    return 'Password must contain at least 1 special character.';
  }

  if (!RegExp(r'^[A-Za-z0-9 !@#\$%\^&\*\(\),\.\?":\{\}\|<>\[\]]+$')
      .hasMatch(password)) {
    return 'Password contains invalid characters.';
  }

  return null;
}
