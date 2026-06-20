import 'package:flutter/material.dart';
import 'package:remixicon/remixicon.dart';

class ListItem extends StatelessWidget {
  final String title;
  final String content;
  final void Function()? onTap;

  const ListItem(
      {super.key, required this.title, required this.content, this.onTap});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      dense: true,
      contentPadding: EdgeInsets.only(left: 10, right: 10),
      leading: Icon(RemixIcons.list_check_2,
          color: Theme.of(context).colorScheme.primary),
      titleAlignment: ListTileTitleAlignment.top,
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: EdgeInsetsGeometry.only(bottom: 5),
            child: Text(
              title,
              overflow: TextOverflow.ellipsis,
              maxLines: 2,
            ),
          ),
          Text(
            content,
            style: Theme.of(context).textTheme.labelSmall,
          )
        ],
      ),
      onTap: onTap,
      shape: Border(
        bottom: BorderSide(
          color: const Color.fromARGB(255, 233, 231, 231),
        ),
      ),
    );
  }
}
