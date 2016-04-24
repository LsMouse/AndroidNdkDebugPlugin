package com.android.tools.ndk;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculatorHelper;

public class GradleLanguageKindCalculatorHelper implements OCLanguageKindCalculatorHelper {
	@Override
   public OCLanguageKind getSpecifiedLanguage(Project project, VirtualFile file) {
      return null;
   }

	@Override
   public OCLanguageKind getLanguageByExtension(Project project, String name) {
      String extension = FileUtilRt.getExtension(name);
      return extension.equalsIgnoreCase("c")?OCLanguageKind.C:OCLanguageKind.CPP;
   }
}
