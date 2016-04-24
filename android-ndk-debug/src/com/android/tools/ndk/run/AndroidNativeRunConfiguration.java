package com.android.tools.ndk.run;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jdom.Element;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;

import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.Variant;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.run.AndroidApplicationLauncher;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunningState;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.editor.ApplicationRunParameters;
import com.android.tools.ndk.run.lldb.AndroidLLDBDebuggerContext;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;

public class AndroidNativeRunConfiguration extends AndroidRunConfiguration {
   private static final String SYMBOL_DIRS = "symbol_dirs";
   private static final String SYMBOL_PATH = "symbol_path";
   private boolean HYBRID_DEBUG;
   private List<String> mySymbolDirs;
   public String WORKING_DIR;
   public String TARGET_LOGGING_CHANNELS;
   private final DebuggerContext myDebugContext;

   protected AndroidNativeRunConfiguration(Project project, ConfigurationFactory factory, DebuggerContext debuggerContext) {
      super(project, factory);
      this.HYBRID_DEBUG = true;
      this.mySymbolDirs = Lists.newLinkedList();
      this.WORKING_DIR = "";
      this.TARGET_LOGGING_CHANNELS = "lldb process:gdb-remote packets";
      this.myDebugContext = debuggerContext;
   }

   public AndroidNativeRunConfiguration(Project project, ConfigurationFactory factory) {
      this(project, factory, new AndroidLLDBDebuggerContext());
   }

   @Override
   public AndroidNativeRunConfigurationEditor getConfigurationEditor() {
      Project project = this.getProject();
      AndroidNativeRunConfigurationEditor editor = new AndroidNativeRunConfigurationEditor(project, Predicates.<AndroidFacet>alwaysFalse(), this);
      editor.setConfigurationSpecificEditor(new AndroidNativeRunConfiguration.AndroidNativeApplicationRunParameters(project, editor.getModuleSelector()));
      return editor;
   }

   @Override
   protected AndroidApplicationLauncher getApplicationLauncher(AndroidFacet facet) {
      AndroidApplicationLauncher activityLauncher = super.getApplicationLauncher(facet);
      return new AndroidNativeRunConfiguration.NativeActivityLauncher(activityLauncher);
   }

   public DebuggerContext getDebuggerContext() {
      return this.myDebugContext;
   }
   
   @Override
   public boolean usesSimpleLauncher() {
      return true;
   }
   
   @Override
   protected boolean supportMultipleDevices() {
      return false;
   }
   
   @Override
   protected List<ValidationError> checkConfiguration(AndroidFacet facet) {
      ArrayList<ValidationError> errors = Lists.newArrayList();
      errors.addAll(super.checkConfiguration(facet));
      AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
      if(androidModel != null && Projects.isBuildWithGradle(facet.getModule())) {
         Variant manifest1 = androidModel.getSelectedVariant();
         BuildTypeContainer buildTypeContainer = androidModel.findBuildType(manifest1.getBuildType());
         if(buildTypeContainer == null) {
            errors.add(ValidationError.fatal("Build type " + manifest1.getBuildType() + " not found"));
            return errors;
         }

         BuildType buildType = buildTypeContainer.getBuildType();
         if(!buildType.isDebuggable()) {
            errors.add(ValidationError.fatal("Build type isn\'t debuggable"));
         }

         if(!buildType.isJniDebuggable()) {
            errors.add(ValidationError.fatal("Build type isn\'t JNI debuggable"));
         }
      } else {
         Manifest manifest = facet.getManifest();
         if(manifest == null) {
            errors.add(ValidationError.fatal("Manifest is not found"));
            return errors;
         }

         if(!Boolean.valueOf((String)manifest.getApplication().getDebuggable().getValue()).booleanValue()) {
            errors.add(ValidationError.fatal("Application is not debuggable"));
         }
      }

      return errors;
   }

   protected void onLaunchDebug(Client client) {
   }

   public boolean isHybridDebug() {
      return this.HYBRID_DEBUG;
   }

   public void setHybridDebug(boolean hybridDebug) {
      this.HYBRID_DEBUG = hybridDebug;
   }

   public List<String> getSymbolDirs() {
      return this.mySymbolDirs;
   }

   public void setSymbolDirs(List<String> symDirs) {
      this.mySymbolDirs.clear();
      this.mySymbolDirs.addAll(symDirs);
   }

   public void addSymbolDir(String symDir) {
      if(this.mySymbolDirs.indexOf(symDir) == -1) {
         this.mySymbolDirs.add(symDir);
      }
   }

   public String getWorkingDir() {
      return this.WORKING_DIR;
   }

   public void setWorkingDir(String workingDir) {
      this.WORKING_DIR = workingDir;
   }

   public String getTargetLoggingChannels() {
      return this.TARGET_LOGGING_CHANNELS;
   }

   public void setTargetLoggingChannels(String targetLoggingChannels) {
      this.TARGET_LOGGING_CHANNELS = targetLoggingChannels;
   }

   @Override
   public void readExternal(Element element) throws InvalidDataException {
      super.readExternal(element);
      this.mySymbolDirs = JDOMExternalizer.loadStringsList(element, SYMBOL_DIRS, SYMBOL_PATH);
   }

   @Override
   public void writeExternal(Element element) throws WriteExternalException {
      super.writeExternal(element);
      JDOMExternalizer.saveStringsList(element, SYMBOL_DIRS, SYMBOL_PATH, this.mySymbolDirs.toArray(new String[0]));
   }

   private static class AndroidNativeApplicationRunParameters extends ApplicationRunParameters<AndroidNativeRunConfiguration> {
      public AndroidNativeApplicationRunParameters(Project project, ConfigurationModuleSelector moduleSelector) {
         super(project, moduleSelector);
      }
   }

   private static class NativeActivityLauncher extends AndroidApplicationLauncher {
      private final AndroidApplicationLauncher myActivityLauncher;

      public NativeActivityLauncher(AndroidApplicationLauncher activityLauncher) {
         this.myActivityLauncher = activityLauncher;
      }

      @Override
      public LaunchResult launch(AndroidRunningState state, IDevice device) throws IOException, AdbCommandRejectedException, TimeoutException {
         return this.myActivityLauncher.launch(state, device);
      }

      @Override
      public boolean isReadyForDebugging(ClientData data, ProcessHandler processHandler) {
         return super.isReadyForDebugging(data, processHandler) && data.isDdmAware();
      }
   }
}
