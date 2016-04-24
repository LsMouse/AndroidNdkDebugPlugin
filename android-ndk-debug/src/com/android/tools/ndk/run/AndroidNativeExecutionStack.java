package com.android.tools.ndk.run;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.Icon;

import com.intellij.execution.ExecutionException;
import com.intellij.icons.AllIcons.Debugger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess.DebuggerCommand;
import com.jetbrains.cidr.execution.debugger.CidrErrorStackFrame;
import com.jetbrains.cidr.execution.debugger.CidrStackFrame;
import com.jetbrains.cidr.execution.debugger.CidrSuspensionCause;
import com.jetbrains.cidr.execution.debugger.backend.DBCannotCollectFramesException;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;
import com.jetbrains.cidr.execution.debugger.backend.LLFrame;
import com.jetbrains.cidr.execution.debugger.backend.LLThread;

class AndroidNativeExecutionStack extends XExecutionStack {
   private final AndroidNativeDebugProcess myProcess;
   private final LLThread myThread;
   private List<XStackFrame> myFrames = null;
   private volatile XStackFrame myTopFrame = null;
   private volatile boolean myTopFrameReady = false;
   private boolean myErrorCollectingFrames = false;
   private final CidrSuspensionCause mySuspensionCause;

   public AndroidNativeExecutionStack(AndroidNativeDebugProcess process, DebuggerDriver driver, LLThread thread, boolean current, CidrSuspensionCause suspensionCause) throws ExecutionException {
      super(thread.getDisplayName(), current?Debugger.ThreadCurrent:Debugger.ThreadSuspended);
      this.myProcess = process;
      this.myThread = thread;
      this.mySuspensionCause = suspensionCause;
      if(current) {
         this.myFrames = this.getStackFrames(driver, true);
      }
   }

   @Override
   public XStackFrame getTopFrame() {
      assert this.myTopFrameReady;

      return this.myTopFrame;
   }

   private void addStackFrames(List<XStackFrame> frames, int from, XStackFrameContainer container) {
      if(from < frames.size()) {
         container.addStackFrames(frames.subList(from, frames.size()), false);
      }

   }

   private List<XStackFrame> getStackFrames(DebuggerDriver driver, boolean untilValidLineEntry) throws ExecutionException {
      ArrayList<XStackFrame> xFrames = new ArrayList<XStackFrame>();

      try {
         List<LLFrame> e = driver.getFrames(this.myThread.getId(), untilValidLineEntry);
         CidrStackFrame prev = null;

         CidrStackFrame frame;
         for(Iterator<LLFrame> i = ContainerUtil.reverse(e).iterator(); i.hasNext(); prev = frame) {
            LLFrame each = (LLFrame)i.next();
            frame = new CidrStackFrame(this.myProcess, this.myThread.getId(), each, prev, this.mySuspensionCause);
            xFrames.add(frame);
         }

         Collections.reverse(xFrames);
      } catch (DBCannotCollectFramesException var9) {
         xFrames.add(new CidrErrorStackFrame(var9.getMessage()));
         this.myErrorCollectingFrames = true;
      }

      if(!this.myTopFrameReady) {
         if(!xFrames.isEmpty()) {
            this.myTopFrame = (XStackFrame)xFrames.get(0);
         }

         this.myTopFrameReady = true;
      }

      return xFrames;
   }

   @Override
   public void computeStackFrames(final int from, final XStackFrameContainer container) {
      final int sentFrames = this.myFrames != null?this.myFrames.size():0;
      if(sentFrames > 0) {
         this.addStackFrames(this.myFrames, from, container);
      }

      if(this.myErrorCollectingFrames) {
         container.addStackFrames(Collections.<XStackFrame> emptyList(), true);
      } else {
         this.myProcess.postCommand(new DebuggerCommand() {
            @Override
        	 public void run(DebuggerDriver driver) throws ExecutionException {
               List<XStackFrame> newFrames = AndroidNativeExecutionStack.this.getStackFrames(driver, false);
               AndroidNativeExecutionStack.this.addStackFrames(newFrames, Math.max(from, sentFrames), container);
               container.addStackFrames(Collections.<XStackFrame> emptyList(), true);
            }
         });
      }
   }

   @Override
   public GutterIconRenderer getExecutionLineIconRenderer() {
      return this.mySuspensionCause == null?super.getExecutionLineIconRenderer():new GutterIconRenderer() {
    	  @Override
    	  public Icon getIcon() {
            return AndroidNativeExecutionStack.this.mySuspensionCause.icon;
         }

    	  @Override
         public String getTooltipText() {
            return AndroidNativeExecutionStack.this.mySuspensionCause.getDisplayString();
         }

    	  @Override
         public boolean equals(Object o) {
            return this == o?true:(o != null && this.getClass() == o.getClass()?Comparing.equal(AndroidNativeExecutionStack.this.mySuspensionCause, ((AndroidNativeExecutionStack)o).mySuspensionCause):false);
         }

    	  @Override
         public int hashCode() {
            return AndroidNativeExecutionStack.this.mySuspensionCause.hashCode();
         }
      };
   }

   @Override
   public String toString() {
      return this.myThread.toString();
   }
}
