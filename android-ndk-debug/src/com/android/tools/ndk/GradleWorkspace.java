package com.android.tools.ndk;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.messages.Message.Type;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.GradleSyncListener.Adapter;
import com.android.tools.idea.gradle.service.notification.errors.NdkLocationNotFoundErrorHandler;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.sdk.SdkPaths;
import com.android.tools.idea.stats.UsageTracker;
import com.android.tools.ndk.ModuleResolveConfiguration;
import com.android.tools.ndk.NdkCompilerInfoCache;
import com.android.tools.ndk.run.AndroidNativeRunConfiguration;
import com.android.tools.ndk.run.AndroidNativeRunConfigurationType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task.Backgroundable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.VirtualFileVisitor.Option;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.cidr.lang.OCFileType;
import com.jetbrains.cidr.lang.parser.OCParserDefinition;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackers;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeaderRoots;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;

public class GradleWorkspace extends AbstractProjectComponent implements OCWorkspace {
   private static final Logger LOG = Logger.getInstance(GradleWorkspace.class);
   private final NdkCompilerInfoCache myCompilerInfoCache = new NdkCompilerInfoCache();
   private final OCWorkspaceModificationTrackers myTrackers;
   private Set<VirtualFile> myProjectJniDirectories = ImmutableSet.of();
   private Set<VirtualFile> myProjectJniFiles = ImmutableSet.of();
   private Map<String,ModuleResolveConfiguration> myResolveConfigurations = ImmutableMap.of();

   public static GradleWorkspace getInstance(Project project) {
      return (GradleWorkspace)project.getComponent(GradleWorkspace.class);
   }

   public GradleWorkspace(Project project) {
      super(project);
      if(!Projects.isBuildWithGradle(project)) {
         setCppSupportDisabled(project, true);
      }

      this.myTrackers = new OCWorkspaceModificationTrackers(project);
      GradleSyncState.subscribe(this.myProject, new Adapter() {
    	  @Override
         public void syncSucceeded(Project project) {
            GradleWorkspace.this.scheduleGradleWorkspaceUpdate();
         }

    	  @Override
         public void syncFailed(Project project, String errorMessage) {
            GradleWorkspace.this.scheduleGradleWorkspaceUpdate();
         }

    	  @Override
         public void syncSkipped(Project project) {
            GradleWorkspace.this.scheduleGradleWorkspaceUpdate();
         }
      });
   }

   private static void setCppSupportDisabled(Project project, boolean disabled) {
      project.putUserData(OCParserDefinition.CPP_SUPPORT_DISABLED, Boolean.valueOf(disabled));
   }

   private void scheduleGradleWorkspaceUpdate() {
      this.myProjectJniDirectories = ImmutableSet.of();
      this.myProjectJniFiles = ImmutableSet.of();
      this.myResolveConfigurations = ImmutableMap.of();
      setCppSupportDisabled(this.myProject, false);
      (new Backgroundable(this.myProject, "Resolving C/C++ Configurations", false) {
         public void run(ProgressIndicator indicator) {
            GradleWorkspace.this.updateGradleWorkspace(indicator);
         }
      }).queue();
   }

