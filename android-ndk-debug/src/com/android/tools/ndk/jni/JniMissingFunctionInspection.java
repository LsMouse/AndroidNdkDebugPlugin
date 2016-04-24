package com.android.tools.ndk.jni;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.ndk.NdkHelper;
import com.android.tools.ndk.jni.JniGotoDeclarationHandler;
import com.android.tools.ndk.jni.JniNameMangler;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.PsiNavigateUtil;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.actions.newFile.OCNewFileHelperUtil;
import com.jetbrains.cidr.lang.psi.OCDeclaration;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.refactoring.util.OCChangeUtil;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.symbols.OCSymbolKind;
import com.jetbrains.cidr.lang.symbols.cpp.OCSymbolWithQualifiedName;
import com.jetbrains.cidr.lang.symbols.symtable.OCGlobalProjectSymbolsCache;
import com.jetbrains.cidr.lang.util.OCElementFactory;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import org.jetbrains.android.facet.AndroidFacet;

public class JniMissingFunctionInspection extends LocalInspectionTool {
   private static final String REGISTER_NATIVES = "RegisterNatives";

   @Override
   public PsiElementVisitor buildVisitor(final ProblemsHolder holder, boolean isOnTheFly, LocalInspectionToolSession session) {
      return new JavaElementVisitor() {
    	  @Override
         public void visitMethod(PsiMethod method) {
            if(method.hasModifierProperty("native")) {
               PsiElement[] targets = JniGotoDeclarationHandler.getGotoDeclarationTargets(method);
               if(targets == null || targets.length == 0) {
                  Project project = holder.getProject();
                  if(JniMissingFunctionInspection.projectUsesRegisterNatives(project)) {
                     return;
                  }

                  String jniMethodName = JniNameMangler.getJniMethodName(method);
                  String message = String.format("Cannot resolve corresponding JNI function %1$s", new Object[]{jniMethodName});
                  LocalQuickFix[] fixes = new LocalQuickFix[]{new JniMissingFunctionInspection.CreateJniFunctionFix(method, jniMethodName)};
                  if(!NdkHelper.isNdkProject(project)) {
                     fixes = LocalQuickFix.EMPTY_ARRAY;
                  }

                  holder.registerProblem((PsiElement)(method.getNameIdentifier() != null?method.getNameIdentifier():method), message, ProblemHighlightType.ERROR, fixes);
               }
            }

         }
      };
   }

   @SuppressWarnings("rawtypes")
private static boolean projectUsesRegisterNatives(Project project) {
      final Ref<Boolean> registersNatives = new Ref<Boolean>(false);
      OCGlobalProjectSymbolsCache.processByQualifiedName(project, new Processor<OCSymbol>() {
		@Override
         public boolean process(OCSymbol symbol) {
            if(symbol.getKind() == OCSymbolKind.STRUCT_FIELD) {
               OCFile definition = symbol.getContainingOCFile();
               if(definition != null) {
                  symbol = definition.findSymbol(REGISTER_NATIVES, OCSymbolWithQualifiedName.class);
                  if(symbol != null) {
                     PsiElement scope = symbol.locateDefinition();
                     if(scope != null) {
                        GlobalSearchScope all = GlobalSearchScope.projectScope(scope.getProject());
                        Collection<PsiReference> all1 = ReferencesSearch.search(scope.getParent(), all).findAll();
                        if(!all1.isEmpty()) {
                           registersNatives.set(Boolean.valueOf(true));
                        }
                     }
                  }
               }
            } else if(symbol.getKind() == OCSymbolKind.FUNCTION_DECLARATION || symbol.getKind() == OCSymbolKind.FUNCTION_PREDECLARATION) {
               PsiElement definition1 = symbol.locateDefinition();
               if(definition1 != null) {
                  GlobalSearchScope scope1 = GlobalSearchScope.projectScope(definition1.getProject());
                  Collection<PsiReference> all2 = ReferencesSearch.search(definition1.getParent(), scope1).findAll();
                  if(!all2.isEmpty()) {
                     registersNatives.set(Boolean.valueOf(true));
                  }
               }
            }
            return true;
         }
      }, REGISTER_NATIVES);
      return registersNatives.get().booleanValue();
   }

   private static class CreateJniFunctionFix implements LocalQuickFix {
      private final PsiMethod myMethod;
      private final String myMethodName;

      public CreateJniFunctionFix(PsiMethod method, String methodName) {
         this.myMethod = method;
         this.myMethodName = methodName;
      }

