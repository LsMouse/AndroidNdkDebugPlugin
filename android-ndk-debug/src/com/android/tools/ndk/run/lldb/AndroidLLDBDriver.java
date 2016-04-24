package com.android.tools.ndk.run.lldb;

import com.android.ddmlib.IDevice;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.editors.navigation.NavigationEditorUtils;
import com.android.tools.idea.run.AndroidRunningState;
import com.android.tools.ndk.ModulePathManager;
import com.android.tools.ndk.run.AndroidNativeDebugProcess;
import com.android.tools.ndk.run.crash.AndroidLLDBBreakpadIntegration;
import com.android.tools.ndk.run.lldb.AndroidLLDBDriverConfiguration;
import com.android.tools.ndk.run.lldb.JavaCallSignature;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.jetbrains.cidr.execution.Installer;
import com.jetbrains.cidr.execution.debugger.backend.LLFrame;
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriver;
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBEntityNotValidException;
import com.jetbrains.cidr.execution.debugger.backend.lldb.ProtobufMessageFactory;
import com.jetbrains.cidr.execution.debugger.backend.lldb.auto_generated.Model.LLDBFrame;
import com.jetbrains.cidr.execution.debugger.backend.lldb.auto_generated.Protocol.CompositeRequest;
import com.jetbrains.cidr.execution.debugger.backend.lldb.auto_generated.Protocol.ConnectPlatform_Res;
import com.jetbrains.cidr.execution.debugger.backend.lldb.auto_generated.Protocol.CreateTarget_Res;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.android.facet.IdeaSourceProvider;

public class AndroidLLDBDriver extends LLDBDriver {
   private static final String PLATFORM_NAME = "remote-android";
   private static final String ENV_VAR_PREFIX = "ANDROIDSTUDIO_LLDB_EXTRA_CMD_";
   private static final String[] STARTUP_SCRIPTS = new String[]{
		   ModulePathManager.getLLDBSharedBinFile("init_script").getPath(), 
		   ModulePathManager.getLLDBStlPrintersBinFile("load_script").getPath()
	};
   private static final int CONNECT_PLATFORM_NUM_RETRIES = 10;
   private static final int CONNECT_PLATFORM_TIMEOUT_MSECS = 500;
   private final AndroidRunningState myState;
   private final List<File> mySymDirs;
   private final String myDeviceSN;
   private final String myPlatformDomainSocketPath;
   private final String[] myStartupCommands;
   private boolean myCrashed = false;
   private static final Logger LOG = Logger.getInstance(AndroidLLDBDriver.class);

   public AndroidLLDBDriver(Handler handler, AndroidLLDBDriverConfiguration configuration) {
      super(handler, configuration);
      this.myState = configuration.getState();
      IDevice device = configuration.getDevice();
      this.myDeviceSN = device.getSerialNumber();
      this.myPlatformDomainSocketPath = configuration.getPlatformDomainSocketPath();
      this.myStartupCommands = configuration.getStartupCommands();
      ArrayList<Abi> abis = Lists.newArrayListWithExpectedSize(device.getAbis().size());
      Iterator<String> iter = device.getAbis().iterator();

      while(iter.hasNext()) {
         String abiStr = iter.next();
         Abi abi = Abi.getEnum(abiStr);
         if(abi != null) {
            abis.add(abi);
         } else {
            LOG.error("Failed to find ABI by name " + abiStr);
         }
      }

      this.mySymDirs = AndroidNativeDebugProcess.getSymbolsDir(this.myState, abis);
      if(this.mySymDirs.isEmpty()) {
         LOG.warn("No symbol directories found");
         this.myState.getPrinter().stderr("Attention! No symbol directories found - please check your native debug configuration");
      }

   }

