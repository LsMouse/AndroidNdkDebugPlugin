package com.android.tools.ndk.run;

import com.android.builder.model.NativeLibrary;
import com.android.builder.model.Variant;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.MultiLineReceiver;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.AndroidRunningState;
import com.android.tools.ndk.MemoryRegionMap;
import com.android.tools.ndk.run.AndroidNativeExecutionStack;
import com.android.tools.ndk.run.AndroidNativeRunConfiguration;
import com.android.tools.ndk.run.AttachProgressReporter;
import com.android.tools.ndk.run.ClientShellHelper;
import com.android.tools.ndk.run.crash.AndroidLLDBBreakpadIntegration;
import com.android.tools.ndk.run.hybrid.NativeDebugProcess;
import com.android.tools.ndk.run.hybrid.StepIntoNativeBreakpointHandler;
import com.android.tools.ndk.run.hybrid.StepIntoNativeBreakpointType;
import com.android.tools.ndk.run.lldb.AndroidLLDBDriver;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.jetbrains.cidr.execution.RunParameters;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.CidrSuspensionCause;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;
import com.jetbrains.cidr.execution.debugger.backend.LLFrame;
import com.jetbrains.cidr.execution.debugger.backend.LLThread;
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrSymbolicBreakpointType;
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrSymbolicBreakpointType.Properties;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.Connector.Argument;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AndroidNativeDebugProcess extends CidrDebugProcess implements NativeDebugProcess {
   private static final Logger LOG = Logger.getInstance(AndroidNativeDebugProcess.class);
   private static final String LIBART_SO = "libart.so";
   private static final String ART_SIGSEGV_FAULT = "art_sigsegv_fault";
   private static final CidrSymbolicBreakpointType SYMBOLIC_BREAKPOINT_TYPE;
   private static final String JDI_CONNECTOR_NAME = "com.sun.jdi.SocketAttach";
   private final AndroidRunningState myState;
   private final AndroidNativeDebugProcess.AttachNotifier myAttachNotifier;
   private final Client myClient;
   private final AttachProgressReporter myAttachProgressReporter;
   private XBreakpoint<?> myArtSigSegvFaultBp;
   private boolean myArt = false;
   private final MemoryRegionMap myArtMemRegionMap = new MemoryRegionMap(Arrays.asList(new String[]{".*\\.dex", ".*\\.odex", ".*\\.oat"}));
   private final StepIntoNativeBreakpointHandler myStepIntoNativeBreakpointHandler;

   public AndroidNativeDebugProcess(RunParameters parameters, XDebugSession session, TextConsoleBuilder consoleBuilder, AndroidRunningState state, AndroidNativeDebugProcess.AttachNotifier attachNotifier, Client client, AttachProgressReporter attachProgressReporter) throws ExecutionException {
      super(parameters, session, consoleBuilder);
      this.myState = state;
      this.myAttachNotifier = attachNotifier;
      this.myClient = client;
      this.myAttachProgressReporter = attachProgressReporter;
      this.myStepIntoNativeBreakpointHandler = this.createStepIntoNativeHandler();
   }

   private StepIntoNativeBreakpointHandler createStepIntoNativeHandler() {
      return new StepIntoNativeBreakpointHandler(this, StepIntoNativeBreakpointType.class);
   }

   @Override
   protected boolean isRemote() {
      return true;
   }

   @Override
   public boolean checkCanInitBreakpoints() {
      return false;
   }

   @Override
   protected void doStart(DebuggerDriver driver) throws ExecutionException {
   }

   @Override
   protected void doLaunchTarget(DebuggerDriver driver) throws ExecutionException {
      this.getProcessHandler().startNotify();

      try {
         LOG.info("Loading driver");
         this.myAttachProgressReporter.step("Loading debugger driver");
         driver.loadForRemote((File)null);
         ClientData e = this.myClient.getClientData();
         LOG.info(String.format("Attaching to inferior: pid=%d, ABI=%s", new Object[]{Integer.valueOf(e.getPid()), e.getAbi()}));
         this.myAttachProgressReporter.step("Attaching to the app");
         driver.attachTo(e.getPid());
         this.myArt = this.isArtVM();
         if(this.myArt) {
            LOG.info("Running in ART VM");
            this.initArtSigSegvFaultBreakpoint();
         } else {
            LOG.info("Running in Dalvik VM");
         }

         ApplicationManager.getApplication().runReadAction(new Runnable() {
        	 @Override
        	 public void run() {
               AndroidNativeDebugProcess.this.getSession().initBreakpoints();
            }
         });
         LOG.info("Resuming paused inferior");
         this.myAttachProgressReporter.step("Resuming the app process");
         driver.resume();
         this.myAttachProgressReporter.step("Resuming the app VM");
         if(this.isHybridDebugModeAllowed()) {
            LOG.info("Hybrid debug mode is on, starting Java debug session: jdwp port=" + this.myClient.getDebuggerListenPort());
            this.myAttachNotifier.debugProcessAttached(this, this.myClient);
         } else {
            LOG.info("Resuming JVM: jdwp port=" + this.myClient.getDebuggerListenPort());
            this.resumeVM();
         }

         LOG.info("Launch has been completed");
      } catch (ExecutionException var6) {
         this.myState.getPrinter().stderr("Failed to attach native debugger: " + var6.getMessage());
         throw var6;
      } finally {
         this.myAttachProgressReporter.finish();
      }

   }

   private boolean isHybridDebugModeAllowed() {
      return ((AndroidNativeRunConfiguration)this.myState.getConfiguration()).isHybridDebug();
   }

   private static AttachingConnector findConnector() throws ExecutionException {
      VirtualMachineManager virtualMachineManager;
      try {
         virtualMachineManager = Bootstrap.virtualMachineManager();
      } catch (Error var4) {
         throw new ExecutionException(var4);
      }

      Iterator<AttachingConnector> iter = virtualMachineManager.attachingConnectors().iterator();
      while(iter.hasNext()){
          AttachingConnector connectorObj = iter.next();
          Connector connector = connectorObj;
          if(JDI_CONNECTOR_NAME.equals(connector.name())){
        	  return (AttachingConnector)connector;
          }
      }
      return null;
   }

   private void resumeVM() throws ExecutionException {
      AttachingConnector jdiConnector = findConnector();
      if(jdiConnector == null) {
         throw new ExecutionException("Failed to find JDI connector");
      } else {
         Map<String, Argument> arguments = jdiConnector.defaultArguments();
         Argument hostnameArg = (Argument)arguments.get("hostname");
         if(hostnameArg != null) {
            hostnameArg.setValue("localhost");
         }

         Argument portArg = (Argument)arguments.get("port");
         if(portArg != null) {
            portArg.setValue(String.valueOf(this.myClient.getDebuggerListenPort()));
         }

         Argument timeoutArg = (Argument)arguments.get("timeout");
         if(timeoutArg != null) {
            timeoutArg.setValue("0");
         }

         try {
            VirtualMachine e = jdiConnector.attach(arguments);
            e.resume();
         } catch (Exception var7) {
            throw new ExecutionException("Failed to resume VM", var7);
         }
      }
   }

   private XBreakpointManager getBreakpointManager() {
      return XDebuggerManager.getInstance(this.myState.getModule().getProject()).getBreakpointManager();
   }

   private static Application getApp() {
      return ApplicationManager.getApplication();
   }

   private void initArtSigSegvFaultBreakpoint() throws ExecutionException {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
         @Override
    	  public void run() {
            Iterator<?> iter = AndroidNativeDebugProcess.this.getBreakpointManager().getBreakpoints(AndroidNativeDebugProcess.SYMBOLIC_BREAKPOINT_TYPE).iterator();

            while(iter.hasNext()) {
               XBreakpoint<?> bp = (XBreakpoint<?>)iter.next();
               Properties bpProps = (Properties)bp.getProperties();
               if(bpProps.getModuleName().equals(LIBART_SO) && bpProps.getSymbolPattern().equals(ART_SIGSEGV_FAULT)) {
                  AndroidNativeDebugProcess.this.myArtSigSegvFaultBp = bp;
                  break;
               }
            }

         }
      });
      if(this.myArtSigSegvFaultBp == null) {
         getApp().invokeLater(new Runnable() {
            @Override
        	 public void run() {
               ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  public void run() {
                     Properties props = new Properties(ART_SIGSEGV_FAULT, LIBART_SO);
                     myArtSigSegvFaultBp = getBreakpointManager().addBreakpoint(AndroidNativeDebugProcess.SYMBOLIC_BREAKPOINT_TYPE, props);
                  }
               });
            }
         });
      }
   }

   private boolean isArtVM() throws ExecutionException {
      CollectingOutputReceiver receiver = new CollectingOutputReceiver();
      this.readProcMapsFile(receiver);
      return receiver.getOutput().contains(LIBART_SO);
   }

   @Override
   public void stop() {
      super.stop();
      getApp().invokeLater(new Runnable() {
    	  @Override
         public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
               public void run() {
                  if(myArtSigSegvFaultBp != null) {
                     XDebuggerManager.getInstance(AndroidNativeDebugProcess.this.myState.getModule().getProject()).getBreakpointManager().removeBreakpoint(AndroidNativeDebugProcess.this.myArtSigSegvFaultBp);
                     myArtSigSegvFaultBp = null;
                  }

               }
            });
         }
      });
   }

   @Override
   public void handleSignal(final List<LLThread> threads, final int currentThreadIndex, final String signal, final String meaning) {
      if(this.myArt && signal.equals("SIGSEGV")) {
         this.postCommand(new DebuggerCommand() {
        	 @Override
        	 public void run(DebuggerDriver driver) throws ExecutionException {
               if(!AndroidNativeDebugProcess.this.handleArtSigSegv(driver, threads, currentThreadIndex)) {
                  AndroidNativeDebugProcess.super.handleSignal(threads, currentThreadIndex, signal, meaning);
               }

            }
         });
      } else {
         super.handleSignal(threads, currentThreadIndex, signal, meaning);
      }
   }

   private boolean handleArtSigSegv(DebuggerDriver driver, List<LLThread> threads, int currentThreadIndex) {
      LLThread thread = threads.get(currentThreadIndex);

      try {
         List<LLFrame> e = driver.getFrames(thread.getId());
         if(e.isEmpty()) {
            return false;
         }

         LLFrame firstFrame = (LLFrame)e.get(0);
         LOG.info(String.format("Got SIGSEGV signal, PC address: 0x%X", new Object[]{Long.valueOf(firstFrame.getProgramCounter())}));
         MemoryRegionMap.Region region = this.myArtMemRegionMap.getRegionByAddress(firstFrame.getProgramCounter());
         if(region == null || !region.isExecutable()) {
            this.myArtMemRegionMap.clear();
            this.readProcMapsFile(new MultiLineReceiver() {
            	@Override
            	public void processNewLines(String[] lines) {
                  AndroidNativeDebugProcess.this.myArtMemRegionMap.addMapEntries(lines);
               }

            	@Override
               public boolean isCancelled() {
                  return false;
               }
            });
         }

         region = this.myArtMemRegionMap.getRegionByAddress(firstFrame.getProgramCounter());
         if(region != null && region.isExecutable()) {
            LOG.info(String.format("SIGSEGV came from ART module \'%s\' - resuming the inferior", new Object[]{region.getFileName()}));
            driver.resume();
            return true;
         }
      } catch (Exception var8) {
         LOG.error(var8);
      }

      return false;
   }

   private void readProcMapsFile(IShellOutputReceiver receiver) throws ExecutionException {
      try {
         ClientShellHelper e = new ClientShellHelper(this.myClient, this.myState.getPackageName());
         this.myClient.getDevice().executeShellCommand(e.runAs(String.format("cat /proc/%d/maps", new Object[]{Integer.valueOf(this.myClient.getClientData().getPid())})), receiver);
      } catch (Exception var3) {
         throw new ExecutionException(var3);
      }
   }

   public static List<File> getSymbolsDir(AndroidRunningState state, List<Abi> abis) {
      ArrayList<File> libFolders = Lists.newArrayList();
      AndroidGradleModel androidModel = AndroidGradleModel.get(state.getFacet());
      if(androidModel != null) {
         Variant config = androidModel.getSelectedVariant();
         Collection<NativeLibrary> iterlib = config.getMainArtifact().getNativeLibraries();
         if(iterlib == null) {
            return Collections.emptyList();
         }

         HashSet<String> symDir = Sets.newHashSetWithExpectedSize(abis.size());
         for(Abi library : abis){
        	 symDir.add(library.toString());
         }
         
         for(NativeLibrary library : iterlib){
             if(symDir.contains(library.getAbi())) {
                 libFolders.addAll(library.getDebuggableLibraryFolders());
              }
         }
      }

      AndroidNativeRunConfiguration config1 = (AndroidNativeRunConfiguration)state.getConfiguration();
      Iterator<String> iterdir = config1.getSymbolDirs().iterator();

      while(iterdir.hasNext()) {
         String symDir1 = (String)iterdir.next();
         libFolders.add(new File(symDir1));
      }

      return libFolders;
   }

   @Override
   protected XExecutionStack newExecutionStack(DebuggerDriver driver, LLThread thread, boolean current, CidrSuspensionCause suspensionCause) throws ExecutionException {
      return (XExecutionStack)(System.getProperty("com.google.android-studio.lazy-backtraces", "yes").equals("yes")?new AndroidNativeExecutionStack(this, driver, thread, current, suspensionCause):super.newExecutionStack(driver, thread, current, suspensionCause));
   }

   @Override
   public void handleBreakpoint(int number, List<LLThread> threads, int currentThreadIndex) {
      final XBreakpoint<?> bp = myStepIntoNativeBreakpointHandler.getCodepoint(number);
      if(bp == null) {
         super.handleBreakpoint(number, threads, currentThreadIndex);
      } else {
         ((StepIntoNativeBreakpointType.Properties)bp.getProperties()).setCalled(true);
         getApp().invokeAndWait(new Runnable() {
        	 @Override
        	 public void run() {
               ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  public void run() {
                     AndroidNativeDebugProcess.this.removeStepIntoNativeBreakpoint(AndroidNativeDebugProcess.this.getBreakpointManager(), bp);
                  }
               });
            }
         }, ModalityState.defaultModalityState());
         this.handleXBreakpoint(threads, currentThreadIndex, bp);
      }
   }

   private void removeStepIntoNativeBreakpoint(XBreakpointManager bpManager, XBreakpoint<?> bp) {
      bpManager.removeBreakpoint(bp);
      this.myStepIntoNativeBreakpointHandler.unregisterBreakpoint(bp, false);
   }

   @Override
   public XBreakpoint<?> registerStepIntoNativeBreakpoint(String jniMethodName, int tid) {
      final StepIntoNativeBreakpointType.Properties props = new StepIntoNativeBreakpointType.Properties(jniMethodName, tid);
      final Ref<XBreakpoint<?>> bpRef = new Ref<XBreakpoint<?>>();
      getApp().invokeAndWait(new Runnable() {
    	  @Override
    	  public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
               public void run() {
                  XBreakpoint<?> bp = getBreakpointManager().addBreakpoint(StepIntoNativeBreakpointType.INSTANCE, props);
                  AndroidNativeDebugProcess.this.myStepIntoNativeBreakpointHandler.registerBreakpoint(bp);
                  bpRef.set(bp);
               }
            });
         }
      }, ModalityState.defaultModalityState());
      return (XBreakpoint<?>)bpRef.get();
   }

   @Override
   public void removeAllStepIntoNativeBreakpoints() {
      final LinkedList<XBreakpoint<StepIntoNativeBreakpointType.Properties>> bps = Lists.newLinkedList();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
    	  @Override
    	  public void run() {
            bps.addAll(getBreakpointManager().getBreakpoints(StepIntoNativeBreakpointType.INSTANCE));
         }
      });
      if(!bps.isEmpty()) {
         getApp().invokeAndWait(new Runnable() {
        	 @Override
        	 public void run() {
               ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  public void run() {
                     XBreakpointManager bpManager = getBreakpointManager();
                     Iterator<?> i$ = bps.iterator();

                     while(i$.hasNext()) {
                        XBreakpoint<?> bp = (XBreakpoint<?>)i$.next();
                        AndroidNativeDebugProcess.this.removeStepIntoNativeBreakpoint(bpManager, bp);
                     }

                  }
               });
            }
         }, ModalityState.defaultModalityState());
      }
   }

   @Override
   protected void handleCommandException(ExecutionException e, DebuggerDriver driver) {
      if(!AndroidLLDBBreakpadIntegration.checkForCrashes((AndroidLLDBDriver)driver)) {
         super.handleCommandException(e, driver);
      }

   }

   static {
      SYMBOLIC_BREAKPOINT_TYPE = (CidrSymbolicBreakpointType)XBreakpointType.EXTENSION_POINT_NAME.findExtension(CidrSymbolicBreakpointType.class);
   }

   public interface AttachNotifier {
      void debugProcessAttached(AndroidNativeDebugProcess var1, Client var2);
   }
}
