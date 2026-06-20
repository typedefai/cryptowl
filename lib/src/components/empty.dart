import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:vector_graphics/vector_graphics.dart';

class Empty extends StatelessWidget {
  final String? tip;

  const Empty({super.key, this.tip});
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          SvgPicture(
            AssetBytesLoader("assets/images/cryptowl-full.svg.vec"),
            height: 50,
          ),
          Text(tip ?? "There are no items here.")
        ],
      ),
    );
  }
}
