package com.android.tools.ndk.run;

import org.jetbrains.android.facet.AndroidFacet;

import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor;
import com.android.tools.ndk.run.AndroidNativeRunConfiguration;
import com.android.tools.ndk.run.NativeRunParametersPanel;
import com.google.common.base.Predicate;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;

public class AndroidNativeRunConfigurationEditor extends AndroidRunConfigurationEditor<AndroidNativeRunConfiguration> {
   private final NativeRunParametersPanel myPanel;

   public AndroidNativeRunConfigurationEditor(Project project, Predicate<AndroidFacet> libraryProjectValidator, AndroidNativeRunConfiguration config) {
      super(project, libraryProjectValidator, config);
      this.myPanel = new NativeRunParametersPanel(project);
      this.myTabbedPane.add("Native Debugger", this.myPanel.getComponent());
   }

   @Override
   protected void resetEditorFrom(AndroidNativeRunConfiguration configuration) {
      super.resetEditorFrom(configuration);
      this.myPanel.setHybridDebug(configuration.isHybridDebug());
      this.myPanel.setSymbolDirs(configuration.getSymbolDirs());
      this.myPanel.setWorkingDir(configuration.getWorkingDir());
      this.myPanel.setTargetLoggingChannels(configuration.getTargetLoggingChannels());
   }

   @Override
   protected void applyEditorTo(AndroidNativeRunConfiguration configuration) throws ConfigurationException {
      super.applyEditorTo(configuration);
      configuration.setHybridDebug(this.myPanel.isHybridDebug());
      configuration.setSymbolDirs(this.myPanel.getSymbolDirs());
      configuration.setWorkingDir(this.myPanel.getWorkingDir());
      configuration.setTargetLoggingChannels(this.myPanel.getTargetLoggingChannels());
   }
}
