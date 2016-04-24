package com.android.tools.ndk.jni;

import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.jetbrains.cidr.lang.psi.OCDeclarator;
import java.util.ArrayList;

public class JniNameMangler {
   public static PsiMethod findDeclaration(Project project, String jniMethodName) {
      if(!jniMethodName.startsWith("Java_")) {
         return null;
      } else {
         int pkgStart = "Java_".length();
         int length = jniMethodName.length();
         boolean lastWasUnderscore = false;

         int classBegin;
         for(classBegin = pkgStart; classBegin < length; ++classBegin) {
            char methodBegin = jniMethodName.charAt(classBegin);
            if(methodBegin == 95) {
               lastWasUnderscore = true;
            } else {
               if(Character.isUpperCase(methodBegin) && lastWasUnderscore) {
                  break;
               }

               lastWasUnderscore = false;
            }
         }

         int pos1 = jniMethodName.indexOf('_', classBegin) + 1;
         if(pos1 == 0) {
            return null;
         } else {
            for(lastWasUnderscore = true; pos1 < length; ++pos1) {
               char methodEnd = jniMethodName.charAt(pos1);
               if(methodEnd == '_') {
                  lastWasUnderscore = true;
               } else {
                  if(Character.isLowerCase(methodEnd) && lastWasUnderscore) {
                     break;
                  }

                  lastWasUnderscore = false;
               }
            }

            int pos2 = jniMethodName.indexOf("__", pos1);
            if(pos2 == -1) {
               pos2 = length;
            }

            String pkg = classBegin > pkgStart?jniDecodeUnderscore(jniMethodName.substring(pkgStart, classBegin - 1), true):null;
            String cls = jniDecodeUnderscore(jniMethodName.substring(classBegin, pos1 - 1), false);
            String methodName = jniDecodeUnderscore(jniMethodName.substring(pos1, pos2), false);
            String fqn = pkg != null?pkg + '.' + cls:cls;
            JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            PsiClass psiClass = psiFacade.findClass(fqn, GlobalSearchScope.allScope(project));
            if(psiClass == null) {
               cls = cls.replace('_', '.');
               fqn = pkg != null?pkg + '.' + cls:cls;
               psiClass = psiFacade.findClass(fqn, GlobalSearchScope.allScope(project));
               if(psiClass == null) {
                  return null;
               }
            }

            PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
            if(methods.length == 1) {
               return methods[0];
            } else {
               ArrayList<PsiMethod> nativeMethods = Lists.newArrayListWithExpectedSize(methods.length);
               PsiMethod[] iter = methods;
               int method = methods.length;

               for(int escaped = 0; escaped < method; ++escaped) {
                  PsiMethod method1 = iter[escaped];
                  if(method1.hasModifierProperty("native")) {
                     nativeMethods.add(method1);
                  }
               }

               if(nativeMethods.size() == 1) {
                  return (PsiMethod)nativeMethods.get(0);
               } 
               
               if(nativeMethods.isEmpty()) {
                  return null;
               } 

               for (PsiMethod method2 : nativeMethods) {
                   if (jniMethodName.equals(getJniMethodName(method2, true))) {
                       return method2;
                   }
               }
               for (PsiMethod method22 : nativeMethods) {
                   if (jniMethodName.equals(getJniMethodName(method22, false))) {
                       return method22;
                   }
               }
               return (PsiMethod) nativeMethods.get(0);
            }
         }
      }
   }

   static PsiMethod findDeclaration(OCDeclarator element) {
      String jniMethodName = element.getName();
      return jniMethodName != null?findDeclaration(element.getProject(), jniMethodName):null;
   }

   public static String getJniMethodName(PsiMethod method) {
      boolean overloaded = isOverloaded(method);
      return getJniMethodName(method, overloaded);
   }

   public static boolean isOverloaded(PsiMethod method) {
      PsiClass containingClass = method.getContainingClass();
      return containingClass != null && containingClass.findMethodsByName(method.getName(), false).length > 1;
   }

   public static String getJniMethodName(PsiMethod method, boolean overloaded) {
      return getJniMethodName(getJniClassName(method.getContainingClass()), method.getName(), method.getParameterList().getParameters(), overloaded);
   }

