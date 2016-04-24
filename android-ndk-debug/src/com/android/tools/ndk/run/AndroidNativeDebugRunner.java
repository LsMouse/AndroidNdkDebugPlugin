package com.android.tools.ndk.run;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidDebugState;
import com.android.tools.idea.run.AndroidProcessText;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunningState;
import com.android.tools.idea.run.AndroidRunningStateListener;
import com.android.tools.idea.run.DebugLauncher;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.ndk.NdkHelper;
import com.android.tools.ndk.run.crash.AndroidLLDBBreakpadIntegration;
import com.android.tools.ndk.run.hybrid.AndroidJavaDebugProcess;
import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugUIEnvironment;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.jetbrains.cidr.execution.CidrCommandLineState;
import com.jetbrains.cidr.execution.CidrRunner;
import com.jetbrains.cidr.execution.Installer;
import com.jetbrains.cidr.execution.RunParameters;
import com.jetbrains.cidr.execution.TrivialInstaller;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration;

public class AndroidNativeDebugRunner extends CidrRunner {
   private static final Logger LOG = Logger.getInstance(AndroidNativeDebugRunner.class);

   public String getRunnerId() {
      return "AndroidNativeDebugRunner";
   }

   /*added by lichao*/
   private Client getSelectedClient(ExecutionEnvironment environment){
	 return NativeRunParametersPanel.getSelectedClient(); 
   }
   
   /*added by lichao*/
   @Override
   protected RunContentDescriptor doExecute(RunProfileState state, ExecutionEnvironment environment) throws ExecutionException {
	   boolean IsAttach = true;//let user choose attach or launch to debug jni, add 2 radio buttons
	  
      AndroidRunningState runningState = (AndroidRunningState)state;
      boolean isDebug = environment.getExecutor() instanceof DefaultDebugExecutor;
      AndroidNativeDebugRunner.NativeDebugLauncher launcher = null;
      if(isDebug) {
         DebuggerContext runDescriptor = ((AndroidNativeRunConfiguration)runningState.getConfiguration()).getDebuggerContext();
         launcher = new AndroidNativeDebugRunner.NativeDebugLauncher(runningState, environment, runDescriptor);
         runningState.setDebugLauncher(launcher);
      }

      runningState.addListener(new AndroidRunningStateListener() {
         public void executionFailed() {
            AndroidNativeDebugRunner.LOG.error("Android Launch failed");
         }
      });
      RunContentDescriptor retRunDescriptor = IsAttach?doExecuteEx((AndroidRunningState)state, environment):
    	  super.doExecute(state, environment);
      if(retRunDescriptor != null && launcher != null) {
         launcher.setRunDescriptor(retRunDescriptor);
      }
      return retRunDescriptor;
   }
   
   /*added by lichao*/
	private boolean isReadyForDebugging(ClientData data, ProcessHandler processHandler){
		ClientData.DebuggerStatus status = data.getDebuggerConnectionStatus();
		switch (status) {
			case DEFAULT://起始状态
				if (processHandler != null) {
					processHandler.notifyTextAvailable("Client is running\n", ProcessOutputTypes.STDOUT);
				}
				LOG.info("Debuggee is running");
				return true;
			case WAITING://等待调试器
				if (processHandler != null) {
					processHandler.notifyTextAvailable("Client is waitting for debugger\n", ProcessOutputTypes.STDOUT);
				}
				LOG.info("Debugge is waitting for debugger");
				return true;
			case ATTACHED://附加后状态
				//重复附加会有问题吗？如果有问题则需要先释放
				if (processHandler != null) {
					processHandler.notifyTextAvailable("Client is already attached\n", ProcessOutputTypes.STDOUT);
				}
				LOG.info("Client is already attached");	
				return true;
			case ERROR://未就绪
				if (processHandler != null) {
					processHandler.notifyTextAvailable("Client is in error\n", ProcessOutputTypes.STDOUT);
				}
				LOG.info("Client is in error");
				return false;
		}
		return false;
   }
   