   @SuppressWarnings("rawtypes")
private void updateGradleWorkspace(ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      String incompatibleModelVersion = getModelVersionIfIncompatible(this.myProject);
      boolean isNdkSpecified = this.isNdkDefined() && SdkPaths.validateAndroidNdk(this.getNdkPath(), false).success;
      HashSet<VirtualFile> projectJniDirectories = Sets.<VirtualFile>newHashSet();
      HashSet<VirtualFile> projectJniFiles = Sets.<VirtualFile>newHashSet();
      HashMap<String, ModuleResolveConfiguration>  projectResolveConfigurations = Maps.newHashMap();
      ArrayList<Module> nativeModules = Lists.newArrayList();
      ModuleManager moduleManager = ModuleManager.getInstance(this.myProject);
      Module[] modules = moduleManager.getModules();
      int len = modules.length;

      for(int i = 0; i < len; ++i) {
         Module module = modules[i];
         AndroidFacet facet = AndroidFacet.getInstance(module);
         if(facet != null) {
            AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
            if(androidModel != null) {
               Set<VirtualFile> moduleJniDirectories = getJniDirectories(facet);
               Set<VirtualFile> moduleJniFiles = getJniFiles(moduleJniDirectories);
               if(!moduleJniFiles.isEmpty()) {
                  if(incompatibleModelVersion != null) {
                     addModelVersionIncompatibilityNotification(incompatibleModelVersion, this.myProject);
                     return;
                  }

                  if(!isNdkSpecified) {
                     this.addNdkNotSpecifiedNotification(this.myProject, this.isNdkDefined());
                     return;
                  }
               }

               Map<String, ModuleResolveConfiguration>  moduleResolveConfigurations = this.getResolveConfigurations(this.myProject, this.myCompilerInfoCache, androidModel);
               if(!moduleJniFiles.isEmpty() && !moduleResolveConfigurations.isEmpty()) {
                  nativeModules.add(module);
               }

               projectJniDirectories.addAll(moduleJniDirectories);
               projectJniFiles.addAll(moduleJniFiles);
               projectResolveConfigurations.putAll(moduleResolveConfigurations);
            }
         }
      }

      this.myProjectJniDirectories = ImmutableSet.copyOf(projectJniDirectories);
      this.myProjectJniFiles = ImmutableSet.copyOf(projectJniFiles);
      this.myResolveConfigurations = ImmutableMap.copyOf(projectResolveConfigurations);
      if(!this.myProjectJniFiles.isEmpty() && !this.myResolveConfigurations.isEmpty()) {
         UsageTracker.getInstance().trackEvent("gradle", "cppSyncCompleted", (String)null, (Integer)null);
      }

      (new ReadAction() {
    	  @Override
         protected void run(Result result) throws Throwable {
            if(!GradleWorkspace.this.myProject.isDisposed()) {
               GradleWorkspace.this.myTrackers.getProjectFilesListTracker().incModificationCount();
               GradleWorkspace.this.myTrackers.getSourceFilesListTracker().incModificationCount();
               GradleWorkspace.this.myTrackers.getBuildConfigurationChangesTracker().incModificationCount();
               if(!ApplicationManager.getApplication().isUnitTestMode()) {
                  GradleWorkspace.this.myTrackers.getBuildSettingsChangesTracker().incModificationCount();
               }

            }
         }
      }).execute();
      createAndroidNativeRunConfigurations(this.myProject, nativeModules);
   }

   private static String getModelVersionIfIncompatible(Project project) {
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      Module[] arr = moduleManager.getModules();
      int len = arr.length;

      for(int i = 0; i < len; ++i) {
         Module module = arr[i];
         AndroidFacet androidFacet = AndroidFacet.getInstance(module);
         if(androidFacet != null) {
            AndroidGradleModel androidModel = AndroidGradleModel.get(androidFacet);
            if(androidModel != null) {
               AndroidProject androidProject = androidModel.getAndroidProject();
               String version = getModelVersionIfIncompatible(androidProject);
               if(version != null) {
                  return version;
               }
            }
         }
      }

      return null;
   }

   private static void addModelVersionIncompatibilityNotification(String modelVersion, Project project) {
      String text = String.format("Android plugin version %1$s is not compatible with the NDK support. ", new Object[]{modelVersion}) + "Please use the experimental plugin.";
      ProjectSyncMessages.getInstance(project).add(new Message("Gradle Sync Issue", Type.WARNING, new String[]{text}), new NotificationHyperlink[0]);
      String title = "Incompatible Android Plugin Version";
      AndroidGradleNotification.getInstance(project).showBalloon(title, text, NotificationType.WARNING);
   }

   private void addNdkNotSpecifiedNotification(Project project, boolean isNdkDefined) {
      boolean ndkInSdk = this.isNdkInSdk();
      NotificationHyperlink downloadLink = NdkLocationNotFoundErrorHandler.getSelectNdkNotificationHyperlink(!ndkInSdk);
      AndroidGradleNotification.getInstance(project).showBalloon("Project Sync Error", String.format("Android NDK location is not %1$s.", new Object[]{isNdkDefined?"valid":"specified"}), NotificationType.ERROR, new NotificationHyperlink[]{downloadLink});
   }

   private static Set<VirtualFile> getJniDirectories(AndroidFacet facet) {
      HashSet<VirtualFile> jniDirectories = Sets.newHashSet();
      Iterator<IdeaSourceProvider> iter = IdeaSourceProvider.getAllIdeaSourceProviders(facet).iterator();

      while(iter.hasNext()) {
         jniDirectories.addAll(iter.next().getJniDirectories());
      }

      return jniDirectories;
   }

   private static Set<VirtualFile> getJniFiles(Set<VirtualFile> jniDirectories) {
      final HashSet<VirtualFile> jniFiles = Sets.newHashSet();
      Iterator<VirtualFile> iter = jniDirectories.iterator();

      while(iter.hasNext()) {
         VirtualFile directory = iter.next();
         VfsUtilCore.visitChildrenRecursively(directory, new VirtualFileVisitor<VirtualFile>(new Option[0]) {
            public boolean visitFile(VirtualFile file) {
               if(file.getFileType() == OCFileType.INSTANCE) {
                  jniFiles.add(file);
               }

               return true;
            }
         });
      }

      return jniFiles;
   }