      @Override
      public String getName() {
         return String.format("Create function %1$s", new Object[]{this.myMethodName});
      }

      @Override
      public String getFamilyName() {
         return "Create JNI function";
      }

      @Override
      public void applyFix(Project project, ProblemDescriptor descriptor) {
         OCFile file = this.findJniTargetFile(project);
         if(file == null) {
            Logger.getInstance(JniMissingFunctionInspection.class).warn("Can\'t create JNI file");
         } else {
            OCLanguageKind kind = OCLanguageKindCalculator.calculateLanguageKind(file);
            this.createMethod(project, file, kind == OCLanguageKind.CPP, true);
         }
      }

      private void createMethod(Project project, OCFile file, boolean cpp, boolean insertConversions) {
         StringBuilder sb = new StringBuilder();
         PsiType returnType = this.myMethod.getReturnType();
         String jniReturnType = null;
         sb.append("JNIEXPORT ");
         if(returnType != null) {
            jniReturnType = JniNameMangler.getJniType(returnType);
            sb.append(jniReturnType);
         } else {
            sb.append("void");
         }

         PsiParameter[] parameters = this.myMethod.getParameterList().getParameters();
         sb.append(" JNICALL");
         sb.append('\n');
         sb.append(this.myMethodName);
         sb.append("(JNIEnv* env, ");
         String objName;
         if(this.myMethod.hasModifierProperty("static")) {
            sb.append("jclass");
            objName = "type";
         } else {
            sb.append("jobject");
            objName = "instance";
         }

         sb.append(" ");

         while(hasParameterName(parameters, objName)) {
            objName = objName + "_";
         }

         sb.append(objName);
         StringBuilder allocate = new StringBuilder();
         StringBuilder deallocate = new StringBuilder();

         String envName;
         for(envName = "env"; hasParameterName(parameters, envName); envName = envName + "_") {
            ;
         }

         if(parameters.length > 0) {
            PsiParameter[] text = parameters;
            int declaration = parameters.length;

            for(int added = 0; added < declaration; ++added) {
               PsiParameter comments = text[added];
               sb.append(", ");
               PsiType firstComment = comments.getType();
               String textRange = JniNameMangler.getJniType(firstComment);
               String editor = comments.getName();
               if(insertConversions) {
                  if("jstring".equals(textRange)) {
                     editor = convertString(parameters, editor, envName, allocate, deallocate, cpp);
                  } else if("jintArray".equals(textRange)) {
                     editor = convertArray("jint", "Int", parameters, editor, envName, allocate, deallocate, cpp);
                  } else if("jlongArray".equals(textRange)) {
                     editor = convertArray("jlong", "Long", parameters, editor, envName, allocate, deallocate, cpp);
                  } else if("jbooleanArray".equals(textRange)) {
                     editor = convertArray("jboolean", "Boolean", parameters, editor, envName, allocate, deallocate, cpp);
                  } else if("jdoubleArray".equals(textRange)) {
                     editor = convertArray("jdouble", "Double", parameters, editor, envName, allocate, deallocate, cpp);
                  } else if("jcharArray".equals(textRange)) {
                     editor = convertArray("jchar", "Char", parameters, editor, envName, allocate, deallocate, cpp);
                  } else if("jbyteArray".equals(textRange)) {
                     editor = convertArray("jbyte", "Byte", parameters, editor, envName, allocate, deallocate, cpp);
                  } else if("jshortArray".equals(textRange)) {
                     editor = convertArray("jshort", "Short", parameters, editor, envName, allocate, deallocate, cpp);
                  } else if("jfloatArray".equals(textRange)) {
                     editor = convertArray("jfloat", "Float", parameters, editor, envName, allocate, deallocate, cpp);
                  }
               }

               sb.append(textRange);
               sb.append(' ');
               sb.append(editor);
            }
         }

         sb.append(")\n{\n");
         sb.append(allocate);
         sb.append("\n // TODO\n\n");
         sb.append(deallocate);
         if(insertConversions && jniReturnType != null && "jstring".equals(jniReturnType)) {
            sb.append("\n");
            sb.append("return ").append(!cpp?"(*":"").append(envName).append(!cpp?")":"").append("->NewStringUTF(").append(!cpp?envName:"").append(!cpp?", ":"").append("returnValue);\n");
         }

         sb.append("}");
         String var20 = sb.toString();
         OCDeclaration var21 = OCElementFactory.declarationFromText(var20, file, true);
         OCDeclaration var22 = OCChangeUtil.add(file, var21);
         OCChangeUtil.reformatTextIfNotInjected(file, var22.getTextOffset(), var22.getTextOffset() + var22.getTextLength());
         Collection<PsiComment> var23 = PsiTreeUtil.findChildrenOfType(var22, PsiComment.class);
         if(var23.size() > 1) {
            PsiComment var24 = var23.iterator().next();
            PsiNavigateUtil.navigate(var24);
            TextRange var25 = var24.getTextRange();
            Editor var26 = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if(var26 != null) {
               var26.getSelectionModel().setSelection(var25.getStartOffset(), var25.getEndOffset());
            }
         } else {
            PsiNavigateUtil.navigate(var22);
         }

      }

