package com.android.tools.ndk.run;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task.Backgroundable;
import com.intellij.openapi.project.Project;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class AttachProgressReporter {
   private static final Logger LOG = Logger.getInstance(AttachProgressReporter.class);
   private final BlockingQueue<StepContext> myStepQueue = new LinkedBlockingDeque<StepContext>();
   private boolean myFinished = false;

   private synchronized void putStep(AttachProgressReporter.StepContext stepContext) {
      if(this.myFinished) {
         throw new IllegalStateException("Already in finished state");
      } else {
         try {
            this.myStepQueue.put(stepContext);
         } catch (InterruptedException var3) {
            LOG.error(var3);
         }

         this.myFinished = stepContext.isFinished();
      }
   }

   private void runProgressTask(final Project project) {
      ProgressManager.getInstance().run(new Backgroundable(project, "Attaching native debugger", false) {
         @Override
    	  public void run(ProgressIndicator indicator) {
            while(true) {
               try {
                  AttachProgressReporter.StepContext e = (AttachProgressReporter.StepContext)AttachProgressReporter.this.myStepQueue.take();
                  if(!e.isFinished()) {
                     indicator.setText(e.getName());
                     continue;
                  }
               } catch (InterruptedException var3) {
                  AttachProgressReporter.LOG.error(var3);
               }

               return;
            }
         }
      });
   }

   public AttachProgressReporter(final Project project) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
         @Override
    	  public void run() {
            AttachProgressReporter.this.runProgressTask(project);
         }
      });
   }

   public void step(String stepName) {
      this.putStep(new AttachProgressReporter.StepContext(stepName, false));
   }

   public void finish() {
      this.putStep(new AttachProgressReporter.StepContext((String)null, true));
   }

   private static class StepContext {
      private final String myName;
      private final boolean myFinished;

      public StepContext(String name, boolean finished) {
         this.myName = name;
         this.myFinished = finished;
      }

      public String getName() {
         return this.myName;
      }

      public boolean isFinished() {
         return this.myFinished;
      }
   }
}
