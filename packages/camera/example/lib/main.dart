import 'dart:async';
import 'dart:io';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';

class CameraExampleHome extends StatefulWidget {
  @override
  _CameraExampleHomeState createState() {
    return new _CameraExampleHomeState();
  }
}

void logError(String code, String message) => print('Error: $code\nError Message: $message');

class _CameraExampleHomeState extends State<CameraExampleHome> {
  CameraController controller;
  String imagePath;
  String messageWidgetText;
  List<CameraDescription> cameras;

  @override
  void initState() {
    super.initState();
    cameras = <CameraDescription>[];
    messageWidgetText = "";
    try {
      // Requesting list of cameras
      getAllAvailableCameras().then((List<CameraDescription> result) {
        if (mounted) {
          setState(() => cameras = result);
        }
      });
    } on CameraException catch (e) {
      logError(e.code, e.description);
    }
  }

  @override
  Widget build(BuildContext context) {
    return new Scaffold(
      appBar: new AppBar(title: const Text('Camera example')),
      body: new Column(children: <Widget>[
        new Container(
          child: new Padding(
            padding: const EdgeInsets.all(1.0),
            child: new Center(
              child: new Column(mainAxisAlignment: MainAxisAlignment.center, children: buildPreviewChildren()),
            ),
          ),
          // Size of the container as wide as the device screen.
          width: MediaQuery.of(context).size.width,
          decoration: new BoxDecoration(
            color: Colors.black,
            border: new Border.all(
              color: Colors.redAccent,
              width: controller != null && controller.value.opened && controller.value.recordingVideo ? 3.0 : 0.0,
            ),
          ),
        ),
        new Padding(
            padding: const EdgeInsets.all(5.0),
            child: new Row(mainAxisAlignment: MainAxisAlignment.start, children: buildControlsChildren())),
        buildMessageWidget(),
      ]),
      bottomNavigationBar: (controller == null)
          ? null
          : new Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, mainAxisSize: MainAxisSize.max, children: <Widget>[
              new IconButton(
                icon: new Icon(Icons.camera_alt),
                color: Colors.blue,
                onPressed: controller.value.opened && !controller.value.recordingVideo ? onTakePictureButtonPressed : null,
              ),
              new IconButton(
                icon: new Icon(Icons.videocam),
                color: Colors.blue,
                onPressed: controller.value.opened && !controller.value.recordingVideo ? onVideoRecordButtonPressed : null,
              ),
              new IconButton(
                icon: new Icon(Icons.stop),
                color: Colors.red,
                onPressed: controller.value.opened && controller.value.recordingVideo ? onStopButtonPressed : null,
              )
            ]),
    );
  }

  List<Widget> buildControlsChildren() {
    final List<Widget> controlsChildren = <Widget>[];
    final List<Widget> cameraList = <Widget>[];
    if (cameras.isEmpty) {
      cameraList.add(const Text('No camera found'));
    } else {
      for (CameraDescription cameraDescription in cameras) {
        cameraList.add(
          new SizedBox(
            width: 90.0,
            child: new RadioListTile<CameraDescription>(
                title: new Icon(getCameraLensIcon(cameraDescription.lensDirection)),
                groupValue: controller?.description,
                value: cameraDescription,
                onChanged: (CameraDescription newValue) async => onNewCameraSelected(newValue)),
          ),
        );
      }
    }
    // Add the cameras to the main controls widget.
    controlsChildren.add(new Row(children: cameraList));
    if (imagePath != null) {
      controlsChildren.add(buildImagePreviewWidget());
    }
    return controlsChildren;
  }

  Widget buildImagePreviewWidget() {
    return new Expanded(
      child: new Align(
        alignment: Alignment.centerRight,
        child: new SizedBox(
          child: new Image.file(new File(imagePath)),
          width: 64.0,
          height: 64.0,
        ),
      ),
    );
  }

  Widget buildMessageWidget() {
    if (controller == null) {
      return const Padding(
        padding: const EdgeInsets.all(1.0),
        child: const Text('Choose a camera'),
      );
    }
    if (controller.value.errorDescription != null) {
      messageWidgetText = 'Camera error ${controller.value.errorDescription}';
    } else if (messageWidgetText == '') {
      messageWidgetText = 'Take a picture or record a video';
    }
    return new Padding(
        padding: const EdgeInsets.all(1.0),
        child: new Text('$messageWidgetText', textAlign: TextAlign.center, overflow: TextOverflow.ellipsis, softWrap: true));
  }

  String getCurrentDate() => new DateFormat('yyyyMMdd_HHmmss').format(new DateTime.now());

  IconData getCameraLensIcon(CameraLensDirection direction) {
    switch (direction) {
      case CameraLensDirection.back:
        return Icons.camera_rear;
      case CameraLensDirection.front:
        return Icons.camera_front;
      case CameraLensDirection.external:
        return Icons.camera;
    }
    throw new ArgumentError('Unknown lens direction');
  }

  void onNewCameraSelected(CameraDescription cameraDescription) async {
    if (controller != null) {
      await controller.dispose();
    }
    controller = new CameraController(cameraDescription, ResolutionPreset.high);
    // If the controller is updated then update the UI.
    controller.addListener(() {
      if (mounted) {
        setState(() => messageWidgetText = '');
      }
    });

    try {
      await controller.openCamera();
    } on CameraException catch (e) {
      logError(e.code, e.description);
    }

    if (mounted) {
      setState(() {});
    }
  }

  void onTakePictureButtonPressed() {
    takePicture().then((String filePath) {
      if (mounted) {
        setState(() {
          imagePath = filePath;
          messageWidgetText = 'Picture saved to $filePath';
        });
      }
    });
  }

  void onVideoRecordButtonPressed() {
    startVideoRecording().then((String filePath) {
      if (mounted) {
        setState(() {
          messageWidgetText = 'Saving video to $filePath';
        });
      }
    });
  }

  void onStopButtonPressed() {
    stopVideoRecording().then((_) {
      if (mounted) {
        setState(() => messageWidgetText = 'Recording done!');
      }
    });
  }

  Future<String> startVideoRecording() async {
    if (!controller.value.opened) {
      return '';
    }
    final Directory extDir = await getApplicationDocumentsDirectory();
    final String dirPath = '${extDir.path}/Movies/flutter_test';
    await new Directory(dirPath).create(recursive: true);
    final String filePath = '$dirPath/${getCurrentDate()}.mp4';
    try {
      await controller.startVideoRecording(filePath);
    } on CameraException catch (e) {
      logError(e.code, e.description);
    }
    return filePath;
  }

  Future<Null> stopVideoRecording() async {
    if (controller.value.opened) {
      try {
        await controller.stopVideoRecording();
      } on CameraException catch (e) {
        logError(e.code, e.description);
      }
    }
  }

  Future<String> takePicture() async {
    if (!controller.value.opened) {
      return '';
    }
    final Directory extDir = await getApplicationDocumentsDirectory();
    final String dirPath = '${extDir.path}/Pictures/flutter_test';
    await new Directory(dirPath).create(recursive: true);
    final String filePath = '$dirPath/${getCurrentDate()}.jpg';
    try {
      await controller.takePicture(filePath);
    } on CameraException catch (e) {
      logError(e.code, e.description);
    }
    return filePath;
  }

  List<Widget> buildPreviewChildren() {
    final List<Widget> previewChildren = <Widget>[];

    // Depending on controller state display a message or the camera preview.
    if (controller == null || !controller.value.initialized) {
      previewChildren.add(new Text(
        'Tap a camera',
        style: new TextStyle(
          color: Colors.white,
          fontSize: 24.0,
          fontWeight: FontWeight.w900,
        ),
      ));
    } else if (controller.value.hasError) {
      previewChildren.add(
        new Text('Camera error ${controller.value.errorDescription}'),
      );
    } else {
      previewChildren.add(
        new Container(
          // Handle the preview depending on the aspect ratio of the camera view.
          child: new AspectRatio(
            aspectRatio: controller.value.aspectRatio,
            child: new CameraPreview(controller),
          ),
          height: (MediaQuery.of(context).size.height - 230.0),
          color: Colors.black,
        ),
      );
    }
    return previewChildren;
  }
}

class CameraApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      home: new CameraExampleHome(),
    );
  }
}

Future<Null> main() async {
  runApp(new CameraApp());
}
