package com.android.tools.ndk.jni;

import com.android.tools.ndk.jni.JniNameMangler;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.Processor;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.symbols.OCSymbolKind;
import com.jetbrains.cidr.lang.symbols.symtable.OCGlobalProjectSymbolsCache;
import java.util.ArrayList;
import org.jetbrains.android.facet.AndroidFacet;

public class JniGotoDeclarationHandler implements GotoDeclarationHandler {
	@Override
   public PsiElement[] getGotoDeclarationTargets(PsiElement sourceElement, int offset, Editor editor) {
      return getGotoDeclarationTargets(sourceElement);
   }

   public static PsiElement[] getGotoDeclarationTargets(PsiElement sourceElement) {
      if(sourceElement instanceof PsiIdentifier) {
         return findNativeDeclarationTargets((PsiIdentifier)sourceElement);
      } else {
         if(sourceElement instanceof PsiKeyword) {
            PsiKeyword keyword = (PsiKeyword)sourceElement;
            if(keyword.getTokenType() == JavaTokenType.NATIVE_KEYWORD) {
               return findNativeDeclarationTargets(keyword);
            }
         } else if(sourceElement instanceof PsiMethod) {
            return findNativeDeclarationTargets((PsiMethod)sourceElement);
         }

         return null;
      }
   }

   private static PsiElement[] findNativeDeclarationTargets(PsiKeyword nativeKeyword) {
      assert nativeKeyword.getTokenType() == JavaTokenType.NATIVE_KEYWORD : nativeKeyword;

      if(nativeKeyword.getParent() instanceof PsiModifierList && nativeKeyword.getParent().getParent() instanceof PsiMethod) {
         PsiMethod method = (PsiMethod)nativeKeyword.getParent().getParent();
         return findNativeDeclarationTargets(method);
      } else {
         return null;
      }
   }

   private static PsiElement[] findNativeDeclarationTargets(PsiIdentifier identifier) {
      if(identifier.getParent() instanceof PsiMethod) {
         PsiMethod method = (PsiMethod)identifier.getParent();
         if(method.getModifierList().hasExplicitModifier("native")) {
            return findNativeDeclarationTargets(method);
         }
      }

      return null;
   }

   @SuppressWarnings("rawtypes")
	private static PsiElement[] findNativeDeclarationTargets(PsiMethod method) {
      PsiFile file = method.getContainingFile();
      if(file == null) {
         return null;
      } else {
         AndroidFacet facet = AndroidFacet.getInstance(file);
         if(facet == null) {
            return null;
         } else {
            Project project = facet.getModule().getProject();
            final ArrayList<PsiElement> elements = Lists.newArrayList();
            Processor<OCSymbol> processor = new Processor<OCSymbol>() {
				@Override
               public boolean process(OCSymbol symbol) {
                  if(symbol.getKind() == OCSymbolKind.FUNCTION_DECLARATION || symbol.getKind() == OCSymbolKind.FUNCTION_PREDECLARATION) {
                     PsiElement definition = symbol.locateDefinition();
                     if(definition != null) {
                        elements.add(definition);
                     }
                  }

                  return true;
               }
            };
            String signature = JniNameMangler.getJniMethodName(method, false);
            OCGlobalProjectSymbolsCache.processByQualifiedName(project, processor, signature);
            if(elements.isEmpty()) {
               signature = JniNameMangler.getJniMethodName(method, true);
               OCGlobalProjectSymbolsCache.processByQualifiedName(project, processor, signature);
            }

            return !elements.isEmpty()?(PsiElement[])elements.toArray(new PsiElement[elements.size()]):null;
         }
      }
   }

	@Override
   public String getActionText(DataContext context) {
      return null;
   }
}
