package com.android.tools.ndk;

import com.android.builder.model.NativeLibrary;
import com.android.builder.model.NativeToolchain;
import com.android.tools.ndk.NdkCompilerInfoCache;
import com.google.common.collect.Lists;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCImportsGraph;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContext;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrSwitchBuilder;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.toolchains.DefaultCidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceUtil;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerMacros;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeaderRoots;
import com.jetbrains.cidr.lang.workspace.headerRoots.IncludedHeadersRoot;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ModuleResolveConfiguration extends UserDataHolderBase implements OCResolveConfiguration {
   private static final NotificationGroup EVENT_LOG_NOTIFICATION = NotificationGroup.logOnlyGroup("NDK Compile");
   private static final Logger LOG = Logger.getInstance(ModuleResolveConfiguration.class);
   private final Project myProject;
   private final NativeLibrary myNativeLibrary;
   private final NativeToolchain myNativeToolchain;
   private final NdkCompilerInfoCache myCompilerInfoCache;
   private final ModuleResolveConfiguration.CompilerSettings myCompilerSettings;
   private final OCCompilerMacros myCompilerMacros;

   ModuleResolveConfiguration(Project project, File compilerWorkingDir, NdkCompilerInfoCache compilerInfoCache, NativeLibrary nativeLibrary, NativeToolchain nativeToolchain) {
      this.myProject = project;
      this.myNativeLibrary = nativeLibrary;
      this.myNativeToolchain = nativeToolchain;
      this.myCompilerInfoCache = compilerInfoCache;
      this.myCompilerSettings = new ModuleResolveConfiguration.CompilerSettings(compilerWorkingDir);
      this.myCompilerMacros = new ModuleResolveConfiguration.CompilerMacros();
   }

   @Override
   public Project getProject() {
      return this.myProject;
   }

   @Override
   public String getDisplayName(boolean shorten) {
      return shorten?this.myNativeLibrary.getToolchainName():String.format("Toolchain: \'%1$s\'\'", new Object[]{this.myNativeLibrary.getToolchainName()});
   }

   @Override
   public VirtualFile getPrecompiledHeader() {
      return null;
   }

   @Override
   public HeaderRoots getProjectHeadersRoots() {
      return new HeaderRoots(Collections.<PsiFileSystemItem> emptyList());
   }

   @Override
   public HeaderRoots getLibraryHeadersRoots(OCResolveRootAndConfiguration headerContext) {
      OCLanguageKind languageKind = headerContext.getKind();
      VirtualFile sourceFile = headerContext.getRootFile();
      if(languageKind == null) {
         languageKind = this.getLanguageKind(sourceFile);
      }

      ArrayList<PsiFileSystemItem> roots = Lists.newArrayList();
      if(languageKind == OCLanguageKind.C) {
         this.addHeaderRoots(roots, this.myNativeLibrary.getCIncludeDirs());
         this.addHeaderRoots(roots, this.myNativeLibrary.getCSystemIncludeDirs());
      } else {
         this.addHeaderRoots(roots, this.myNativeLibrary.getCppIncludeDirs());
         this.addHeaderRoots(roots, this.myNativeLibrary.getCppSystemIncludeDirs());
      }

      NdkCompilerInfoCache.NdkCompilerInfo compilerInfo = this.getNdkCompilerInfo(languageKind, sourceFile);
      if(compilerInfo != null) {
         roots.addAll(compilerInfo.getHeaderSearchPaths());
      }

      return new HeaderRoots(roots);
   }

   private void addHeaderRoots(List<PsiFileSystemItem> roots, List<File> includeDirs) {
      Iterator<File> iter = includeDirs.iterator();

      while(iter.hasNext()) {
         VirtualFile virtualFile = this.findVirtualFile(iter.next());
         if(virtualFile != null) {
            roots.add(new IncludedHeadersRoot(this.getProject(), virtualFile, false, false));
         }
      }

   }

   private NdkCompilerInfoCache.NdkCompilerInfo getNdkCompilerInfo(OCLanguageKind languageKind, VirtualFile sourceFile) {
      try {
         return this.myCompilerInfoCache.getCompilerInfo(this.myProject, this.myCompilerSettings, languageKind, sourceFile);
      } catch (NdkCompilerInfoCache.NdkCompilerInvocationException var4) {
         EVENT_LOG_NOTIFICATION.createNotification(var4.getMessage(), MessageType.ERROR).notify(this.myProject);
         LOG.warn(var4);
         return null;
      }
   }

   protected VirtualFile findVirtualFile(File ioFile) {
      return VfsUtil.findFileByIoFile(ioFile, true);
   }

   private OCLanguageKind getLanguageKind(VirtualFile sourceFile) {
      OCLanguageKind kind = OCLanguageKindCalculator.tryFileTypeAndExtension(this.myProject, sourceFile);
      return kind != null?kind:this.getMaximumLanguageKind();
   }

   @Override
   public OCLanguageKind getDeclaredLanguageKind(VirtualFile sourceOrHeaderFile) {
      if(sourceOrHeaderFile == null) {
         return null;
      } else {
         String fileName = sourceOrHeaderFile.getName();
         return OCFileTypeHelpers.isSourceFile(fileName)?this.getLanguageKind(sourceOrHeaderFile):(OCFileTypeHelpers.isHeaderFile(fileName)?this.getLanguageKind(this.getSourceFileForHeaderFile(sourceOrHeaderFile)):null);
      }
   }

   private VirtualFile getSourceFileForHeaderFile(VirtualFile headerFile) {
      ArrayList<VirtualFile> roots = new ArrayList<VirtualFile>(OCImportsGraph.getAllHeaderRoots(this.myProject, headerFile));
      String headerNameWithoutExtension = headerFile.getNameWithoutExtension();
      Iterator<VirtualFile> iter = roots.iterator();

      while(iter.hasNext()){
    	  VirtualFile root = iter.next();
    	  if(root.getNameWithoutExtension().equals(headerNameWithoutExtension)){
    		  return root;
    	  }
      }     
      return null;
   }

   @Override
   public OCLanguageKind getPrecompiledLanguageKind() {
      return this.getMaximumLanguageKind();
   }

   @Override
   public OCLanguageKind getMaximumLanguageKind() {
      return OCLanguageKind.CPP;
   }

   @Override
   public OCCompilerMacros getCompilerMacros() {
      return this.myCompilerMacros;
   }

   @Override
   public OCCompilerSettings getCompilerSettings() {
      return this.myCompilerSettings;
   }

   @Override
   public Object getIndexingCluster() {
      return null;
   }

   @Override
   public int compareTo(OCResolveConfiguration o) {
      return OCWorkspaceUtil.compareConfigurations(this, o);
   }

   public String getAbiString() {
      return this.myNativeLibrary.getAbi();
   }

   // $FF: synthetic class
   static class SyntheticClass_1 {
   }

   private class CompilerMacros extends OCCompilerMacros {
      private CompilerMacros() {
      }

      @Override
      protected void fillFileMacros(OCInclusionContext context, PsiFile sourceFile) {
         NdkCompilerInfoCache.NdkCompilerInfo compilerInfo = ModuleResolveConfiguration.this.getNdkCompilerInfo(context.getLanguageKind(), OCInclusionContextUtil.getVirtualFile(sourceFile));
         if(compilerInfo != null) {
            fillSubstitutions(context, compilerInfo.getDefines());
            this.enableClangFeatures(context, compilerInfo.getFeatures());
            this.enableClangExtensions(context, compilerInfo.getFeatures());
         }
      }
   }

   private class CompilerSettings extends OCCompilerSettings {
      private final CidrToolEnvironment myToolEnvironment = new DefaultCidrToolEnvironment();
      private final File myWorkingDir;

      CompilerSettings(File workingDir) {
         this.myWorkingDir = workingDir;
      }

      @Override
      public OCCompilerKind getCompiler(OCLanguageKind languageKind) {
         return null;
      }

      @Override
      public File getCompilerExecutable(OCLanguageKind lang) {
         return lang == OCLanguageKind.C?ModuleResolveConfiguration.this.myNativeToolchain.getCCompilerExecutable():ModuleResolveConfiguration.this.myNativeToolchain.getCppCompilerExecutable();
      }

      @Override
      public File getCompilerWorkingDir() {
         return this.myWorkingDir;
      }

      @Override
      public CidrToolEnvironment getEnvironment() {
         return this.myToolEnvironment;
      }

      @Override
      public CidrCompilerSwitches getCompilerSwitches(OCLanguageKind lang, VirtualFile sourceFile) {
         List<String> compilerFlags;
         if(lang == OCLanguageKind.C) {
            compilerFlags = ModuleResolveConfiguration.this.myNativeLibrary.getCCompilerFlags();
         } else {
            compilerFlags = ModuleResolveConfiguration.this.myNativeLibrary.getCppCompilerFlags();
         }

         CidrSwitchBuilder builder = new CidrSwitchBuilder();

         String compilerFlag;
         for(Iterator<String> iter = compilerFlags.iterator(); iter.hasNext(); builder.addParameter(compilerFlag)) {
            compilerFlag = iter.next();
            if(compilerFlag.contains(" ")) {
               if(compilerFlag.contains("\\ ")) {
                  compilerFlag = compilerFlag.replace("\\ ", " ");
               }

               compilerFlag = compilerFlag.replace(" ", "\\ ");
            }
         }

         return builder.build();
      }
   }
}
