package com.android.tools.ndk.run.hybrid;

import com.android.tools.ndk.jni.JniNameMangler;
import com.android.tools.ndk.run.hybrid.MethodCollector;
import com.android.tools.ndk.run.hybrid.NativeDebugProcess;
import com.android.tools.ndk.run.hybrid.StepIntoNativeBreakpointType;
import com.google.common.collect.Lists;
import com.intellij.debugger.engine.BasicStepMethodFilter;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Range;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionAdapter;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import com.sun.jdi.Location;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import javax.swing.Icon;

public class AndroidJavaDebugProcess extends JavaDebugProcess {
   private static final Logger LOG = Logger.getInstance(AndroidJavaDebugProcess.class);
   private static final String TID_EXPRESSION = "android.os.Process.myTid()";
   private final DebuggerSession myJavaSession;
   private final Project myProject;
   private final NativeDebugProcess myNativeDebugProcess;
   private final XSmartStepIntoHandler<?> mySmartStepIntoHandler = new AndroidJavaDebugProcess.SmartStepIntoHandlerImpl();
   private XSourcePosition myPauseSourcePosition;
   private Future<Integer> myCurrentTidFuture;

   public static JavaDebugProcess create(XDebugSession session, DebuggerSession javaSession, NativeDebugProcess nativeDebugProcess) {
      AndroidJavaDebugProcess res = new AndroidJavaDebugProcess(session, javaSession, nativeDebugProcess);
      javaSession.getProcess().setXDebugProcess(res);
      return res;
   }

   protected AndroidJavaDebugProcess(XDebugSession session, DebuggerSession javaSession, NativeDebugProcess nativeDebugProcess) {
      super(session, javaSession);
      this.myJavaSession = javaSession;
      this.myNativeDebugProcess = nativeDebugProcess;
      this.myProject = javaSession.getProject();
      session.addSessionListener(new XDebugSessionAdapter() {
    	  @Override
         public void sessionPaused() {
            AndroidJavaDebugProcess.this.updateSourcePosition();
         }

    	  @Override
         public void stackFrameChanged() {
            AndroidJavaDebugProcess.this.updateSourcePosition();
         }
      });
   }

   private Future<Integer> getCurrentThreadIdFuture(XSourcePosition sourcePosition) {
      final FutureResult<Integer> futureResult = new FutureResult<Integer>();
      XEvaluationCallback evaluationCallback = new XEvaluationCallback() {
         @Override
    	  public void evaluated(XValue result) {
            if(result instanceof JavaValue) {
               JavaValue jValue = (JavaValue)result;
               futureResult.set(Integer.valueOf(Integer.parseInt(jValue.getDescriptor().getValue().toString())));
            } else {
               futureResult.setException(new EvaluateException("Unexpected value type: " + result.toString()));
            }

         }

         @Override
         public void errorOccurred(String errorMessage) {
            futureResult.setException(new EvaluateException("Evaluation failed: " + errorMessage));
         }
      };
      this.getEvaluator().evaluate(TID_EXPRESSION, evaluationCallback, sourcePosition);
      return futureResult;
   }

   void updateSourcePosition() {
      if(this.myCurrentTidFuture != null) {
         this.myCurrentTidFuture.cancel(true);
         this.myCurrentTidFuture = null;
      }

      XStackFrame frame = this.getSession().getCurrentStackFrame();
      if(frame != null) {
         this.myPauseSourcePosition = frame.getSourcePosition();
         this.myCurrentTidFuture = this.getCurrentThreadIdFuture(this.myPauseSourcePosition);
      } else {
         this.myPauseSourcePosition = null;
      }

   }

