package com.android.tools.ndk.jni;

import com.android.tools.ndk.jni.JniGotoDeclarationHandler;
import com.android.tools.ndk.jni.JniNameMangler;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.PsiNavigateUtil;
import com.jetbrains.cidr.lang.psi.OCDeclarator;
import icons.CidrLangIcons;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JniRelatedItemMarkerProvider extends RelatedItemLineMarkerProvider {
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
   protected void collectNavigationMarkers(PsiElement element, Collection result) {
      if(element instanceof OCDeclarator) {
         OCDeclarator method = (OCDeclarator)element;
         String targets = method.getName();
         if(targets == null || !targets.startsWith("Java_")) {
            return;
         }

         PsiMethod items = JniNameMangler.findDeclaration(method);
         if(items != null) {
            List nameIdentifier = Collections.singletonList(new GotoRelatedItem(items));
            PsiElement textRange = method.getNameIdentifier();
            if(textRange == null) {
               return;
            }

            TextRange handler = textRange.getTextRange();
            JniRelatedItemMarkerProvider.ToJavaNavigationHandler info = new JniRelatedItemMarkerProvider.ToJavaNavigationHandler();
            result.add(new RelatedItemLineMarkerInfo(method, handler, CidrLangIcons.AssocFile, 
            		6, info, info, Alignment.LEFT, nameIdentifier));
         }
      } else if(element instanceof PsiMethod) {
         PsiMethod var11 = (PsiMethod)element;
         if(var11.hasModifierProperty("native")) {
            PsiElement[] ele = JniGotoDeclarationHandler.getGotoDeclarationTargets(element);
            if(ele != null && ele.length > 0) {
               ArrayList<GotoRelatedItem> var13 = new ArrayList<GotoRelatedItem>(ele.length);
               for(int i = 0; i < ele.length; ++i) {
                  var13.add(new GotoRelatedItem(ele[i]));
               }

               PsiIdentifier var15 = var11.getNameIdentifier();
               if(var15 == null) {
                  return;
               }

               TextRange var17 = var15.getTextRange();
               JniRelatedItemMarkerProvider.ToNativeNavigationHandler var19 = new JniRelatedItemMarkerProvider.ToNativeNavigationHandler();
               result.add(new RelatedItemLineMarkerInfo(var11, var17, CidrLangIcons.AssocFile, 6, var19, var19, Alignment.LEFT, var13));
            }
         }
      }

   }

   // $FF: synthetic class
   static class SyntheticClass_1 {
   }

   private static class ToJavaNavigationHandler implements GutterIconNavigationHandler<PsiElement>, Function<OCDeclarator, String> {
      private ToJavaNavigationHandler() {
      }

      @Override
      public void navigate(MouseEvent e, PsiElement paramT) {
    	  OCDeclarator declarator = (OCDeclarator)paramT;
         PsiMethod declaration = JniNameMangler.findDeclaration(declarator);
         if(declaration != null) {
            PsiNavigateUtil.navigate(declaration);
         }

      }

      @Override
      public String fun(OCDeclarator declarator) {
         PsiMethod declaration = JniNameMangler.findDeclaration(declarator);
         if(declaration != null) {
            PsiClass containingClass = declaration.getContainingClass();
            return "Native declaration: " + declaration.getName() + (containingClass != null?" in " + containingClass.getName():"");
         } else {
            return null;
         }
      }

   }

   private static class ToNativeNavigationHandler implements GutterIconNavigationHandler<PsiElement>, Function<PsiMethod,String> {
      private ToNativeNavigationHandler() {
      }

      @Override
      public void navigate(MouseEvent e, PsiElement paramT) {
    	  PsiMethod method = (PsiMethod)paramT;
         PsiElement[] targets = JniGotoDeclarationHandler.getGotoDeclarationTargets(method);
         if(targets != null && targets.length != 0) {
            if(targets.length == 1) {
               PsiNavigateUtil.navigate(targets[0]);
            } else {
               JBPopup popup = NavigationUtil.getPsiElementPopup(targets, "<html>Choose target for <b>" + method.getName() + "<b></html>");
               popup.show(new RelativePoint(e));
            }
         }

      }

      @Override
      public String fun(PsiMethod method) {
         return "JNI implementation: " + JniNameMangler.getJniMethodName(method);
      }
   }
}
