import 'package:flutter/material.dart';

/// The more page.
class PhotosPage extends StatelessWidget {
  const PhotosPage({super.key});

  static const String path = '/photos';
  static const String name = 'Photos';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Photos'),
        leading: Builder(
          builder: (context) {
            return IconButton(
              icon: const Icon(Icons.menu),
              onPressed: () {
                Scaffold.of(context).openDrawer();
              },
              tooltip: "Menu",
            );
          },
        ),
        actions: [
          IconButton(
            onPressed: () {},
            icon: Icon(Icons.search),
            tooltip: "Search",
          ),
          IconButton(
            onPressed: () {},
            icon: Icon(Icons.more_vert),
            tooltip: "More operations",
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        heroTag: "photo_add",
        onPressed: () {},
        child: const Icon(Icons.add),
      ),
      body: GridView.count(
        primary: false,
        padding: const EdgeInsets.all(20),
        crossAxisSpacing: 10,
        mainAxisSpacing: 10,
        crossAxisCount: 2,
        children: <Widget>[
          Container(
            padding: const EdgeInsets.all(8),
            color: Colors.teal[100],
            child: const Text("He'd have you all unravel at the"),
          ),
          Container(
            padding: const EdgeInsets.all(8),
            color: Colors.teal[200],
            child: const Text('Heed not the rabble'),
          ),
          Container(
            padding: const EdgeInsets.all(8),
            color: Colors.teal[300],
            child: const Text('Sound of screams but the'),
          ),
          Container(
            padding: const EdgeInsets.all(8),
            color: Colors.teal[400],
            child: const Text('Who scream'),
          ),
          Container(
            padding: const EdgeInsets.all(8),
            color: Colors.teal[500],
            child: const Text('Revolution is coming...'),
          ),
          Container(
            padding: const EdgeInsets.all(8),
            color: Colors.teal[600],
            child: const Text('Revolution, they...'),
          ),
        ],
      ),
    );
  }
}