   private void setupNativeBreakpoints(int tid) {
      final LinkedList<String> jniMethodNames = Lists.newLinkedList();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
    	  @Override
         public void run() {
            jniMethodNames.addAll(findNativeMethodsInCurrentSourcePosition());
         }
      });
      Iterator<String> i = jniMethodNames.iterator();

      while(i.hasNext()) {
         this.myNativeDebugProcess.registerStepIntoNativeBreakpoint(i.next(), tid);
      }

   }

   private List<PsiMethod> findMethodsBySourcePosition(XSourcePosition position) {
      MethodCollector methodCollector = new MethodCollector(this.myProject, position);
      return methodCollector.getMethods();
   }

   private List<String> findNativeMethodsInCurrentSourcePosition() {
      List<PsiMethod> methods = this.findMethodsBySourcePosition(this.myPauseSourcePosition);
      LinkedList<String> jniMethodNames = Lists.newLinkedList();
      Iterator<PsiMethod> iter = methods.iterator();

      while(iter.hasNext()) {
         PsiMethod method = (PsiMethod)iter.next();
         if(isNativeMethod(method)) {
            jniMethodNames.add(formatJniMethodPattern(method));
         }
      }

      return jniMethodNames;
   }

   private static boolean isNativeMethod(PsiMethod method) {
      return method.getModifierList().hasExplicitModifier("native");
   }

   private static String formatJniMethodPattern(PsiMethod method) {
      StringBuilder sb = new StringBuilder();
      sb.append("Java_");
      sb.append(".*");
      sb.append(JniNameMangler.jniEncodeUnderscore(method.getName(), false));
      sb.append(".*");
      return sb.toString();
   }

   private void initNativeSteppingInto() {
      if(this.myPauseSourcePosition != null && this.myCurrentTidFuture != null) {
         try {
            int e = ((Integer)this.myCurrentTidFuture.get()).intValue();
            this.setupNativeBreakpoints(e);
         } catch (Exception var2) {
            LOG.error(var2);
         }

      }
   }

   private void removeAllStepIntoNativeMethodBreakpoints() {
      this.myNativeDebugProcess.removeAllStepIntoNativeBreakpoints();
   }

   @Override
   public void startStepInto() {
      this.initNativeSteppingInto();
      super.startStepInto();
   }

   @Override
   public void startForceStepInto() {
      this.initNativeSteppingInto();
      super.startForceStepInto();
   }

   @Override
   public void startStepOver() {
      this.removeAllStepIntoNativeMethodBreakpoints();
      super.startStepOver();
   }

   @Override
   public void runToPosition(XSourcePosition position) {
      this.removeAllStepIntoNativeMethodBreakpoints();
      super.runToPosition(position);
   }

   @Override
   public void resume() {
      this.removeAllStepIntoNativeMethodBreakpoints();
      super.resume();
   }

   @Override
   public void stop() {
      super.stop();
      myNativeDebugProcess.getSession().stop();
   }

   @Override
   public XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
      return mySmartStepIntoHandler;
   }

   private class SmartStepIntoHandlerImpl extends XSmartStepIntoHandler<XSmartStepIntoVariantImpl> {
      private SmartStepIntoHandlerImpl() {
      }

      @Override
      public List<XSmartStepIntoVariantImpl> computeSmartStepVariants(XSourcePosition position) {
         MethodCollector methodCollector = new MethodCollector(AndroidJavaDebugProcess.this.myProject, position);
         List<PsiMethod> methods = methodCollector.getMethods();
         ArrayList<XSmartStepIntoVariantImpl> variants = Lists.newArrayListWithExpectedSize(methods.size());
         Range<Integer> lineRange = methodCollector.getLineRange();
         if(lineRange != null) {
            Iterator<PsiMethod> iter = methods.iterator();

            while(iter.hasNext()) {
               PsiMethod method = iter.next();
               variants.add(new XSmartStepIntoVariantImpl(method, lineRange));
            }
         }

         return variants;
      }

      @Override
      public void startStepInto(XSmartStepIntoVariantImpl paramVariant) {
    	 XSmartStepIntoVariantImpl variant = (AndroidJavaDebugProcess.XSmartStepIntoVariantImpl)paramVariant;
         PsiMethod method = variant.getMethod();
         if(!AndroidJavaDebugProcess.isNativeMethod(method)) {
            myJavaSession.stepInto(true, new BasicStepMethodFilter(method, variant.getLineRange()));
         } else {
            try {
               XBreakpoint<?> e = myNativeDebugProcess.registerStepIntoNativeBreakpoint(
            		   AndroidJavaDebugProcess.formatJniMethodPattern(method), 
            		   ((Integer)AndroidJavaDebugProcess.this.myCurrentTidFuture.get()).intValue());
               myJavaSession.stepInto(true, new AndroidJavaDebugProcess.NativeMethodFilter(e, variant.getLineRange()));
            } catch (Exception var4) {
               AndroidJavaDebugProcess.LOG.error(var4);
            }

         }
      }

      @Override
      public String getPopupTitle(XSourcePosition position) {
         return "Method to Step Into";
      }
   }

   private class XSmartStepIntoVariantImpl extends XSmartStepIntoVariant {
      private final PsiMethod myMethod;
      private final Range<Integer> myLineRange;

      public XSmartStepIntoVariantImpl(PsiMethod method, Range<Integer> lineRange) {
         this.myMethod = method;
         this.myLineRange = lineRange;
      }

      @Override
      public Icon getIcon() {
         return this.myMethod.getIcon(2);
      }

      @Override
      public String getText() {
         return this.myMethod.getText();
      }

      public Range<Integer> getLineRange() {
         return this.myLineRange;
      }

      public PsiMethod getMethod() {
         return this.myMethod;
      }
   }

   private static class NativeMethodFilter implements MethodFilter {
      private final XBreakpoint<?> myBp;
      private final Range<Integer> myLineRange;

      public NativeMethodFilter(XBreakpoint<?> bp, Range<Integer> lineRange) {
         this.myBp = bp;
         this.myLineRange = lineRange;
      }

      @Override
      public boolean locationMatches(DebugProcessImpl process, Location location) throws EvaluateException {
         return ((StepIntoNativeBreakpointType.Properties)this.myBp.getProperties()).isCalled();
      }

      @Override
      public Range<Integer> getCallingExpressionLines() {
         return this.myLineRange;
      }
   }
}