   private Map<String,ModuleResolveConfiguration> getResolveConfigurations(Project project, NdkCompilerInfoCache compilerInfoCache, AndroidGradleModel androidModel) {
      HashMap<String,ModuleResolveConfiguration> configurations = Maps.newHashMap();
      AndroidProject androidProject = androidModel.getAndroidProject();
      Map<String,NativeToolchain> toolchains = getToolchainsByName(androidProject);
      Iterator<Variant> varIter = androidProject.getVariants().iterator();

      while(varIter.hasNext()) {
         Variant variant = varIter.next();
         Iterator<NativeLibrary> libIter = getNativeLibraries(variant, androidProject).iterator();

         while(libIter.hasNext()) {
            NativeLibrary library = libIter.next();
            NativeToolchain toolchain = toolchains.get(library.getToolchainName());
            String key = getResolvedConfigurationKey(androidModel.getModuleName(), variant.getName(), library.getAbi());
            if(toolchain != null && !configurations.containsKey(key)) {
               ModuleResolveConfiguration configuration = new ModuleResolveConfiguration(project, androidModel.getRootDirPath(), compilerInfoCache, library, toolchain);
               configurations.put(key, configuration);
            }
         }
      }

      return configurations;
   }

   private static Map<String,NativeToolchain> getToolchainsByName(AndroidProject androidProject) {
      if(isModelVersionIncompatible(androidProject)) {
         return Collections.emptyMap();
      } else {
         HashMap<String,NativeToolchain> toolchains = Maps.<String,NativeToolchain>newHashMap();
         Iterator<NativeToolchain> iter = androidProject.getNativeToolchains().iterator();

         while(iter.hasNext()) {
            NativeToolchain toolchain = iter.next();
            toolchains.put(toolchain.getName(), toolchain);
         }

         return toolchains;
      }
   }

   private static String getResolvedConfigurationKey(String moduleName, String variantName, String abi) {
      return moduleName + ":" + variantName + ":" + abi;
   }

   private static void createAndroidNativeRunConfigurations(Project project, List<Module> nativeModules) {
      Iterator<Module> iter = nativeModules.iterator();

      while(iter.hasNext()){
          Module module = iter.next();
          AndroidFacet facet = AndroidFacet.getInstance(module);
          if(facet != null && !facet.isLibraryProject()){
        	  RunManager runManager = RunManager.getInstance(project);
              ConfigurationFactory configurationFactory = AndroidNativeRunConfigurationType.getInstance().getFactory();
              List<RunConfiguration> configs = runManager.getConfigurationsList(configurationFactory.getType());
              boolean found = false;
              Iterator<RunConfiguration> settings = configs.iterator();

              while(settings.hasNext()) {
                 RunConfiguration configuration = (RunConfiguration)settings.next();
                 if(configuration instanceof AndroidNativeRunConfiguration) {
                    AndroidNativeRunConfiguration androidNativeRunConfig = (AndroidNativeRunConfiguration)configuration;
                    if(((JavaRunConfigurationModule)androidNativeRunConfig.getConfigurationModule()).getModule() == module) {
                       found = true;
                       break;
                    }
                 }
              }

              if(!found) {
                 RunnerAndConfigurationSettings settings1 = runManager.createRunConfiguration(module.getName() + "-native", configurationFactory);
                 AndroidNativeRunConfiguration configuration1 = (AndroidNativeRunConfiguration)settings1.getConfiguration();
                 configuration1.setModule(module);
                 configuration1.setTargetSelectionMode(TargetSelectionMode.SHOW_DIALOG);
                 runManager.addConfiguration(settings1, false);
              }
          }
      }
   }

   public boolean isNdkDefined() {
      return this.getNdkPath() != null;
   }

   public File getNdkPath() {
      try {
         return (new LocalProperties(this.myProject)).getAndroidNdkPath();
      } catch (IOException var3) {
         String msg = String.format("Unable to read local.properties file of Project \'%1$s\'", new Object[]{this.myProject.getName()});
         LOG.info(msg, var3);
         return null;
      }
   }

   public File getSdkPath() {
      try {
         return (new LocalProperties(this.myProject)).getAndroidSdkPath();
      } catch (IOException var3) {
         String msg = String.format("Unable to read local.properties file of Project \'%1$s\'", new Object[]{this.myProject.getName()});
         LOG.info(msg, var3);
         return null;
      }
   }

   private boolean isNdkInSdk() {
      return this.getNdkPath() != null && FileUtil.filesEqual(this.getNdkPath().getParentFile(), this.getSdkPath());
   }

   public boolean isInProject(VirtualFile file) {
      return this.myProjectJniFiles.contains(file);
   }

   public boolean isInProjectDirectories(VirtualFile file) {
      Iterator<VirtualFile>  iter = this.myProjectJniDirectories.iterator();

      do {
         if(!iter.hasNext()) {
            return false;
         }
      } while(!VfsUtil.isAncestor(iter.next(), file, true));

      return true;
   }

