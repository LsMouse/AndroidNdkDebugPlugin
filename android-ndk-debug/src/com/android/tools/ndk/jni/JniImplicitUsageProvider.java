package com.android.tools.ndk.jni;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import com.jetbrains.cidr.lang.psi.OCDeclarator;
import com.jetbrains.cidr.lang.types.OCFunctionType;
import com.jetbrains.cidr.lang.types.OCType;
import java.util.List;

public class JniImplicitUsageProvider implements ImplicitUsageProvider {
	@Override
   public boolean isImplicitUsage(PsiElement element) {
      if(element instanceof OCDeclarator) {
         OCDeclarator declarator = (OCDeclarator)element;
         String name = declarator.getName();
         if(name == null || !name.startsWith("Java_")) {
            return false;
         }

         if(declarator.getType() instanceof OCFunctionType) {
            OCFunctionType function = (OCFunctionType)declarator.getType();
            List<? extends OCType> parameterTypes = function.getParameterTypes();
            if(parameterTypes.size() >= 1) {
               if(parameterTypes.get(0).getName().startsWith("JNIEnv")) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Override
   public boolean isImplicitRead(PsiElement element) {
      return false;
   }

   @Override
   public boolean isImplicitWrite(PsiElement element) {
      return false;
   }
}
