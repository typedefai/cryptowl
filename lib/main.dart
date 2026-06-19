import 'dart:convert';
import 'dart:ffi';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';
import 'package:flutter/material.dart';
import 'package:native_argon2/native_argon2.dart' as argon2;

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Cryptowl - Argon2 Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const Argon2DemoPage(),
    );
  }
}

class Argon2DemoPage extends StatefulWidget {
  const Argon2DemoPage({super.key});

  @override
  State<Argon2DemoPage> createState() => _Argon2DemoPageState();
}

class _Argon2DemoPageState extends State<Argon2DemoPage> {
  final _passwordController = TextEditingController(text: 'password');
  final _saltController = TextEditingController(text: 'somesalt');
  String _status = 'Ready';
  String _result = '';
  bool _running = false;

  // OWASP recommended parameters
  static const int _tCost = 2;
  static const int _mCost = 19456; // 19 MiB
  static const int _parallelism = 1;
  static const int _hashLen = 32;

  Future<void> _runArgon2id() async {
    setState(() {
      _running = true;
      _status = 'Running argon2id...';
      _result = '';
    });

    final stopwatch = Stopwatch()..start();

    try {
      final password = utf8.encode(_passwordController.text);
      final salt = utf8.encode(_saltController.text);

      final hashPtr = calloc<Uint8>(_hashLen);
      try {
        final params = argon2.Argon2RawParams(
          tCost: _tCost,
          mCost: _mCost,
          parallelism: _parallelism,
          password: Uint8List.fromList(password),
          salt: Uint8List.fromList(salt),
          hashLen: _hashLen,
          hash: hashPtr.cast<Void>(),
        );

        final nativeArgon2 = argon2.NativeArgon2();
        final result = nativeArgon2.argon2idHashRaw(params);

        stopwatch.stop();

        if (result == 0) {
          final hash = hashPtr.asTypedList(_hashLen);
          final hashHex = hash.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
          final hashB64 = base64Encode(hash);

          setState(() {
            _status = 'Success (${stopwatch.elapsedMilliseconds}ms)';
            _result = 'Parameters:\n'
                '  variant: argon2id\n'
                '  t=$_tCost, m=$_mCost, p=$_parallelism, hashLen=$_hashLen\n\n'
                'Password: "${_passwordController.text}"\n'
                'Salt: "${_saltController.text}"\n\n'
                'Hash (hex): $hashHex\n\n'
                'Hash (base64): $hashB64';
          });
        } else {
          setState(() {
            _status = 'Failed with error code: $result';
          });
        }
      } finally {
        calloc.free(hashPtr);
      }
    } catch (e) {
      stopwatch.stop();
      setState(() {
        _status = 'Error: $e';
      });
    } finally {
      setState(() {
        _running = false;
      });
    }
  }

  Future<void> _runAllVariants() async {
    setState(() {
      _running = true;
      _status = 'Running all variants...';
      _result = '';
    });

    try {
      final password = utf8.encode(_passwordController.text);
      final salt = utf8.encode(_saltController.text);
      final nativeArgon2 = argon2.NativeArgon2();
      final buffer = StringBuffer();

      for (final variant in ['argon2i', 'argon2d', 'argon2id']) {
        final hashPtr = calloc<Uint8>(_hashLen);
        final stopwatch = Stopwatch()..start();

        try {
          final params = argon2.Argon2RawParams(
            tCost: _tCost,
            mCost: _mCost,
            parallelism: _parallelism,
            password: Uint8List.fromList(password),
            salt: Uint8List.fromList(salt),
            hashLen: _hashLen,
            hash: hashPtr.cast<Void>(),
          );

          int result;
          switch (variant) {
            case 'argon2i':
              result = nativeArgon2.argon2iHashRaw(params);
            case 'argon2d':
              result = nativeArgon2.argon2dHashRaw(params);
            case 'argon2id':
              result = nativeArgon2.argon2idHashRaw(params);
            default:
              result = -1;
          }

          stopwatch.stop();

          if (result == 0) {
            final hash = hashPtr.asTypedList(_hashLen);
            final hex = hash.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
            buffer.writeln('$variant (${stopwatch.elapsedMilliseconds}ms):');
            buffer.writeln('  $hex');
            buffer.writeln();
          } else {
            buffer.writeln('$variant: FAILED (error $result)');
            buffer.writeln();
          }
        } finally {
          calloc.free(hashPtr);
        }
      }

      setState(() {
        _status = 'Done';
        _result = buffer.toString();
      });
    } catch (e) {
      setState(() {
        _status = 'Error: $e';
      });
    } finally {
      setState(() {
        _running = false;
      });
    }
  }

  @override
  void dispose() {
    _passwordController.dispose();
    _saltController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('Argon2 FFI Demo'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _passwordController,
              decoration: const InputDecoration(
                labelText: 'Password',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _saltController,
              decoration: const InputDecoration(
                labelText: 'Salt',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Params: t=$_tCost, m=$_mCost KiB, p=$_parallelism, hashLen=$_hashLen',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _running ? null : _runArgon2id,
                    icon: const Icon(Icons.lock),
                    label: const Text('Argon2id'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _running ? null : _runAllVariants,
                    icon: const Icon(Icons.compare),
                    label: const Text('All Variants'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              _status,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: _status.startsWith('Error') || _status.startsWith('Failed')
                        ? Colors.red
                        : null,
                  ),
            ),
            const SizedBox(height: 12),
            Expanded(
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: SingleChildScrollView(
                  child: SelectableText(
                    _result,
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 13,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
