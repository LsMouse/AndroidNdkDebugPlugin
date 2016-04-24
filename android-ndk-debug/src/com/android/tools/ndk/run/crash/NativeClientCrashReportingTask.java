package com.android.tools.ndk.run.crash;

import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task.Backgroundable;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;

class NativeClientCrashReportingTask extends Backgroundable {
   private final ErrorBean myBean;
   private final Consumer<String> myCallback;
   private final Consumer<String> myErrorCallback;

   public NativeClientCrashReportingTask(Project project, String title, boolean canBeCancelled, 
		   ErrorBean bean, Consumer<String> callback, Consumer<String> errorCallback) {
      super(project, title, canBeCancelled);
      this.myBean = bean;
      this.myCallback = callback;
      this.myErrorCallback = errorCallback;
   }

   @Override
   public void run(ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
   }

	public ErrorBean getMyBean() {
		return myBean;
	}

	public Consumer<String> getMyCallback() {
		return myCallback;
	}

	public Consumer<String> getMyErrorCallback() {
		return myErrorCallback;
	}
}