   public boolean isInLibraries(VirtualFile file) {
      return false;
   }

   public boolean isInSDK(VirtualFile file) {
      return false;
   }

   public Collection<VirtualFile> getProjectFiles() {
      return this.myProjectJniFiles;
   }

   public Iterable<VirtualFile> getProjectFilesByName(String fileName) {
      ArrayList<VirtualFile> result = Lists.newArrayListWithExpectedSize(2);
      Iterator<VirtualFile> iter = this.getProjectFiles().iterator();

      while(iter.hasNext()) {
         VirtualFile file = (VirtualFile)iter.next();
         if(file.getName().equals(fileName)) {
            result.add(file);
         }
      }

      return result;
   }

   public boolean areFromSameProject(VirtualFile a, VirtualFile b) {
      return false;
   }

   public boolean areFromSamePackage(VirtualFile a, VirtualFile b) {
      return false;
   }

   @SuppressWarnings("rawtypes")
   public boolean isFromWrongSDK(OCSymbol symbol, VirtualFile contextFile) {
      return false;
   }

   public Collection<VirtualFile> getLibraryFiles() {
      return Collections.emptyList();
   }

   public Collection<VirtualFile> getLibraryFilesToBuildSymbols() {
      return this.getLibraryFiles();
   }

   public List<ModuleResolveConfiguration> getConfigurations() {
      return Lists.newArrayList(this.myResolveConfigurations.values());
   }

   public List<ModuleResolveConfiguration> getConfigurationsForFile(VirtualFile file) {
      if(this.myResolveConfigurations.isEmpty()) {
         return ImmutableList.of();
      } else {
         ArrayList<ModuleResolveConfiguration> configurations = Lists.newArrayList();
         if(file != null) {
            Module module = ModuleUtilCore.findModuleForFile(file, this.myProject);
            if(module != null) {
               AndroidFacet i$ = AndroidFacet.getInstance(module);
               if(i$ != null) {
                  AndroidGradleModel configuration = AndroidGradleModel.get(i$);
                  if(configuration != null) {
                     Variant headerRoots = configuration.getSelectedVariant();
                     Iterator<NativeLibrary> iterlib = getNativeLibraries(headerRoots, configuration.getAndroidProject()).iterator();
                     while(iterlib.hasNext()) {
                        NativeLibrary rootFile = (NativeLibrary)iterlib.next();
                        String key = getResolvedConfigurationKey(module.getName(), headerRoots.getName(), rootFile.getAbi());
                        ModuleResolveConfiguration configuration1 = (ModuleResolveConfiguration)this.myResolveConfigurations.get(key);
                        if(configuration1 != null) {
                           configurations.add(configuration1);
                        }
                     }
                  }
               }
            } else {
               Iterator<ModuleResolveConfiguration> iterconf = this.myResolveConfigurations.values().iterator();

               while(true) {
                  while(iterconf.hasNext()) {
                     ModuleResolveConfiguration configuration2 = (ModuleResolveConfiguration)iterconf.next();
                     HeaderRoots headerRoots1 = configuration2.getLibraryHeadersRoots(new OCResolveRootAndConfiguration((OCResolveConfiguration)null, file));
                     Iterator<PsiFileSystemItem> itersys = headerRoots1.getRoots().iterator();

                     while(itersys.hasNext()) {
                        PsiFileSystemItem root1 = (PsiFileSystemItem)itersys.next();
                        VirtualFile rootFile1 = root1.getVirtualFile();
                        if(rootFile1 != null && VfsUtilCore.isAncestor(rootFile1, file, true)) {
                           configurations.add(configuration2);
                           break;
                        }
                     }
                  }

                  return configurations;
               }
            }
         }

         return configurations;
      }
   }

   private static Collection<NativeLibrary> getNativeLibraries(Variant variant, AndroidProject androidProject) {
      if(isModelVersionIncompatible(androidProject)) {
         return Collections.emptyList();
      } else {
         AndroidArtifact mainArtifact = variant.getMainArtifact();
         Collection<NativeLibrary> nativeLibraries = mainArtifact.getNativeLibraries();
         if(nativeLibraries != null)
        	 return nativeLibraries;
         else
        	 return Collections.emptyList();
      }
   }

   private static boolean isModelVersionIncompatible(AndroidProject androidProject) {
      return getModelVersionIfIncompatible(androidProject) != null;
   }

   private static String getModelVersionIfIncompatible(AndroidProject androidProject) {
      String modelVersion = androidProject.getModelVersion();
      return !modelVersion.startsWith("1.0") && !modelVersion.startsWith("1.1") && androidProject.getApiVersion() >= 3?null:modelVersion;
   }

   public OCResolveConfiguration getSelectedResolveConfiguration() {
      return null;
   }

   public OCWorkspaceModificationTrackers getModificationTrackers() {
      return this.myTrackers;
   }
}
