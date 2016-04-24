package com.android.tools.ndk.editors;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications.Provider;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.cidr.lang.OCFileType;
import java.awt.Color;

@SuppressWarnings("rawtypes")
public class ExperimentalNdkSupportNotificationProvider extends Provider {
   private static final Key KEY = Key.create("android.ndk.editors.experimental");

   @Override
   public Key getKey() {
      return KEY;
   }

   @Override
   public ExperimentalNdkSupportNotificationProvider.InfoPanel createNotificationPanel(VirtualFile file, FileEditor fileEditor) {
      if(file.getFileType() != OCFileType.INSTANCE) {
         return null;
      } else {
         ExperimentalNdkSupportNotificationProvider.InfoPanel panel = new ExperimentalNdkSupportNotificationProvider.InfoPanel();
         panel.setText("NDK support is an experimental feature and all use cases are not yet supported.");
         return panel;
      }
   }

   public static class InfoPanel extends EditorNotificationPanel {
      /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public Color getBackground() {
         Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.READONLY_BACKGROUND_COLOR);
         return color == null?UIUtil.getPanelBackground():color;
      }
   }
}
