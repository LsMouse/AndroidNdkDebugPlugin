package com.android.tools.ndk.run.hybrid;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;

public interface NativeDebugProcess {
   XDebugSession getSession();

   XBreakpoint<?> registerStepIntoNativeBreakpoint(String var1, int var2);

   void removeAllStepIntoNativeBreakpoints();
}
