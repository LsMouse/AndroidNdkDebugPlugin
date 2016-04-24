# AndroidNdkDebugPlugin

ADT now can be used to debug android so file in sourcecode mode, but still bugs and flaws
Android Studio can be used to debug android project with jni code, by run ndk-debug configuration in "Edit Run/Debug Configuration"
during the debugging, it will first compile so and apk files, then follows:

  adb push 1.apk /data/local/tmp/1.apk
  am start -D -n com.example.test/.MainActivity
  waiting for app's status to be debuggable
  set java-layer-breakpoints
  adb push lldb_server 
  ./lldb_server ..............setup lldb server
  lldbfrontend.exe ........... connect to lldb server
  send cmmand to lldb_server to attach to some pid
  set jni-layer breakpoints 
  remote connect to app and resume app running

I cracked android-ndk.jar to make the process up there directly establish jni-layer debug, and finally it works!!!
you can use the source code i cracked by follow steps:
  export the eclipse project to android-ndk.jar
  replace android-ndk.jar used by android studio, or install it as a plugin(must uninstall the origin), remember to bake up
  restart android studio, open an existing android project with jni code
  start app by hand, and make changes in "Run/Debug Configurations" in android studio, you shall see a process list
  select the process you have just launched, press ok, and you will return to main window
  press debug button, wait for a moment, you will see some info about attached to app
  enjoy it!
