package com.android.tools.ndk.run.hybrid;

import com.google.common.collect.Lists;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.util.Range;
import com.intellij.xdebugger.XSourcePosition;
import java.util.List;

public class MethodCollector {
   private final List<PsiMethod> myMethods = Lists.newLinkedList();
   private Range<Integer> myLineRange;

   public MethodCollector(Project project, XSourcePosition pos) {
      this.doCollect(project, pos);
   }

   private void doCollect(Project project, XSourcePosition pos) {
      Document doc = FileDocumentManager.getInstance().getDocument(pos.getFile());
      if(doc != null) {
         PsiElement element = DebuggerUtilsEx.findElementAt(PsiDocumentManager.getInstance(project).getPsiFile(doc), pos.getOffset());

         while(true) {
            PsiElement line = element.getParent();
            if(line == null || line.getTextOffset() < pos.getOffset()) {
               int line1 = pos.getLine();
               int startOffset = doc.getLineStartOffset(line1);
               final TextRange exprRange = new TextRange(startOffset, doc.getLineEndOffset(line1));
               final Ref<TextRange> refExprRange = new Ref<TextRange>(exprRange);
               JavaRecursiveElementVisitor elementVisitor = new JavaRecursiveElementVisitor() {
                  @Override
            	   public void visitStatement(PsiStatement statement) {
                     if(exprRange.intersects(statement.getTextRange())) {
                        super.visitStatement(statement);
                     }

                  }

                  @Override
                  public void visitExpression(PsiExpression expression) {
                     TextRange range = expression.getTextRange();
                     if(exprRange.intersects(range)) {
                        refExprRange.set(((TextRange)refExprRange.get()).union(range));
                     }

                     super.visitExpression(expression);
                  }

                  @Override
                  public void visitCallExpression(PsiCallExpression expression) {
                     super.visitCallExpression(expression);
                     PsiMethod psiMethod = expression.resolveMethod();
                     if(psiMethod != null) {
                        MethodCollector.this.myMethods.add(psiMethod);
                     }

                  }
               };
               element.accept(elementVisitor);

               for(PsiElement sibling = element.getNextSibling(); sibling != null && exprRange.intersects(sibling.getTextRange()); sibling = sibling.getNextSibling()) {
                  sibling.accept(elementVisitor);
               }

               this.myLineRange = new Range<Integer>(doc.getLineNumber(exprRange.getStartOffset()),
            		   doc.getLineNumber(exprRange.getEndOffset()));
               return;
            }

            element = line;
         }
      }
   }

   public List<PsiMethod> getMethods() {
      return this.myMethods;
   }

   public Range<Integer> getLineRange() {
      return this.myLineRange;
   }
}