      private static boolean hasParameterName(PsiParameter[] parameters, String candidate) {
         PsiParameter[] arr$ = parameters;
         int len$ = parameters.length;

         for(int i$ = 0; i$ < len$; ++i$) {
            PsiParameter parameter = arr$[i$];
            if(candidate.equals(parameter.getName())) {
               return true;
            }
         }

         return false;
      }

      private static String convertString(PsiParameter[] parameters, String name, String envName, StringBuilder allocate, StringBuilder deallocate, boolean cpp) {
         String tempName = findUniqueParameterName(parameters, name);
         allocate.append("const char *").append(name).append(" = ").append(!cpp?"(*":"").append(envName).append(!cpp?")":"").append("->GetStringUTFChars(").append(!cpp?envName:"").append(!cpp?", ":"").append(tempName).append(", 0);\n");
         deallocate.append(!cpp?"(*":"").append(envName).append(!cpp?")":"").append("->ReleaseStringUTFChars(").append(!cpp?envName:"").append(!cpp?", ":"").append(tempName).append(", ").append(name).append(");\n");
         return tempName;
      }

      private static String convertArray(String elementType, String methodNameMiddle, PsiParameter[] parameters, String name, String envName, StringBuilder allocate, StringBuilder deallocate, boolean cpp) {
         String tempName = findUniqueParameterName(parameters, name);
         allocate.append(elementType).append(" *").append(name).append(" = ").append(!cpp?"(*":"").append(envName).append(!cpp?")":"").append("->").append("Get").append(methodNameMiddle).append("ArrayElements").append("(").append(!cpp?envName:"").append(!cpp?", ":"").append(tempName).append(", NULL);\n");
         deallocate.append(!cpp?"(*":"").append(envName).append(!cpp?")":"").append("->").append("Release").append(methodNameMiddle).append("ArrayElements").append("(").append(!cpp?envName:"").append(!cpp?", ":"").append(tempName).append(", ").append(name).append(", 0);\n");
         return tempName;
      }

      private static String findUniqueParameterName(PsiParameter[] parameters, String name) {
         String tempName;
         for(tempName = name + "_"; hasParameterName(parameters, tempName); tempName = tempName + "_") {
            ;
         }

         return tempName;
      }

      private OCFile findJniTargetFile(Project project) {
         OCFile existing = findFileWithJniFunctions(project);
         return existing != null?existing:this.createNewTargetFile(project);
      }

      private AndroidFacet pickModule() {
         Module module = AndroidPsiUtils.getModuleSafely(this.myMethod);
         if(module != null) {
            AndroidFacet arr$ = AndroidFacet.getInstance(module);
            if(arr$ != null) {
               return arr$;
            }
         }

         Module[] var7 = ModuleManager.getInstance(this.myMethod.getProject()).getModules();
         int len$ = var7.length;

         for(int i$ = 0; i$ < len$; ++i$) {
            Module m = var7[i$];
            AndroidFacet facet = AndroidFacet.getInstance(m);
            if(facet != null) {
               return facet;
            }
         }

         return null;
      }