   @Override
   public void loadForRemote(File deviceSupport) throws ExecutionException {
      LOG.debug("Load startup scripts");
      this.loadStartupScripts();
      LOG.debug("run console commands from environment");
      this.runEnvCommands();
      this.runStartupCommands();
      LOG.debug("createEmptyTarget");
      this.createEmptyTarget();
      LOG.debug("connectPlatform");
      LLDBEntityNotValidException lastException = null;
      int searchPaths = 0;

      while(searchPaths < CONNECT_PLATFORM_NUM_RETRIES) {
         try {
            this.connectPlatform();
            lastException = null;
            break;
         } catch (LLDBEntityNotValidException var7) {
            lastException = var7;
            LOG.warn("failed to connect platform - retrying");

            try {
               Thread.sleep(CONNECT_PLATFORM_TIMEOUT_MSECS);
            } catch (InterruptedException var6) {
               ;
            }

            ++searchPaths;
         }
      }

      if(lastException != null) {
         throw lastException;
      } else {
         if(!this.mySymDirs.isEmpty()) {
            ArrayList<String> var8 = Lists.newArrayListWithExpectedSize(this.mySymDirs.size());
            Iterator<File> searchPathsStr = this.mySymDirs.iterator();

            while(searchPathsStr.hasNext()) {
               File symDir = (File)searchPathsStr.next();
               var8.add("\"" + symDir.getAbsolutePath() + "\"");
            }

            String var9 = StringUtil.join(var8, " ");
            LOG.info("Set target.exec-search-paths: " + var9);
            this.executeConsoleCommand("settings set target.exec-search-paths " + var9);
         }

      }
   }

   private void runStartupCommands() throws ExecutionException {
      if(myStartupCommands != null) {
         for(int i = 0; i < myStartupCommands.length; ++i) {
            String cmd = myStartupCommands[i];
            LOG.info("Startup command: " + cmd);
            this.executeConsoleCommand(cmd);
         }

      }
   }

   @Override
   public boolean supportsWatchpoints() {
      return false;
   }

   private void runEnvCommands() throws ExecutionException {
      int i = 0;

      while(true) {
         String envCommand = System.getenv(ENV_VAR_PREFIX + i);
         if(envCommand == null) {
            return;
         }

         LOG.info("Environment command: " + envCommand);
         this.executeConsoleCommand(envCommand);
         ++i;
      }
   }

   private void loadStartupScripts() throws ExecutionException {
      for(int i = 0; i < STARTUP_SCRIPTS.length; ++i) {
         String script = STARTUP_SCRIPTS[i];
         LOG.info("Loading startup script: " + script);
         this.executeConsoleCommand("command source \"" + script + "\"");
      }

   }

   private void createEmptyTarget() throws ExecutionException {
      ThrowIfNotValid<CreateTarget_Res> responseHandler = new ThrowIfNotValid<CreateTarget_Res>("Couldn\'t create target");
      CompositeRequest createTargetReq = ProtobufMessageFactory.createTarget("", "");
      this.getProtobufClient().sendMessageAndWaitForReply(createTargetReq, CreateTarget_Res.class, responseHandler);
      responseHandler.throwIfNeeded();
   }

   private void connectPlatform() throws ExecutionException {
      String connectURL = String.format("adb://[%s]%s", new Object[]{this.myDeviceSN, this.myPlatformDomainSocketPath});
      LOG.info("Connecting to LLDB server: " + connectURL);
      ThrowIfNotValid<ConnectPlatform_Res> responseHandler = new ThrowIfNotValid<ConnectPlatform_Res>("Couldn\'t connect platform");
      CompositeRequest connectPlatformReq = ProtobufMessageFactory.connectPlatform(PLATFORM_NAME, connectURL);
      this.getProtobufClient().sendMessageAndWaitForReply(connectPlatformReq, ConnectPlatform_Res.class, responseHandler);
      responseHandler.throwIfNeeded();
   }

   @Override
   public void start(Installer installer, String architecture, boolean isRemote) throws ExecutionException {
      super.start(installer, architecture, isRemote);
      AndroidLLDBBreakpadIntegration.monitorForCrashes(this);
   }