	   /*added by lichao*/
   @SuppressWarnings("unchecked")
   private void start(AndroidRunningState state, ExecutionEnvironment env){
	   //AndroidRunningState.java 
	   Client client = getSelectedClient(env);
	   if(client == null){
		   LOG.error("Please select a package process name in \"Run/Debug Configurations->Android Native->Native Debugger\" ");
		   return;
	   }
	   if (isReadyForDebugging(client.getClientData(), state.getProcessHandler())) {
		   state.getDebugLauncher().launchDebug(client);
		   state.setDebugLauncher(null);
	   }
	   List<AndroidRunningStateListener> myListeners = (List<AndroidRunningStateListener>) JavaCalls.getField(state, "myListeners");
	   for (AndroidRunningStateListener listener : myListeners) {
		   listener.executionStarted(client.getDevice());
	   }
   }
   
   /*added by lichao*/
	protected RunContentDescriptor doExecuteEx(final AndroidRunningState state, final ExecutionEnvironment env)
			throws ExecutionException
	{
		//DefaultProgramRunner.java
		FileDocumentManager.getInstance().saveAllDocuments();
		ExecutionResult executionResult = null;
		
		//AndroidRunningState.java
		final ProcessHandler myProcessHandler = new DefaultDebugProcessHandler();
		AndroidProcessText.attach(myProcessHandler);
		state.setProcessHandler(myProcessHandler);
		
		//AndroidRunConfiguration.java
		Project project = ((AndroidRunConfiguration)state.getConfiguration()).getConfigurationModule().getProject();
		TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
		ConsoleView myConsole = builder.getConsole();
		myConsole.attachToProcess(state.getProcessHandler());
		
		((ProcessHandlerConsolePrinter)state.getPrinter()).setProcessHandler(myProcessHandler);

	    ApplicationManager.getApplication().executeOnPooledThread(new Runnable(){
			@Override
			public void run() {
				start(state, env);
			}
	    });
	    
	    myProcessHandler.addProcessListener(new ProcessAdapter()
	    {
	    	@SuppressWarnings("rawtypes")
			@Override
	    	public void onTextAvailable(ProcessEvent event, Key outputType) {
	    		if (outputType.equals(ProcessOutputTypes.STDERR)) {
	    			UIUtil.invokeLaterIfNeeded(new Runnable()
	    			{
	    				@Override
	    				public void run() {
	    					ToolWindowManager instance = ToolWindowManager.getInstance(state.getFacet().getModule().getProject());
	    					instance.getToolWindow(env.getExecutor().getToolWindowId()).activate(null, true, false);
	    				}
	    			});
	    		}
	    	}

	    	@Override
	    	public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed)
	    	{
	    		myProcessHandler.removeProcessListener(this);
	    	}    
	    });
	    executionResult = new DefaultExecutionResult(myConsole, myProcessHandler);
	    return new RunContentBuilder(executionResult, env).showRunContent(env.getContentToReuse());
	}

	@Override
   public boolean canRun(String executorId, RunProfile profile) {
      return (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) || DefaultRunExecutor.EXECUTOR_ID.equals(executorId)) && 
    		  profile instanceof AndroidNativeRunConfiguration;
   }

   static {
      AndroidLLDBBreakpadIntegration.checkForStaleMinidumps();
   }

   private class NativeDebugLauncher implements DebugLauncher, AndroidNativeDebugProcess.AttachNotifier {
      private final AndroidRunningState myRunningState;
      private final ExecutionEnvironment myEnvironment;
      private RunContentDescriptor myRunDescriptor;
      private DebuggerContext myDebuggerContext;
      private AtomicBoolean myCancelled = new AtomicBoolean(false);

      public NativeDebugLauncher(AndroidRunningState runningState, ExecutionEnvironment environment, DebuggerContext debuggerContext) {
         this.myRunningState = runningState;
         this.myEnvironment = environment;
         this.myDebuggerContext = debuggerContext;
      }

      @Override
      public void launchDebug(final Client client) {
         ((AndroidNativeRunConfiguration)this.myRunningState.getConfiguration()).onLaunchDebug(client);
         IDevice device = client.getDevice();
         AndroidNativeDebugRunner.LOG.info(String.format("Launching native debug session on device: manufacturer=%s, model=%s, API=%s, ABIs=%s", 
        		 new Object[]{
        				 device.getProperty("ro.product.manufacturer"), 
        				 device.getProperty("ro.product.model"), 
        				 device.getProperty("ro.build.version.sdk"), 
        				 device.getAbis().toString()}));
         myDebuggerContext.setAttachProgressReporter(new AttachProgressReporter(this.myRunningState.getModule().getProject()));

         try {
            myDebuggerContext.startServer(this.myRunningState, client, this.myCancelled);
         } catch (Throwable var4) {
            onLaunchFailure(client, "Error while launching debug server on device: " + var4.toString(), var4);
            return;
         }

         ApplicationManager.getApplication().invokeLater(new Runnable() {
        	 @Override
        	 public void run() {
               try {
                  launchCidrDebugger(client);
               } catch (Throwable var2) {
                  onLaunchFailure(client, "Error while starting native debug session: " + var2.toString(), var2);
               }

            }
         });
      }

      private void launchCidrDebugger(final Client client) throws ExecutionException {
         final IDevice device = client.getDevice();
         final RunParameters runParameters = new RunParameters() {
        	 @Override
        	 public Installer getInstaller() {
               return new TrivialInstaller(new GeneralCommandLine());
            }

        	 @Override
            public DebuggerDriverConfiguration getDebuggerDriverConfiguration() {
               return myDebuggerContext.getDebuggerDriverConfiguration(myRunningState, device);
            }

        	 @Override
            public boolean isWaitFor() {
               return false;
            }

        	 @Override
            public String getArchitectureId() {
               return NdkHelper.getArchitectureId(NdkHelper.getAbi(device));
            }
         };
         CidrCommandLineState cidrState = new CidrCommandLineState(this.myEnvironment) {
        	 @Override
        	 public XDebugProcess startDebugProcess(XDebugSession session) throws ExecutionException {
               AndroidNativeDebugProcess result = new AndroidNativeDebugProcess(runParameters, session, getConsoleBuilder(), 
            		   myRunningState, NativeDebugLauncher.this, client, myDebuggerContext.getAttachProgressReporter());
               ProcessTerminatedListener.attach(result.getProcessHandler(), myEnvironment.getProject());
               result.start();
               return result;
            }

        	 @Override
            protected ProcessHandler startProcess() throws ExecutionException {
               throw new RuntimeException("start process not implemented");
            }
         };
         this.myRunningState.getPrinter().stdout("Now Launching Native Debug Session");
         this.myDebuggerContext.getAttachProgressReporter().step("Launching debug session");
         ProcessHandler processHandler = this.myRunningState.getProcessHandler();
         processHandler.detachProcess();
         ExecutionEnvironment env = (new ExecutionEnvironmentBuilder(this.myEnvironment)).executor(this.myEnvironment.getExecutor()).
        		 runner(AndroidNativeDebugRunner.this).contentToReuse(this.myRunDescriptor).build();

         XDebugSessionImpl xDebugSession;
         try {
            xDebugSession = (XDebugSessionImpl)AndroidNativeDebugRunner.this.startDebugSession(cidrState, env, false, new XDebugSessionListener[0]);
         } catch (Exception var10) {
            throw new ExecutionException(var10);
         }

         xDebugSession.showSessionTab();
         ProcessHandler newProcessHandler = xDebugSession.getRunContentDescriptor().getProcessHandler();
         if(newProcessHandler == null) {
            throw new ExecutionException("Cannot start debugging - null process handler.");
         } else {
            this.myRunningState.setProcessHandler(newProcessHandler);
            AndroidProcessText oldText = AndroidProcessText.get(processHandler);
            if(oldText != null) {
               oldText.printTo(newProcessHandler);
            }

            AndroidProcessText.attach(newProcessHandler);
         }
      }

      private void onLaunchFailure(Client client, String message, Throwable e) {
         this.myRunningState.getPrinter().stderr(message);
         AndroidNativeDebugRunner.LOG.error(message, e);
         this.myCancelled.set(true);
         this.myDebuggerContext.getAttachProgressReporter().finish();
         this.forceStopActivity(client);
      }

      private void forceStopActivity(Client client) {
         String packageName = this.myRunningState.getPackageName();

         try {
            IDevice e = client.getDevice();
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            e.executeShellCommand("am force-stop " + packageName, receiver);
         } catch (Exception var5) {
            AndroidNativeDebugRunner.LOG.error("Failed to force-stop activity " + packageName, var5);
         }

      }

      public void setRunDescriptor(RunContentDescriptor runDescriptor) {
         this.myRunDescriptor = runDescriptor;
      }

      private void startJavaDebugSession(AndroidNativeDebugProcess nativeDebugProcess, Client client) {
         IDevice device = client.getDevice();
         String debugPort = Integer.toString(client.getDebuggerListenPort());
         Project project = this.myRunningState.getModule().getProject();
         AndroidDebugState st = new AndroidDebugState(project, new RemoteConnection(true, "localhost", debugPort, false), this.myRunningState, device);
         RunContentDescriptor debugDescriptor = null;

         try {
            debugDescriptor = this.attachVirtualMachine(nativeDebugProcess, (new ExecutionEnvironmentBuilder(this.myEnvironment)).
            		executor(this.myEnvironment.getExecutor()).runner(AndroidNativeDebugRunner.this).
            		contentToReuse(this.myRunDescriptor).build(), st, st.getRemoteConnection(), false);
         } catch (ExecutionException var9) {
            this.myRunningState.getPrinter().stderr("ExecutionException: " + var9.getMessage() + '.');
         }

         ProcessHandler newProcessHandler = debugDescriptor != null?debugDescriptor.getProcessHandler():null;
         if(debugDescriptor != null && newProcessHandler != null) {
            AndroidProcessText.attach(newProcessHandler);
         } else {
            AndroidNativeDebugRunner.LOG.info("cannot start debugging");
         }
      }

      public RunContentDescriptor attachVirtualMachine(final AndroidNativeDebugProcess nativeDebugProcess, ExecutionEnvironment environment, 
    		  RunProfileState state, RemoteConnection remoteConnection, boolean pollConnection) throws ExecutionException {
         DefaultDebugUIEnvironment debugUIEnvironment = new DefaultDebugUIEnvironment(environment, state, remoteConnection, pollConnection);
         Project project = this.myRunningState.getModule().getProject();
         DebugEnvironment modelEnvironment = debugUIEnvironment.getEnvironment();
         final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).attachVirtualMachine(modelEnvironment);
         if(debuggerSession == null) {
            return null;
         } else {
            DebugProcessImpl debugProcess = debuggerSession.getProcess();
            if(!debugProcess.isDetached() && !debugProcess.isDetaching()) {
               if(modelEnvironment.isRemote()) {
                  debugProcess.putUserData(BatchEvaluator.REMOTE_SESSION_KEY, Boolean.TRUE);
               }

               XDebugSession debugSession = XDebuggerManager.getInstance(project).startSessionAndShowTab(
            		   modelEnvironment.getSessionName() + "-java", debugUIEnvironment.getReuseContent(), new XDebugProcessStarter() {
                  public XDebugProcess start(XDebugSession session) {
                     return AndroidJavaDebugProcess.create(session, debuggerSession, nativeDebugProcess);
                  }
               });
               return debugSession.getRunContentDescriptor();
            } else {
               debuggerSession.dispose();
               return null;
            }
         }
      }

      public void debugProcessAttached(final AndroidNativeDebugProcess nativeDebugProcess, final Client client) {
         ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
               NativeDebugLauncher.this.startJavaDebugSession(nativeDebugProcess, client);
            }
         });
      }
   }
}
