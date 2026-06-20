import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:go_router/go_router.dart';
import 'package:introduction_screen/introduction_screen.dart';
import 'package:vector_graphics/vector_graphics.dart';

import 'onboarding_page.dart';

const pageDecoration = PageDecoration(
  imagePadding: EdgeInsets.only(top: 24, bottom: 24),
  bodyAlignment: Alignment.topRight,
  bodyTextStyle: TextStyle(fontSize: 14),
);

final introductionPages = [
  PageViewModel(
    title: "Comprehensive Secret Protection",
    bodyWidget: const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Text(
              "eDiting goes beyond passwords, providing comprehensive protection for all your sensitive information. Safely store and manage passwords, notes, photos, files, and more within our app's encrypted vault. Advanced algorithms like AES-256 and Argon2 ensure that your secrets remain secure and inaccessible to unauthorized individuals."),
        ],
      ),
    ),
    image: SvgPicture(
      AssetBytesLoader("assets/images/authentication-flatline-48804.svg.vec"),
    ),
    decoration: pageDecoration,
  ),
  PageViewModel(
    title: "Strong Encryption with AES-256 and Argon2",
    bodyWidget: const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Text(
              "Your secrets are encrypted with the highest level of security using AES-256 and Argon2. These industry-standard algorithms make it virtually impossible for anyone to decipher your encrypted data. Rest assured that your secrets are safe from brute-force and dictionary attacks, keeping them confidential and protected."),
        ],
      ),
    ),
    image: SvgPicture(
      AssetBytesLoader("assets/images/super-man-flatline.svg.vec"),
    ),
    decoration: pageDecoration,
  ),
  PageViewModel(
    title: "Cross-Platform Support",
    bodyWidget: const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Text(
              "Access and manage your secrets seamlessly across multiple platforms with eDiting SecureVault. Whether you're using iOS, Android, or desktop, our app synchronizes your encrypted data, ensuring that changes you make on one device are instantly reflected on all others. Enjoy a consistent and user-friendly experience, no matter which platform you're using."),
        ],
      ),
    ),
    image: SvgPicture(
      AssetBytesLoader("assets/images/transferwise-two-color.svg.vec"),
    ),
    decoration: pageDecoration,
  ),
];

class IntroductionPage extends StatelessWidget {
  const IntroductionPage({super.key});

  static const String path = '/introduction';
  static const String name = 'Introduction';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IntroductionScreen(
        pages: introductionPages,
        showSkipButton: true,
        showNextButton: true,
        showDoneButton: true,
        globalFooter: SizedBox(
          height: 40,
          child: const Text("Created by quillgen.com"),
        ),
        skip: const Text("Skip"),
        next: const Icon(Icons.arrow_right_alt),
        done: const Text("Start"),
        onDone: () {
          if (context.mounted) {
            context.goNamed(
              OnboardingPage.name,
              queryParameters: <String, String>{'skip': "true"},
            );
          }
        },
      ),
    );
  }
}
