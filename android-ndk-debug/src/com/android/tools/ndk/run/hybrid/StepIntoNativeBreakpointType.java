package com.android.tools.ndk.run.hybrid;

import com.intellij.icons.AllIcons.Debugger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Icon;
import javax.swing.JComponent;

public class StepIntoNativeBreakpointType extends XBreakpointType<XBreakpoint<StepIntoNativeBreakpointType.Properties>,StepIntoNativeBreakpointType.Properties> {
   public static final StepIntoNativeBreakpointType INSTANCE = new StepIntoNativeBreakpointType();

   public StepIntoNativeBreakpointType() {
      this("StepIntoNativeBreakpointType", "StepInto Native Breakpoints");
   }

   protected StepIntoNativeBreakpointType(String id, String title) {
      super(id, title);
   }

   @Override
   public String getDisplayText(XBreakpoint<StepIntoNativeBreakpointType.Properties> breakpoint) {
      StepIntoNativeBreakpointType.Properties properties = (StepIntoNativeBreakpointType.Properties)breakpoint.getProperties();
      return properties == null?"Invalid breakpoint":(StringUtil.isEmpty(properties.getSymbolPattern())?"<Empty>":properties.getSymbolPattern());
   }

   @Override
   public Icon getEnabledIcon() {
      return Debugger.Db_method_breakpoint;
   }

   @Override
   public Icon getDisabledIcon() {
      return Debugger.Db_disabled_method_breakpoint;
   }

   @Override
   public Icon getInactiveDependentIcon() {
      return Debugger.Db_dep_method_breakpoint;
   }

   @Override
   public boolean isAddBreakpointButtonVisible() {
      return false;
   }

   @Override
   public StepIntoNativeBreakpointType.Properties createProperties() {
      return new StepIntoNativeBreakpointType.Properties();
   }

   @SuppressWarnings({ "rawtypes", "unchecked" })
   @Override
   public XBreakpoint addBreakpoint(final Project project, JComponent parentComponent) {
      return (XBreakpoint)ApplicationManager.getApplication().runWriteAction(new Computable() {
         public XBreakpoint compute() {
            return XDebuggerManager.getInstance(project).getBreakpointManager().addBreakpoint(StepIntoNativeBreakpointType.this, 
            		new StepIntoNativeBreakpointType.Properties());
         }
      });
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @Override
   public XBreakpointCustomPropertiesPanel createCustomTopPropertiesPanel(Project project) {
      return null;
   }

   @Override
   public XDebuggerEditorsProvider getEditorsProvider(XBreakpoint<StepIntoNativeBreakpointType.Properties> breakpoint, Project project) {
      return null;
   }

   public static class Properties extends XBreakpointProperties<Properties> {
      private String mySymbolPattern;
      private int myTid = -1;
      private AtomicBoolean myIsCalled = new AtomicBoolean(false);

      public Properties() {
      }

      public Properties(String symbolPattern, int tid) {
         this.mySymbolPattern = symbolPattern;
         this.myTid = tid;
      }

      public String getSymbolPattern() {
         return this.mySymbolPattern;
      }

      public int getTid() {
         return this.myTid;
      }

      @Override
      public StepIntoNativeBreakpointType.Properties getState() {
         return this;
      }

      @Override
      public void loadState(StepIntoNativeBreakpointType.Properties state) {
         this.mySymbolPattern = state.mySymbolPattern;
         this.myTid = state.myTid;
      }

      public boolean isCalled() {
         return this.myIsCalled.get();
      }

      public void setCalled(boolean isCalled) {
         this.myIsCalled.set(isCalled);
      }
   }
}