   public static String getJniMethodName(String jniClassName, String name, PsiParameter[] parameters, boolean overloaded) {
      StringBuilder sb = new StringBuilder();
      sb.append("Java_");
      sb.append(jniClassName);
      sb.append('_');
      sb.append(jniEncodeUnderscore(name, false));
      if(overloaded) {
         sb.append('_').append('_');
  
         for(int i = 0; i < parameters.length; ++i) {
            PsiParameter parameter = parameters[i];
            PsiType type = parameter.getType();
            type = TypeConversionUtil.erasure(type);
            String internal = getJvmType(type);
            sb.append(jniEscape(internal));
         }
      }

      return sb.toString();
   }

   public static String getJniClassName(PsiClass psiClass) {
      if(psiClass != null) {
         String qualifiedName = psiClass.getQualifiedName();
         return qualifiedName != null?jniEncodeUnderscore(qualifiedName, true):jniEncodeUnderscore(psiClass.getName(), false);
      } else {
         return "";
      }
   }

   public static String jniEncodeUnderscore(String javaName, boolean isPackageName) {
      return isPackageName?javaName.replaceAll("_", "_1").replace('.', '_'):javaName.replaceAll("_", "_1");
   }

   private static String jniDecodeUnderscore(String nativeName, boolean isPackageName) {
      return isPackageName?nativeName.replace('_', '.').replaceAll("\\.1", "_"):nativeName.replaceAll("_1", "_");
   }

   public static String getJniType(PsiType type) {
      if(type == PsiType.VOID) {
         return "void";
      } else if(type == PsiType.BOOLEAN) {
         return "jboolean";
      } else if(type == PsiType.INT) {
         return "jint";
      } else if(type == PsiType.LONG) {
         return "jlong";
      } else if(type == PsiType.FLOAT) {
         return "jfloat";
      } else if(type == PsiType.DOUBLE) {
         return "jdouble";
      } else if(type == PsiType.BYTE) {
         return "jbyte";
      } else if(type == PsiType.CHAR) {
         return "jchar";
      } else if(type == PsiType.SHORT) {
         return "jshort";
      } else if(type instanceof PsiArrayType) {
         type = ((PsiArrayType)type).getComponentType();
         return type == PsiType.BOOLEAN?"jbooleanArray":(type == PsiType.INT?"jintArray":(type == PsiType.LONG?"jlongArray":(type == PsiType.DOUBLE?"jdoubleArray":(type == PsiType.CHAR?"jcharArray":(type == PsiType.FLOAT?"jfloatArray":(type == PsiType.SHORT?"jshortArray":(type == PsiType.BYTE?"jbyteArray":"jobjectArray")))))));
      } else {
         return isString(type)?"jstring":(isClass(type)?"jclass":(isThrowable(type)?"jthrowable":"jobject"));
      }
   }

   public static boolean isClass(PsiType type) {
      return "java.lang.Class".equals(type.getCanonicalText());
   }

   public static boolean isString(PsiType type) {
      if(type instanceof PsiClassType) {
         String shortName = ((PsiClassType)type).getClassName();
         if(!Comparing.equal(shortName, "String")) {
            return false;
         }
      }

      return "java.lang.String".equals(type.getCanonicalText());
   }

   public static boolean isThrowable(PsiType type) {
      return type != null && InheritanceUtil.isInheritor(type, "java.lang.Throwable");
   }

   private static String getJvmType(PsiType type) {
      return PsiType.BOOLEAN == type?"Z":(PsiType.BYTE == type?"B":(PsiType.CHAR == type?"C":(PsiType.SHORT == type?"S":(PsiType.INT == type?"I":(PsiType.LONG == type?"J":(PsiType.FLOAT == type?"F":(PsiType.DOUBLE == type?"D":(PsiType.VOID == type?"V":"L" + type.getCanonicalText().replace('.', '/') + ";"))))))));
   }

   static String jniEscape(String name) {
      int len = name.length();
      StringBuilder sb = new StringBuilder(len);

      for(int i = 0; i < len; ++i) {
         char c = name.charAt(i);
         if(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
            sb.append(c);
         } else {
            switch(c) {
            case '.':
            case '/':
               sb.append('_');
               break;
            case ';':
               sb.append('_').append('2');
               break;
            case '[':
               sb.append('_').append('3');
               break;
            case '_':
               sb.append('_').append('1');
               break;
            default:
               sb.append('_').append('0');
               sb.append(c);
            }
         }
      }

      return sb.toString();
   }
}
