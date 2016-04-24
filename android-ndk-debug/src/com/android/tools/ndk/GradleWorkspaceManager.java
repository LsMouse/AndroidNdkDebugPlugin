package com.android.tools.ndk;

import com.android.tools.ndk.GradleWorkspace;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceManager;

public class GradleWorkspaceManager extends OCWorkspaceManager {
   private final Project myProject;

   public GradleWorkspaceManager(Project project) {
      this.myProject = project;
   }

   @Override
   public OCWorkspace getWorkspace() {
      return GradleWorkspace.getInstance(this.myProject);
   }
}
