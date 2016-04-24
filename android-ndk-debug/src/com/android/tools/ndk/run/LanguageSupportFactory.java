package com.android.tools.ndk.run;

import com.android.tools.ndk.run.AndroidNativeRunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.cidr.execution.debugger.OCDebuggerLanguageSupportFactory;
import com.jetbrains.cidr.execution.debugger.OCDebuggerTypesHelper;
import com.jetbrains.cidr.lang.OCFileType;
import com.jetbrains.cidr.lang.OCLanguage;
import com.jetbrains.cidr.lang.psi.OCCodeFragment;
import com.jetbrains.cidr.lang.util.OCElementFactory;

public class LanguageSupportFactory extends OCDebuggerLanguageSupportFactory {
   public XDebuggerEditorsProvider createEditor(RunProfile profile) {
      return !(profile instanceof AndroidNativeRunConfiguration) && profile != null?null:new LanguageSupportFactory.DebuggerEditorsProvider();
   }
   
   private class DebuggerEditorsProvider extends XDebuggerEditorsProvider {
      private DebuggerEditorsProvider() {
      }

      @Override
      public FileType getFileType() {
         return OCFileType.INSTANCE;
      }

      @Override
      public Document createDocument(final Project project, final String text, XSourcePosition sourcePosition, final EvaluationMode mode) {
         final PsiElement context = OCDebuggerTypesHelper.getContextElement(sourcePosition, project);
         if(context != null && context.getLanguage() == OCLanguage.getInstance()) {
            return (Document)(new WriteAction<Document>() {
               @Override
            	protected void run(Result<Document> result) throws Throwable {
                  OCCodeFragment fragment;
                  if(mode == EvaluationMode.EXPRESSION){
                	  fragment = OCElementFactory.expressionCodeFragment(text, project, context, true, false);
                  }
                  else{
                	  fragment = OCElementFactory.expressionOrStatementsCodeFragment(text, project, context, true, false);
                  }
                  result.setResult(PsiDocumentManager.getInstance(project).getDocument(fragment));
               }
            }).execute().getResultObject();
         } else {
            LightVirtualFile plainTextFile = new LightVirtualFile("oc-debug-editor-when-no-source-position-available.txt", text);
            return FileDocumentManager.getInstance().getDocument(plainTextFile);
         }
      }
   }
}
