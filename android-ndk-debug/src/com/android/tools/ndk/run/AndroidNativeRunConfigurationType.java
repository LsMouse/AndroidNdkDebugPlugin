package com.android.tools.ndk.run;

import com.android.tools.ndk.run.AndroidNativeRunConfiguration;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.AndroidIcons;
import javax.swing.Icon;

public class AndroidNativeRunConfigurationType implements ConfigurationType {
   private final ConfigurationFactory myFactory = new AndroidNativeRunConfigurationType.AndroidNativeRunConfigurationFactory(this);

   public String getDisplayName() {
      return "Android Native";
   }

   public String getConfigurationTypeDescription() {
      return "Android Native Configuration";
   }

   public Icon getIcon() {
      return AndroidIcons.Android;
   }

   public String getId() {
      return "AndroidNativeRunConfigurationType";
   }

   public ConfigurationFactory[] getConfigurationFactories() {
      return new ConfigurationFactory[]{this.myFactory};
   }

   public static AndroidNativeRunConfigurationType getInstance() {
      return (AndroidNativeRunConfigurationType)ConfigurationTypeUtil.findConfigurationType(AndroidNativeRunConfigurationType.class);
   }

   public ConfigurationFactory getFactory() {
      return this.myFactory;
   }

   public static class AndroidNativeRunConfigurationFactory extends ConfigurationFactory {
      protected AndroidNativeRunConfigurationFactory(ConfigurationType type) {
         super(type);
      }

      public RunConfiguration createTemplateConfiguration(Project project) {
         return new AndroidNativeRunConfiguration(project, this);
      }

      @SuppressWarnings("rawtypes")
	public void configureBeforeRunTaskDefaults(Key providerID, BeforeRunTask task) {
         if(CompileStepBeforeRun.ID.equals(providerID)) {
            task.setEnabled(false);
         }

      }
   }
}
