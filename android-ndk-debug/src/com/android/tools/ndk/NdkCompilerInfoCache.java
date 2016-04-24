package com.android.tools.ndk;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.compiler.CidrCompilerResult;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.toolchains.CompilerInfoCache;
import com.jetbrains.cidr.toolchains.CompilerInfoCache.Entry;
import java.util.List;
import java.util.Map;

public class NdkCompilerInfoCache {
   private final CompilerInfoCache myCompilerInfoCache = new CompilerInfoCache();

   public NdkCompilerInfoCache.NdkCompilerInfo getCompilerInfo(Project project, OCCompilerSettings compilerSettings, OCLanguageKind lang, VirtualFile sourceFile) throws NdkCompilerInfoCache.NdkCompilerInvocationException {
      CidrCompilerResult<Entry>  compilerResult = this.myCompilerInfoCache.getCompilerInfoCache(project, compilerSettings, lang, sourceFile);
      Entry compilerInfo = (Entry)compilerResult.getResult();
      if(compilerInfo == null) {
         Throwable error = compilerResult.getError();
         if(error != null) {
            throw new NdkCompilerInfoCache.NdkCompilerInvocationException(error);
         } else {
            throw new NdkCompilerInfoCache.NdkCompilerInvocationException("Failed to get the compiler information for file " + sourceFile.getPath());
         }
      } else {
         return new NdkCompilerInfoCache.NdkCompilerInfo(compilerInfo);
      }
   }

   // $FF: synthetic class
   static class SyntheticClass_1 {
   }

   public static class NdkCompilerInvocationException extends Exception {
      /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private NdkCompilerInvocationException(String message) {
         super(message);
      }

      private NdkCompilerInvocationException(Throwable cause) {
         super(cause);
      }

      // $FF: synthetic method
      NdkCompilerInvocationException(Throwable x0, NdkCompilerInfoCache.SyntheticClass_1 x1) {
         this(x0);
      }

      // $FF: synthetic method
      NdkCompilerInvocationException(String x0, NdkCompilerInfoCache.SyntheticClass_1 x1) {
         this(x0);
      }
   }

   public static class NdkCompilerInfo {
      private final Entry myInfo;

      private NdkCompilerInfo(Entry info) {
         this.myInfo = info;
      }

      public String getDefines() {
         return this.myInfo.defines;
      }

      public Map<String, String> getFeatures() {
         return this.myInfo.features;
      }

      public Map<String, String> getExtensions() {
         return this.myInfo.extensions;
      }

      public List<PsiFileSystemItem> getHeaderSearchPaths() {
         return this.myInfo.headerSearchPaths;
      }

      // $FF: synthetic method
      NdkCompilerInfo(Entry x0, NdkCompilerInfoCache.SyntheticClass_1 x1) {
         this(x0);
      }
   }
}
