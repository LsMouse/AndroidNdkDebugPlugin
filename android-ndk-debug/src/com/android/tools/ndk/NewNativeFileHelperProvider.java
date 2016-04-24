package com.android.tools.ndk;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.ui.FormBuilder;
import com.jetbrains.cidr.lang.actions.newFile.DialogWrapperFacade;
import com.jetbrains.cidr.lang.actions.newFile.OCNewFileHelper;
import com.jetbrains.cidr.lang.actions.newFile.OCNewFileHelperProvider;
import com.jetbrains.cidr.lang.actions.newFile.OCNewFileHelperUtil;
import com.jetbrains.cidr.lang.actions.newFile.OCNewFileActionBase.CreateFileDialogBase;
import java.awt.BorderLayout;
import java.util.Properties;
import javax.swing.JComponent;
import javax.swing.JPanel;

public class NewNativeFileHelperProvider implements OCNewFileHelperProvider {
	
	@Override
   public OCNewFileHelper createHelper() {
      return new NewNativeFileHelperProvider.Helper();
   }

   // $FF: synthetic class
   static class SyntheticClass_1 {
   }

   private static class Helper implements OCNewFileHelper {
      private Project myProject;
      private PsiFile mySampleFile;

      private Helper() {
      }

      @Override
      public boolean initFromDataContext(DataContext dataContext) {
         this.myProject = (Project)CommonDataKeys.PROJECT.getData(dataContext);
         return this.myProject != null;
      }

      @Override
      public boolean initFromFile(PsiFile file) {
         this.myProject = file.getProject();
         this.mySampleFile = file;
         return true;
      }

      @Override
      public String getDefaultClassPrefix() {
         return "";
      }

      @Override
      public boolean canChangeDir() {
         return false;
      }

      @SuppressWarnings("rawtypes")
      @Override
	public DialogWrapper createDialog(CreateFileDialogBase peer, PsiDirectory selectedDir, DataContext dataContext) {
         return new NewNativeFileHelperProvider.Helper.Dialog(peer, this.mySampleFile);
      }

      @Override
      public void setProperties(DialogWrapper dialog, Properties properties, PsiFile sampleFile, Project project) {
         String projectName = project.getName();
         OCNewFileHelperUtil.fillCommonTemplateProperties(properties, projectName);
      }

      @SuppressWarnings("unchecked")
      @Override
	public void doCreateFiles(Project project, PsiDirectory directory, String[] fileNames, PsiFile[] resultElements, DialogWrapper dialog, PsiFile sampleFile) {
         Function<VirtualFile, Void> handler = Function.NULL;
         OCNewFileHelperUtil.addCreatedFiles(directory, fileNames, resultElements, project, handler);
      }

      private static class Dialog extends DialogWrapper implements DialogWrapperFacade {
		@SuppressWarnings("rawtypes")
		private final CreateFileDialogBase myPeer;

		@SuppressWarnings("rawtypes")
		public Dialog(CreateFileDialogBase peer, PsiFile sampleFile) {
            super(false);
            this.myPeer = peer;
            peer.setWrapper(this);
            this.setTitle(peer.getTitle());
            this.init();
         }

         @Override
         protected JComponent createCenterPanel() {
            FormBuilder formBuilder = FormBuilder.createFormBuilder().setVertical(false);
            this.myPeer.fillGenericControls(formBuilder);
            this.myPeer.validateOkAction();
            JPanel myMainPanel = formBuilder.getPanel();
            JPanel result = new JPanel(new BorderLayout());
            result.add(myMainPanel, "Center");
            return result;
         }

         @Override
         public JComponent getPreferredFocusedComponent() {
            return this.myPeer.getPreferredFocusedComponent();
         }

         @Override
         protected void doOKAction() {
            if(this.myPeer.checkCanDoOKAction()) {
               super.doOKAction();
            }

         }

         @Override
         public void setOKEnabled(boolean isEnable) {
            this.setOKActionEnabled(isEnable);
         }

         @Override
         public void setErrorMessage(String text) {
            this.setErrorText(text);
         }

         @Override
         protected void dispose() {
            super.dispose();
            Disposer.dispose(this.myPeer.getDisposable());
         }
      }
   }
}
