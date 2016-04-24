package com.android.tools.ndk.editors;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.ndk.GradleWorkspace;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications.Provider;
import com.jetbrains.cidr.lang.OCFileType;

@SuppressWarnings("rawtypes")
public class NewCppSourceNotificationProvider extends Provider {
   private static final Key KEY = Key.create("android.ndk.editors.newCppSource");
   private final Project myProject;

   public NewCppSourceNotificationProvider(Project project) {
      this.myProject = project;
   }

   @Override
   public Key getKey() {
      return KEY;
   }

   @Override
   public EditorNotificationPanel createNotificationPanel(VirtualFile file, FileEditor fileEditor) {
      if(file.getFileType() != OCFileType.INSTANCE) {
         return null;
      } else {
         GradleWorkspace workspace = GradleWorkspace.getInstance(this.myProject);
         return (!workspace.isInProject(file) && workspace.isInProjectDirectories(file))?
        		 new NewCppSourceNotificationProvider.StaleCppProjectNotificationPanel(
        				 "This file has been added after the last project sync with Gradle. Please sync the project again for the NDK support to work properly."):null;
      }
   }

   
   private class StaleCppProjectNotificationPanel extends EditorNotificationPanel {
      /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	
	StaleCppProjectNotificationPanel(String message) {
         this.setText(message);
         this.createActionLabel("Sync Now", new Runnable() {
        	 @Override
            public void run() {
               GradleProjectImporter.getInstance().requestProjectSync(NewCppSourceNotificationProvider.this.myProject, (GradleSyncListener)null);
            }
         });
      }
   }
}
