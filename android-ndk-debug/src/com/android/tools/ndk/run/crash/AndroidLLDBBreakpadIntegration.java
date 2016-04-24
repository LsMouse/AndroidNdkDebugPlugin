package com.android.tools.ndk.run.crash;

import com.android.tools.ndk.run.crash.NativeClientCrashException;
import com.android.tools.ndk.run.lldb.AndroidLLDBDriver;
import com.google.common.io.PatternFilenameFilter;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class AndroidLLDBBreakpadIntegration {
   private static final Logger LOG = Logger.getInstance(AndroidLLDBBreakpadIntegration.class);
   private static final Object MINIDUMP_MUTEX = new Object();

   private static File getMinidumpDir() {
      File folder = new File(PathManager.getTempPath(), "lldb");
      if(folder.exists()) {
         if(!folder.isDirectory()) {
            return null;
         }
      } else if(!folder.mkdir()) {
         return null;
      }

      return folder;
   }

   private static int checkForMinidumps(String message) {
      File dir = getMinidumpDir();
      if(dir == null) {
         return 0;
      } else {
         File[] minidumps = dir.listFiles(new PatternFilenameFilter(".*\\.dmp"));
         int count = 0;
         File[] arr$ = minidumps;
         int len$ = minidumps.length;

         for(int i$ = 0; i$ < len$; ++i$) {
            File minidump = arr$[i$];

            try {
               LOG.error(NativeClientCrashException.create(message, minidump));
               ++count;
            } catch (IOException var9) {
               LOG.error("Error reading native client crash dump.", var9);
            }

            minidump.delete();
         }

         return count;
      }
   }

   public static void setUpRunEnvironment(Map<String,String> env) {
   }

   public static boolean checkForStaleMinidumps() {
      synchronized(MINIDUMP_MUTEX) {
         return checkForMinidumps("Previous native debug session crashed") > 0;
      }
   }

   public static boolean checkForCrashes(AndroidLLDBDriver driver) {
      synchronized(MINIDUMP_MUTEX) {
         if(checkForMinidumps("Native debugging client crashed") > 0) {
            driver.setCrashed(true);
         }

         return driver.isCrashed();
      }
   }

   public static void monitorForCrashes(final AndroidLLDBDriver driver) {
      driver.getProcessHandler().addProcessListener(new ProcessAdapter() {
         public void processTerminated(ProcessEvent event) {
            AndroidLLDBBreakpadIntegration.checkForCrashes(driver);
         }
      });
   }
}
