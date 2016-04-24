package com.android.tools.ndk.run.crash;

import com.android.tools.idea.diagnostics.error.ErrorReportCustomizer;
import com.android.tools.ndk.run.crash.NativeClientCrashReportingTask;
import com.google.common.io.Files;
import com.intellij.diagnostic.LogEventException;
import com.intellij.diagnostic.LogMessage;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.progress.Task.Backgroundable;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class NativeClientCrashException extends Exception {
   /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final char[] hexChars = "0123456789abcdef".toCharArray();

   public static LogEventException create(String message, File minidump) throws IOException {
      return new LogEventException(new NativeClientCrashException.LoggingEvent(message, minidump));
   }

   private static Attachment createMinidumpAttachment(File minidump) throws IOException {
      String filename = minidump.getAbsolutePath();
      byte[] contents = Files.toByteArray(minidump);
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < contents.length; i += 16) {
         int j;
         for(j = 0; j < 16; ++j) {
            if(i + j < contents.length) {
               sb.append(hexChars[contents[i + j] >> 4 & 15]);
               sb.append(hexChars[contents[i + j] & 15]);
            } else {
               sb.append("  ");
            }

            sb.append(" ");
            if(j == 7) {
               sb.append("  ");
            }
         }

         sb.append("  |");

         for(j = 0; j < 16 && i + j < contents.length; ++j) {
            Byte b = Byte.valueOf(contents[i + j]);
            if(b.byteValue() > 32 && b.byteValue() < 127) {
               sb.appendCodePoint(b.byteValue());
            } else {
               sb.append('.');
            }
         }

         sb.append("|\n");
      }

      return new Attachment(filename, contents, sb.toString());
   }

   @Override
   public void printStackTrace(PrintWriter s) {
      s.append("\nTechnical details are in the attachment.");
   }

   private static class CrashData extends LogMessage implements ErrorReportCustomizer {
      private final Attachment myAttachment;

      public CrashData(IdeaLoggingEvent aEvent, Attachment attachment) {
         super(aEvent);
         this.myAttachment = attachment;
      }

      @Override
      public List<Attachment> getAttachments() {
         return Arrays.asList(new Attachment[]{this.myAttachment});
      }

      @SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
      public Backgroundable makeReportingTask(Project project, String title, boolean canBeCancelled, ErrorBean bean, Consumer callback, Consumer errorCallback) {
         return new NativeClientCrashReportingTask(project, title, canBeCancelled, bean, callback, errorCallback);
      }
   }

   private static class LoggingEvent extends IdeaLoggingEvent {
      private final Attachment myAttachment;

      public LoggingEvent(String message, File minidump) throws IOException {
         super(message, new NativeClientCrashException());
         this.myAttachment = NativeClientCrashException.createMinidumpAttachment(minidump);
      }

      @Override
      public Object getData() {
         return new NativeClientCrashException.CrashData(this, this.myAttachment);
      }
   }
}
