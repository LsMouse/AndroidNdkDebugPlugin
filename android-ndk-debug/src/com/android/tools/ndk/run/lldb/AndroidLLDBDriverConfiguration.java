package com.android.tools.ndk.run.lldb;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.run.AndroidRunningState;
import com.android.tools.ndk.GradleWorkspace;
import com.android.tools.ndk.ModulePathManager;
import com.android.tools.ndk.NdkHelper;
import com.android.tools.ndk.run.AndroidNativeRunConfiguration;
import com.android.tools.ndk.run.crash.AndroidLLDBBreakpadIntegration;
import com.android.tools.ndk.run.lldb.AndroidLLDBDriver;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.cidr.execution.Installer;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;
import com.jetbrains.cidr.execution.debugger.backend.LLDBDriverConfiguration;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver.Handler;
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriver;
import java.io.File;
import java.util.Map;

public class AndroidLLDBDriverConfiguration extends LLDBDriverConfiguration {
   private final AndroidRunningState myState;
   private final IDevice myDevice;
   private final String myPlatformDomainSocketPath;
   private final String[] myStartupCommands;

   public AndroidLLDBDriverConfiguration(AndroidRunningState androidRunningState, IDevice device, String platformDomainSocketPath, String[] startupCommands) {
      this.myState = androidRunningState;
      this.myDevice = device;
      this.myPlatformDomainSocketPath = platformDomainSocketPath;
      this.myStartupCommands = startupCommands;
   }

   public AndroidRunningState getState() {
      return this.myState;
   }

   public IDevice getDevice() {
      return this.myDevice;
   }

   public String getPlatformDomainSocketPath() {
      return this.myPlatformDomainSocketPath;
   }

   public String[] getStartupCommands() {
      return this.myStartupCommands;
   }

   @Override
   public DebuggerDriver createDriver(Handler handler) {
      AndroidLLDBDriver driver = new AndroidLLDBDriver(handler, this);
      return driver;
   }

   private File getLLDBPlatformBinFile(String relativePath) {
      String selector;
      if(SystemInfo.isWindows){
    	  selector = "win";
      }
      else if(SystemInfo.isMac){
    	  selector = "mac";
      }
      else{
    	  selector = "linux";
      }
      return ModulePathManager.getLLDBPlatformBinFile(selector, relativePath);
   }

   @Override
   protected File getLLDBBinFile(String relativePath) {
      return this.getLLDBPlatformBinFile((new File("bin", relativePath)).getPath());
   }

   private File getLLDBLibDir() {
      return this.getLLDBPlatformBinFile((new File("lib")).getPath());
   }

   private File getLLDBLibFile(String relativePath) {
      return new File(this.getLLDBLibDir(), relativePath);
   }

   @Override
   protected File getLLDBFrameworkFile() {
	   if(SystemInfo.isWindows){
		   return getLLDBBinFile("liblldb.dll");
	   }
	   else if(SystemInfo.isLinux){
		   return getLLDBLibFile("liblldb.so.3.8");
	   }
	   else{
		   return getLLDBPlatformBinFile("LLDB.framework");
	   }
   }

   @Override
   protected void configureDriverCommandLine(GeneralCommandLine result) {
      AndroidNativeRunConfiguration config = (AndroidNativeRunConfiguration)this.myState.getConfiguration();
      String workingDir = config.getWorkingDir();
      if(!workingDir.isEmpty()) {
         result.withWorkDirectory(workingDir);
      }

      if(Projects.isBuildWithGradle(this.myState.getModule().getProject())) {
         Map<String,String> environment = result.getEnvironment();
         File galaPath = ModulePathManager.getLLDBStlPrintersFolder();
         if(galaPath.exists()) {
            environment.put("AS_GALA_PATH", galaPath.getAbsolutePath());
         }

         File libStdCxxPrinterPath = NdkHelper.getLibStdCxxPrintersPath(GradleWorkspace.getInstance(this.myState.getModule().getProject()).getNdkPath(), "4.9");
         if(libStdCxxPrinterPath.exists()) {
            environment.put("AS_LIBSTDCXX_PRINTER_PATH", libStdCxxPrinterPath.getAbsolutePath());
         }

      }
   }

   @Override
   public GeneralCommandLine createDriverCommandLine(DebuggerDriver driver, Installer installer, String architecture) throws ExecutionException {
      File lldbFrameworkFile = this.getLLDBFrameworkFile();
      if(!lldbFrameworkFile.exists()) {
         throw new ExecutionException(lldbFrameworkFile + " not found");
      } else {
         File frontendExecutable = this.getLLDBBinFile(SystemInfo.isWindows?"LLDBFrontend.exe":"LLDBFrontend");
         if(!frontendExecutable.exists()) {
            throw new ExecutionException(frontendExecutable.getAbsolutePath() + " not found");
         } else {
            GeneralCommandLine result = new GeneralCommandLine();
            result.setExePath(frontendExecutable.getAbsolutePath());
            result.addParameter(String.valueOf(((LLDBDriver)driver).getPort()));
            setupCommonParameters(result);
            Map<String,String> env = result.getEnvironment();
            if(SystemInfo.isLinux) {
               env.put("LD_LIBRARY_PATH", this.getLLDBLibDir().getAbsolutePath());
            } else if(SystemInfo.isMac) {
               env.put("DYLD_FRAMEWORK_PATH", lldbFrameworkFile.getParent());
            }

            if(!SystemInfo.isMac) {
               env.put("PYTHONHOME", this.getLLDBLibDir().getParent());
            }

            AndroidLLDBBreakpadIntegration.setUpRunEnvironment(env);
            this.configureDriverCommandLine(result);
            return result;
         }
      }
   }
}
