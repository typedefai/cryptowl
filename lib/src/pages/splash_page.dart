import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:vector_graphics/vector_graphics.dart';

class SplashPage extends StatelessWidget {
  const SplashPage({super.key});

  static const String path = '/splash';
  static const String name = 'Splash';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: const Center(
        child: SvgPicture(
          AssetBytesLoader("assets/images/cryptowl-full.svg.vec"),
          height: 50,
        ),
      ),
    );
  }
}
