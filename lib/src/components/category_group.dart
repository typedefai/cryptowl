import 'package:flutter/material.dart';

class CategoryGroup extends StatelessWidget {
  final String name;

  const CategoryGroup({super.key, required this.name});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        InkWell(
          child: Icon(
            Icons.keyboard_arrow_right,
            color: Colors.grey,
          ),
          onTap: () {},
        ),
        Padding(
          padding: EdgeInsets.only(left: 10),
          child: Text(
            name,
            style: TextStyle(
              color: Colors.grey,
            ),
          ),
        ),
        Expanded(child: Container()),
        Padding(
          padding: EdgeInsets.only(right: 30),
          child: InkWell(
            onTap: () {},
            child: Icon(
              Icons.add,
              color: Colors.grey,
            ),
          ),
        )
      ],
    );
  }
}
