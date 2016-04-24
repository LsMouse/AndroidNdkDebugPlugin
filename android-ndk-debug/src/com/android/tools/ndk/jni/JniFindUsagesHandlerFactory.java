package com.android.tools.ndk.jni;

import com.android.tools.ndk.jni.JniGotoDeclarationHandler;
import com.android.tools.ndk.jni.JniNameMangler;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.jetbrains.cidr.lang.psi.OCDeclarator;

public class JniFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
	@Override
   public boolean canFindUsages(PsiElement element) {
      if(!(element instanceof OCDeclarator)) {
         return element instanceof PsiMethod?((PsiMethod)element).getModifierList().hasExplicitModifier("native"):false;
      } else {
         OCDeclarator declarator = (OCDeclarator)element;
         String name = declarator.getName();
         return name != null && name.startsWith("Java_");
      }
   }

	@Override
   public FindUsagesHandler createFindUsagesHandler(PsiElement element, boolean forHighlightUsages) {
      return element instanceof OCDeclarator?findJavaReferences((OCDeclarator)element):findNativeReferences((PsiMethod)element);
   }

   private static FindUsagesHandler findNativeReferences(PsiMethod element) {
      PsiElement[] targets = JniGotoDeclarationHandler.getGotoDeclarationTargets(element);
      return targets != null && targets.length > 0?new JniFindUsagesHandlerFactory.MyFindUsagesHandler(element, targets):null;
   }

   private static FindUsagesHandler findJavaReferences(OCDeclarator element) {
      PsiMethod declaration = JniNameMangler.findDeclaration(element);
      return declaration != null?new JniFindUsagesHandlerFactory.MyFindUsagesHandler(element, new PsiElement[]{declaration}):null;
   }

   private static class MyFindUsagesHandler extends FindUsagesHandler {
      private final PsiElement[] myAdditionalElements;

      protected MyFindUsagesHandler(PsiElement element, PsiElement... additionalElements) {
         super(element);
         this.myAdditionalElements = additionalElements;
      }

      @Override
      public PsiElement[] getSecondaryElements() {
         return this.myAdditionalElements;
      }
   }
}
