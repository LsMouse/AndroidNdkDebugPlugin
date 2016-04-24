package com.android.tools.ndk.run;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidRunningState;
import com.android.tools.ndk.run.AttachProgressReporter;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class DebuggerContext {
   private AttachProgressReporter myAttachProgressReporter;

   public abstract DebuggerDriverConfiguration getDebuggerDriverConfiguration(AndroidRunningState var1, IDevice var2);

   public abstract void startServer(AndroidRunningState var1, Client var2, AtomicBoolean var3) 
		   throws DebuggerContext.StartServerException;

   public AttachProgressReporter getAttachProgressReporter() {
      return this.myAttachProgressReporter;
   }

   public void setAttachProgressReporter(AttachProgressReporter attachProgressReporter) {
      this.myAttachProgressReporter = attachProgressReporter;
   }

   public static final class StartServerException extends Exception {
      private static final long serialVersionUID = 1L;

      public StartServerException(Throwable cause) {
         super(cause);
      }
   }
}
