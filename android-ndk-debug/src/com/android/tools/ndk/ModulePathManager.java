package com.android.tools.ndk;

import com.intellij.openapi.application.PathManager;
import java.io.File;

public class ModulePathManager {
   public static File getLLDBBinFile(String relativePath) {
      File lldbBin = new File(PathManager.getBinPath(), "lldb");
      File file = new File(lldbBin, relativePath);
      return file.exists()?file:new File(new File(PathManager.getHomePath(), "../vendor/google/android-ndk/bin/lldb"), relativePath);
   }

   public static File getLLDBPlatformBinFile(String platform, String relativePath) {
      return getLLDBBinFile((new File(platform, relativePath)).getPath());
   }

   public static File getAndroidLLDBBinFile(String relativePath) {
      return getLLDBPlatformBinFile("android", relativePath);
   }

   public static File getLLDBSharedBinFile(String relativePath) {
      return getLLDBBinFile((new File("shared", relativePath)).getPath());
   }

   public static File getLLDBStlPrintersFolder() {
      return getLLDBSharedBinFile("stl_printers");
   }

   public static File getLLDBStlPrintersBinFile(String relativePath) {
      return new File(getLLDBStlPrintersFolder(), relativePath);
   }
}
