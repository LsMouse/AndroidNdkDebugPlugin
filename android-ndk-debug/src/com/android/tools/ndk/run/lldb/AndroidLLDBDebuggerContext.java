package com.android.tools.ndk.run.lldb;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.SyncService.FileStat;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.run.AndroidRunningState;
import com.android.tools.idea.run.ErrorMatchingReceiver;
import com.android.tools.ndk.ModulePathManager;
import com.android.tools.ndk.run.AndroidNativeDebugProcess;
import com.android.tools.ndk.run.AndroidNativeRunConfiguration;
import com.android.tools.ndk.run.ClientShellHelper;
import com.android.tools.ndk.run.DebuggerContext;
import com.android.tools.ndk.run.lldb.AndroidLLDBDriverConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AndroidLLDBDebuggerContext extends DebuggerContext {
   private static final String LLDB_SERVER_START_SCRIPT = "start_lldb_server.sh";
   private static final String LLDB_SERVER = "lldb-server";
   private static final String DEVICE_TEMP_PATH = "/data/local/tmp";
   private static final Map<Abi,Abi> ABI_MAPPINGS;
   private String myPlatformDomainSocketPath;
   private static final Logger LOG;

   public DebuggerDriverConfiguration getDebuggerDriverConfiguration(AndroidRunningState androidRunningState, IDevice device) {
      return new AndroidLLDBDriverConfiguration(androidRunningState, device, myPlatformDomainSocketPath, null);
   }

   @Override
   public void startServer(AndroidRunningState state, Client client, AtomicBoolean cancelled) throws DebuggerContext.StartServerException {
      try {
         this.launchLLDBServer(state, client, cancelled);
      } catch (Exception var5) {
         throw new DebuggerContext.StartServerException(var5);
      }
   }

   private void launchLLDBServer(AndroidRunningState state, Client client, final AtomicBoolean cancelled) 
		   throws IOException, AdbCommandRejectedException, TimeoutException, SyncException, ShellCommandUnresponsiveException {
      final IDevice device = client.getDevice();
      AndroidNativeRunConfiguration configuration = (AndroidNativeRunConfiguration)state.getConfiguration();
      File lldbServer = findLLDBServer(state, device);
      if(lldbServer == null) {
         LOG.error("LLDB server not found");
         throw new IllegalStateException("LLDB server not found");
      } else {
         ClientShellHelper clientShell = new ClientShellHelper(client, state.getPackageName());
         String packageLLDBDir = clientShell.getPackageFolder() + "/lldb";
         String packageLLDBBinDir = packageLLDBDir + "/bin";
         this.getAttachProgressReporter().step("Creating device directories");
         LOG.info("Creating LLDB bin directory " + packageLLDBBinDir);
         ErrorMatchingReceiver receiver = new ErrorMatchingReceiver(state.getStoppedRef());
         state.executeDeviceCommandAndWriteToConsole(device, clientShell.runAs("mkdir " + packageLLDBDir), receiver);
         String runAsBrokenMessage = String.format("run-as: Package \'%s\' is unknown", new Object[]{state.getPackageName()});
         if(receiver.getOutput().toString().startsWith(runAsBrokenMessage)) {
            LOG.error("run-as is broken (please see http://b.android.com/187955 for details)");
            throw new IllegalStateException("run-as is broken (please see http://b.android.com/187955 for details)");
         } else {
            state.executeDeviceCommandAndWriteToConsole(device, clientShell.runAs("mkdir " + packageLLDBBinDir), new ErrorMatchingReceiver(state.getStoppedRef()));
            LOG.info("Pushing LLDB files to the device");
            this.getAttachProgressReporter().step("Pushing binaries to the device");
            this.pushDebuggerFile(state, device, clientShell, lldbServer, packageLLDBBinDir);
            File startLLLDBServerScript = ModulePathManager.getAndroidLLDBBinFile(LLDB_SERVER_START_SCRIPT);
            String startScriptPath = this.pushDebuggerFile(state, device, clientShell, startLLLDBServerScript, packageLLDBBinDir);
            this.myPlatformDomainSocketPath = String.format("%s/tmp/platform-%d.sock", new Object[]{packageLLDBDir, Long.valueOf(System.currentTimeMillis())});
            final String startCmd = clientShell.runAs(String.format("%s %s %s \"%s\"", new Object[]{startScriptPath, packageLLDBDir, this.myPlatformDomainSocketPath, configuration.getTargetLoggingChannels()}));
            LOG.info("Starting LLDB server: " + startCmd);
            this.getAttachProgressReporter().step("Starting LLDB server");
            state.getPrinter().stdout("Starting LLDB server: " + startCmd);
            ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
               public void run() {
                  try {
                     ErrorMatchingReceiver e = new ErrorMatchingReceiver(cancelled);
                     device.executeShellCommand(startCmd, e, 0L, TimeUnit.DAYS);
                     LOG.info("LLDB server has exited: " + e.getOutput());
                  } catch (Exception var2) {
                     LOG.error("LLDB server has failed: ", var2);
                  }

               }
            });
         }
      }
   }

   private static File getAndroidLLDBBinFile(Abi abi) {
      return ModulePathManager.getAndroidLLDBBinFile((new File(abi.toString(), LLDB_SERVER)).getPath());
   }

   private static File findLLDBServer(AndroidRunningState state, IDevice device) {
      List<String> abis = device.getAbis();
      Iterator<String> abi = abis.iterator();

      while(abi.hasNext()) {
         String lldbServerFile = abi.next();
         Abi abi1 = Abi.getEnum(lldbServerFile);
         if(abi1 == null) {
            LOG.error("Failed to get abi by name: " + lldbServerFile);
         } else if(!AndroidNativeDebugProcess.getSymbolsDir(state, Collections.singletonList(abi1)).isEmpty()) {
            File lldbServerFile1 = getServerFileByAbi(abi1);
            if(lldbServerFile1 != null) {
               return lldbServerFile1;
            }
         }
      }

      if(!abis.isEmpty()) {
         Abi abi2 = Abi.getEnum((String)abis.get(0));
         File lldbServerFile2 = getServerFileByAbi(abi2);
         if(lldbServerFile2 != null) {
            return lldbServerFile2;
         }
      }

      return null;
   }

   private static File getServerFileByAbi(Abi abi) {
      File lldbServerFile = getAndroidLLDBBinFile(abi);
      if(lldbServerFile.exists()) {
         return lldbServerFile;
      } else {
         Abi mappedAbi = (Abi)ABI_MAPPINGS.get(abi);
         if(mappedAbi != null) {
            lldbServerFile = getAndroidLLDBBinFile(mappedAbi);
            if(lldbServerFile.exists()) {
               return lldbServerFile;
            }
         }

         return null;
      }
   }

   private static FileStat statRemoteFile(IDevice device, String remoteFile) 
		   throws TimeoutException, AdbCommandRejectedException, IOException {
      SyncService sync = null;

      try {
         sync = device.getSyncService();
         if(sync != null) {
            FileStat var3 = sync.statFile(remoteFile);
            return var3;
         }

         LOG.error("Failed to get SyncService");
      } finally {
         if(sync != null) {
            sync.close();
         }

      }

      return null;
   }

   private String pushDebuggerFile(AndroidRunningState state, IDevice device, ClientShellHelper clientShell, File localFile, String destDir) 
		   throws IOException, AdbCommandRejectedException, TimeoutException, SyncException, ShellCommandUnresponsiveException {
      String fileName = localFile.getName();
      String destFile = destDir + "/" + fileName;
      String tmpDestFile = DEVICE_TEMP_PATH + fileName;
      Date localFileLmt = new Date(localFile.lastModified() / 1000L * 1000L);
      FileStat destFileStat = statRemoteFile(device, tmpDestFile);
      if(destFileStat != null && destFileStat.getMode() != 0 && localFileLmt.equals(destFileStat.getLastModified()) && localFile.length() == (long)destFileStat.getSize()) {
         LOG.info("Remote file " + tmpDestFile + " is up-to-date.");
      } else {
         LOG.info("Pushing to the device: " + localFile + " => " + tmpDestFile);
         device.pushFile(localFile.getAbsolutePath(), tmpDestFile);
      }

      ErrorMatchingReceiver receiver = new ErrorMatchingReceiver(state.getStoppedRef());
      String copyChmodCommand = "cat " + tmpDestFile + " | " + clientShell.runAs(String.format("sh -c \'cat > %s; chmod 700 %s\'", new Object[]{destFile, destFile}));
      LOG.info("Copying to app folder: " + tmpDestFile + " => " + destFile);
      LOG.info("Command: " + copyChmodCommand);
      state.executeDeviceCommandAndWriteToConsole(device, copyChmodCommand, receiver);
      if(receiver.hasError()) {
         throw new IOException("Command failed: " + copyChmodCommand);
      } else {
         return destFile;
      }
   }

   static {
      ABI_MAPPINGS = Collections.singletonMap(Abi.ARMEABI_V7A, Abi.ARMEABI);
      LOG = Logger.getInstance(AndroidLLDBDebuggerContext.class);
   }
}