   @Override
   protected void handleDriverException(Exception e) {
      if(!AndroidLLDBBreakpadIntegration.checkForCrashes(this)) {
         super.handleDriverException(e);
      }

   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
protected File getSourceFile(final LLDBFrame frame) {
      String sourceFile = frame.getFile();
      File sf = new File(frame.getFile());
      if(sf.isAbsolute()) {
         return sf;
      } else {
         IdeaSourceProvider srcProvider = this.myState.getFacet().getMainIdeaSourceProvider();
         Iterator<Collection> psiMethodFilePath = Lists.newArrayList(new Collection[]{
        		 srcProvider.getJavaDirectories(), 
        		 srcProvider.getJniDirectories()}
         ).iterator();

         
         File foundSrcFile;
         do {
            if(!psiMethodFilePath.hasNext()) {
               String psiMethodFilePath1 = (String)ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            	   @Override
                  public String compute() {
                     PsiMethod psiMethod = AndroidLLDBDriver.this.parseJavaCallSignature(frame.getFunction());
                     if(psiMethod == null) {
                        return null;
                     } else {
                        try {
                           VirtualFile e = psiMethod.getContainingFile().getVirtualFile();
                           if(e != null) {
                              return e.getCanonicalPath();
                           }
                        } catch (Exception var3) {
                           LOG.error(var3);
                        }

                        return null;
                     }
                  }
               });
               if(psiMethodFilePath1 != null) {
                  return new File(psiMethodFilePath1);
               }

               return sf;
            }

            Collection<VirtualFile> srcDirs =  psiMethodFilePath.next();
            foundSrcFile = findSourceFile(srcDirs, sourceFile);
         } while(foundSrcFile == null);

         return foundSrcFile;
      }
   }

   private static File findSourceFile(Collection<VirtualFile> srcDirs, String relPath) {
      Iterator<VirtualFile> iter = srcDirs.iterator();
      while(iter.hasNext()){
          VirtualFile srcDir = (VirtualFile)iter.next();
          VirtualFile childFile = srcDir.findFileByRelativePath(relPath);
          if(childFile != null){
        	  return new File(childFile.getCanonicalPath());
          }
      }
      return null;
   }

   @Override
   protected LLFrame newLLFrame(final LLDBFrame frame) {
      String sourceFile = frame.getFile();
      if(sourceFile != null && !sourceFile.isEmpty()) {
         try {
            sourceFile = this.getSourceFile(frame).getCanonicalPath();
         } catch (IOException var5) {
            ;
         }
      }

      int line = frame.getLine() - 1;
      if(line < 0 && sourceFile != null) {
         Pair<String,Integer> location = (Pair<String,Integer>)ApplicationManager.getApplication().runReadAction(new Computable<Pair<String,Integer>>() {
        	 @Override
            public Pair<String,Integer> compute() {
               PsiMethod psiMethod = AndroidLLDBDriver.this.parseJavaCallSignature(frame.getFunction());
               if(psiMethod == null) {
                  return null;
               } else {
                  try {
                     VirtualFile e = psiMethod.getContainingFile().getVirtualFile();
                     if(e == null) {
                        return null;
                     } else {
                        Document document = FileDocumentManager.getInstance().getDocument(e);
                        return document == null?null:Pair.create(e.getCanonicalPath(), Integer.valueOf(document.getLineNumber(psiMethod.getTextOffset())));
                     }
                  } catch (Exception var4) {
                     LOG.error(var4);
                     return null;
                  }
               }
            }
         });
         if(location != null) {
            sourceFile = (String)location.getFirst();
            line = ((Integer)location.getSecond()).intValue();
         }
      }

      return new LLFrame(frame.getNumber(), frame.getFunction(), sourceFile, line, frame.getPc());
   }

   private PsiMethod parseJavaCallSignature(String callSignature) {
      JavaCallSignature signature = JavaCallSignature.Parse(callSignature);
      if(signature == null) {
         return null;
      } else {
         PsiClass psiClass = NavigationEditorUtils.getPsiClass(this.myState.getFacet().getModule(), signature.getClassName());
         if(psiClass == null) {
            return null;
         } else {
            PsiMethod[] arr = psiClass.findMethodsByName(signature.getMethodName(), true);
            for(int iter = 0; iter < arr.length; ++iter) {
               PsiMethod psiMethod = arr[iter];
               if(psiMethod.getReturnType().equalsToText(signature.getReturnType())) {
                  PsiParameterList paramList = psiMethod.getParameterList();
                  if(paramList.getParametersCount() == signature.getParameterList().size()) {
                     PsiParameter[] psiParams = paramList.getParameters();
                     boolean equalParameterTypes = true;

                     for(int i = 0; i < psiParams.length && equalParameterTypes; ++i) {
                        equalParameterTypes = psiParams[i].getType().equalsToText((String)signature.getParameterList().get(i));
                     }

                     if(equalParameterTypes) {
                        return psiMethod;
                     }
                  }
               }
            }

            return null;
         }
      }
   }

   public boolean isCrashed() {
      return this.myCrashed;
   }

   public void setCrashed(boolean crashed) {
      this.myCrashed = crashed;
   }
}
