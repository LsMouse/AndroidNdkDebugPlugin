package com.android.tools.ndk.run.hybrid;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess.DebuggerCommand;
import com.jetbrains.cidr.execution.debugger.backend.DBCannotSetBreakpointException;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver.SymbolicBreakpoint;
import com.jetbrains.cidr.execution.debugger.backend.LLCodepoint;
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrCodePointHandlerBase;

public class StepIntoNativeBreakpointHandler extends CidrCodePointHandlerBase<XBreakpoint<?>> {
   private static final Logger LOG = Logger.getInstance(StepIntoNativeBreakpointHandler.class);

   @SuppressWarnings({ "rawtypes", "unchecked" })
   public StepIntoNativeBreakpointHandler(CidrDebugProcess process, Class type) {
      super(process, type);
   }

   @Override
   public void registerBreakpoint(final XBreakpoint<?> breakpoint) {
      final int threadId = this.myProcess.getCurrentThreadId();
      final int frameNumber = this.myProcess.getCurrentFrameNumber();
      final CountDownLatch latch = new CountDownLatch(1);
      this.myProcess.postCommand(new DebuggerCommand() {
    	  @Override
         public void run(DebuggerDriver driver) throws ExecutionException {
            StepIntoNativeBreakpointHandler.this.doRegisterBreakpoint(driver, breakpoint, threadId, frameNumber);
            latch.countDown();
         }
      });

      try {
         latch.await();
      } catch (InterruptedException var6) {
         LOG.error(var6);
      }

   }

   @Override
   protected Collection<LLCodepoint> doAddCodepoints(DebuggerDriver driver, XBreakpoint<?> breakpoint, int threadId, int frameNumber) throws ExecutionException {
      String condition = this.convertCondition(breakpoint);
      StepIntoNativeBreakpointType.Properties properties = (StepIntoNativeBreakpointType.Properties)breakpoint.getProperties();
      if(properties != null && properties.getSymbolPattern() != null) {
         try {
            SymbolicBreakpoint e = new SymbolicBreakpoint();
            e.setPattern(properties.getSymbolPattern());
            e.setRegexpPattern(true);
            e.setCondition(condition);
            e.setThreadId(properties.getTid());
            return Arrays.asList(new LLCodepoint[]{driver.addSymbolicBreakpoint(e)});
         } catch (DBCannotSetBreakpointException var8) {
            return Collections.emptyList();
         }
      } else {
         return Collections.emptyList();
      }
   }
}
