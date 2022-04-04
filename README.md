# Eminisce Android Application for Library Borrowing using Fingerprint + Face Recognition
### Part of Eminisce Group's CSIT321 Final Year Project<br>Topic: Biometric Authentication<br>Made with Android Studio. <br>Languages: Java
This is the Android application part of our group's final year project: a library management suite that supports borrowing books using biometric authentication, particularly fingerprint scanning and facial recognition. The face recognition is done using a simple camera and a single photo of a person's face.

The Android application is meant to be run on a self-service checkout machine in a library. The application enables a library user to scan their fingerprint plus their face to authenticate their permission to borrow books from the library. This helps remove the use of a library card to avoid library users' frustration when forgetting to bring their cards. Additionally, the application supports scanning books' barcodes using the device's built-in camera.

A library user must have had their fingerprint and face enrolled in the database of the Eminisce Web Application (https://github.com/digitalk064/eminisce) for the app to successfully authenticate the user.

The Android application will make API calls to the specified URL in the code (please see instructions below on how to set up your own custom URL), the API is part of the Eminisce Web Application. These calls are to: download books and biometric data from the database, send borrow requests and receive confirmation. The Web Application uses token authorization and the token must be put in the Android application's code (see instructions below) for it to work.

Note that biometric data enrollments are NOT done through this Android app. It is done through the Web Application.

***The Android application requires a compatible fingerprint scanner. We use ZKTeco ZK Live 20R but most of ZKTeco's fingerprint scanners should work. Alternatively you can turn on debug mode which allows skipping the fingerprint scanning step by setting DEBUG_MODE to true at line 88 in the MainActivity.java file. See the instructions below for more information.***

## Instructions
**Requires companion Android app: https://github.com/digitalk064/eminisce_android**  
 Please find here our User Manual PDF file: [Eminisce_User_Manual.pdf](docs/Eminisce_User_Manual.pdf)  
 ***Read from page 30 for instructions on setting up the Android application.***
 
## Developers
**Le Vu Nguyen Khanh**  
**Loz Zhen Yuan**  
**Teo Wen Xuan**

## Credits
Thanks to these articles on how to train and use FaceNet models:  
https://towardsdatascience.com/using-facenet-for-on-device-face-recognition-with-android-f84e36e19761  
https://machinelearningmastery.com/how-to-develop-a-face-recognition-system-using-facenet-in-keras-and-an-svm-classifier/  
Face recognition model trained with VGGFace2 dataset.

Huge thanks to Shubham Panchal's repo on how to really integrate FaceNet in an Android application: https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android