      @SuppressWarnings("unchecked")
	private OCFile createNewTargetFile(Project project) {
         AndroidFacet facet = this.pickModule();
         if(facet == null) {
            return null;
         } else {
            VirtualFile dir = null;
            PsiDirectory psiDirectory = null;
            if(facet.getAndroidModel() != null) {
               SourceProvider fileName = facet.getMainSourceProvider();
               Collection<File> newFile = fileName.getCDirectories();
               if(!newFile.isEmpty()) {
                  File file = newFile.iterator().next();
                  dir = LocalFileSystem.getInstance().findFileByIoFile(file);
                  if(dir == null) {
                     psiDirectory = DirectoryUtil.mkdirs(PsiManager.getInstance(project), file.getPath());
                     if(psiDirectory != null) {
                        dir = psiDirectory.getVirtualFile();
                     }
                  } else {
                     psiDirectory = PsiManager.getInstance(project).findDirectory(dir);
                  }
               }
            }

            if(dir != null && psiDirectory != null) {
               String fileName1 = this.pickFilename();
               VirtualFile newFile1 = dir.findChild(fileName1);
               if(newFile1 == null) {
                  OCNewFileHelperUtil.addCreatedFiles(psiDirectory, new String[]{fileName1}, new PsiFile[]{null}, project, Function.NULL);
                  newFile1 = dir.findChild(fileName1);
                  if(newFile1 == null) {
                     return null;
                  }

                  try {
                     VfsUtil.saveText(newFile1, "#include <jni.h>\n");
                  } catch (IOException var10) {
                     Logger.getInstance(JniMissingFunctionInspection.class).error(var10);
                     return null;
                  }
               }

               PsiFile file1 = PsiManager.getInstance(project).findFile(newFile1);
               if(file1 instanceof OCFile) {
                  PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
                  Document document = documentManager.getDocument(file1);
                  if(document != null) {
                     documentManager.commitDocument(document);
                  }

                  return (OCFile)file1;
               } else {
                  return null;
               }
            } else {
               return null;
            }
         }
      }

      private String pickFilename() {
         PsiFile containingFile = this.myMethod.getContainingFile();
         if(containingFile != null) {
            Iterator<PsiMethodCallExpression> cls = PsiTreeUtil.findChildrenOfType(containingFile, PsiMethodCallExpression.class).iterator();

            while(cls.hasNext()) {
            	PsiMethodCallExpression expression = cls.next();
            	PsiReferenceExpression methodExpression = expression.getMethodExpression();
            	String referenceName = methodExpression.getReferenceName();
            	if("loadLibrary".equals(referenceName)){
            		PsiElement resolved = methodExpression.resolve();
            		if(resolved instanceof PsiMethod) {
            			PsiMethod argumentList = (PsiMethod)resolved;
            			PsiClass literal = argumentList.getContainingClass();
            			if(literal != null) {
            				String value = literal.getQualifiedName();
            				if(value == null || value.equals("java.lang.System")){
            					PsiExpression[] exps = expression.getArgumentList().getExpressions();
            					if(exps.length == 1 && exps[0] instanceof PsiLiteralExpression){
            						Object exp = ((PsiLiteralExpression)exps[0]).getValue();
            						if(exp instanceof String){
            							String name = (String)exp;
            							StringBuilder sb = new StringBuilder();
            							for(int i = 0; i < name.length(); ++i) {
            								char c = name.charAt(i);
            								if(Character.isLetter(c) || Character.isDigit(c) || c == '_' || c == '-') {
            									sb.append(c);
            								}
            							}
            							if(sb.length() > 0) {
            								sb.append(".c");
            								return sb.toString();
            							}
            						}
            					}
            				}
            			}
            		}
            	}
            }
         }

         PsiClass cls = this.myMethod.getContainingClass();
         return cls != null?cls.getName().toLowerCase(Locale.US) + ".c":"jni.c";
      }

      @SuppressWarnings("rawtypes")
	private static OCFile findFileWithJniFunctions(Project project) {
         final Ref<OCFile> ref = new Ref<OCFile>();
         Iterator<String> iter = OCGlobalProjectSymbolsCache.getAllSymbolNames(project).iterator();

         while(iter.hasNext()) {
            String symbol = (String)iter.next();
            if(symbol.startsWith("Java_")) {
               Processor<OCSymbol> processor = new Processor<OCSymbol>() {
            	   @Override
                  public boolean process(OCSymbol symbol) {
                     if(symbol.getKind() == OCSymbolKind.FUNCTION_DECLARATION || symbol.getKind() == OCSymbolKind.FUNCTION_PREDECLARATION) {
                        OCFile containing = symbol.getContainingOCFile();
                        if(containing != null) {
                           ref.set(containing);
                           return false;
                        }
                     }

                     return true;
                  }
               };
               OCGlobalProjectSymbolsCache.processTopLevelSymbols(project, processor, symbol);
               OCFile file = ref.get();
               if(file != null) {
                  return file;
               }
            }
         }

         return null;
      }
   }
}
