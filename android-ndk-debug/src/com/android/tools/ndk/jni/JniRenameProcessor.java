package com.android.tools.ndk.jni;

import com.android.tools.ndk.NdkHelper;
import com.android.tools.ndk.jni.JniGotoDeclarationHandler;
import com.android.tools.ndk.jni.JniNameMangler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import java.util.Map;

public class JniRenameProcessor extends RenamePsiElementProcessor {
   public boolean canProcessElement(PsiElement element) {
      return NdkHelper.isNdkProject(element.getProject()) && (element instanceof PsiMethod || element instanceof PsiClass);
   }

   @Override
   public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames) {
      if(element instanceof PsiMethod) {
         PsiMethod psiClass = (PsiMethod)element;
         renameNativeMethod(newName, allRenames, psiClass, JniNameMangler.getJniClassName(psiClass.getContainingClass()));
      } else if(element instanceof PsiClass) {
         PsiClass var12 = (PsiClass)element;
         String qualifiedName = var12.getQualifiedName();
         if(qualifiedName == null) {
            return;
         }

         int lastDot = qualifiedName.lastIndexOf(46);
         if(lastDot == -1) {
            return;
         }

         String newQualifiedName = qualifiedName.substring(0, lastDot) + '.' + newName;
         PsiMethod[] arr$ = var12.getMethods();
         int len$ = arr$.length;

         for(int i = 0; i < len$; ++i) {
            PsiMethod method = arr$[i];
            renameNativeMethod(method.getName(), allRenames, method, JniNameMangler.jniEncodeUnderscore(newQualifiedName, true));
         }
      }

   }

   private static void renameNativeMethod(String newName, Map<PsiElement, String> allRenames, PsiMethod method, String jniClassPrefix) {
      if(method.hasModifierProperty("native")) {
         PsiElement[] targets = JniGotoDeclarationHandler.getGotoDeclarationTargets(method);
         if(targets != null) {
            boolean overloaded = JniNameMangler.isOverloaded(method);
            String newMangledName = JniNameMangler.getJniMethodName(jniClassPrefix, newName, method.getParameterList().getParameters(), overloaded);
            PsiElement[] arr$ = targets;
            int len$ = targets.length;

            for(int i$ = 0; i$ < len$; ++i$) {
               PsiElement target = arr$[i$];
               allRenames.put(target, newMangledName);
            }

         }
      }
   }
}
