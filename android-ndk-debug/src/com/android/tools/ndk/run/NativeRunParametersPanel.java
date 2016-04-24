package com.android.tools.ndk.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.jetbrains.android.sdk.AndroidSdkUtils;

public class NativeRunParametersPanel implements ActionListener {
   private Project myProject;
   private JPanel myPanel;
   private JBLabel mySymDirsLabel;
   private JPanel mySymDirsPanel;
   private TextFieldWithBrowseButton myWorkingDirField;
   private JTextField myLoggingTargetChannelsField;
   private JCheckBox myHybridDebugCheckBox;
   private JBList mySymDirsList;
   private CollectionListModel<String> mySymDirsModel;
   private ArrayList<Client> myClients;
   private static Client mySelClient = null;
   
   //add by lichao
   private JPanel myClientsPanel;
   private CollectionListModel<String> myClientsModel;
   private JBList myClientsList;

   public NativeRunParametersPanel(Project project) { 
	  myClients = new ArrayList<Client>();
      this.myProject = project;
      String[] strs = new String[0];
      this.setupUI();
      this.mySymDirsModel = new CollectionListModel<String>(strs);
      this.mySymDirsList = new JBList(this.mySymDirsModel);
      this.mySymDirsLabel.setLabelFor(this.mySymDirsList);
      ToolbarDecorator decorator = ToolbarDecorator.createDecorator(this.mySymDirsList).setAddAction(new AnActionButtonRunnable() {
         @Override
    	  public void run(AnActionButton button) {
            String path = NativeRunParametersPanel.this.chooseDirectory();
            if(path != null) {
               NativeRunParametersPanel.this.mySymDirsModel.add(path);
               NativeRunParametersPanel.this.mySymDirsList.setSelectedValue(path, true);
            }
         }
      });
      this.mySymDirsPanel.add(decorator.createPanel());
      this.myWorkingDirField.addBrowseFolderListener((String)null, (String)null, this.myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor());
 
      //add by lichao
      this.myClientsModel = new CollectionListModel<String>(this.getClients());
      this.myClientsList = new JBList(this.myClientsModel);
      this.myClientsList.addListSelectionListener(new ListSelectionListener(){
		@Override
		public void valueChanged(ListSelectionEvent arg0) {
			int index = myClientsList.getSelectedIndex();
			if(index < myClients.size()){
				mySelClient = myClients.get(index);
			}
		}
      });
      this.myClientsPanel.add(this.myClientsList);  
   }

   private String chooseDirectory() {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      VirtualFile file = FileChooser.chooseFile(descriptor, this.mySymDirsList, this.myProject, this.myProject.getBaseDir());
      return file != null?FileUtil.toSystemDependentName(file.getPath()):null;
   }

   public JComponent getComponent() {
      return this.myPanel;
   }

   @Override
   public void actionPerformed(ActionEvent e) {
   }

   public boolean isHybridDebug() {
      return this.myHybridDebugCheckBox.isSelected();
   }

   public void setHybridDebug(boolean hybridDebug) {
      this.myHybridDebugCheckBox.setSelected(hybridDebug);
   }

   public List<String> getSymbolDirs() {
      return this.mySymDirsModel.getItems();
   }

   @SuppressWarnings("unchecked")
   public void setSymbolDirs(List<String> symDirs) {
      this.mySymDirsModel = new CollectionListModel<String>(symDirs);
      this.mySymDirsList.setModel(this.mySymDirsModel);
   }

   public String getWorkingDir() {
      return this.myWorkingDirField.getText();
   }

   public void setWorkingDir(String workingDir) {
      this.myWorkingDirField.setText(workingDir);
   }

   public String getTargetLoggingChannels() {
      String logChannels = this.myLoggingTargetChannelsField.getText().trim();
      return logChannels.replaceAll("[^a-z\\-\\s:]", "");
   }

   public void setTargetLoggingChannels(String targetLoggingChannels) {
      this.myLoggingTargetChannelsField.setText(targetLoggingChannels);
   }

   // $FF: synthetic method
   private void setupUI() {
      myPanel = new JPanel();
      myPanel.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1, false, false));
      myPanel.add(new Spacer(), new GridConstraints(1, 1, 4, 1, 0, 2, 1, 6, (Dimension)null, (Dimension)null, (Dimension)null));
      
      mySymDirsLabel = new JBLabel();
      mySymDirsLabel.setText("Symbol directories:");
      myPanel.add(mySymDirsLabel, new GridConstraints(1, 0, 1, 1, 9, 0, 0, 0, (Dimension)null, (Dimension)null, (Dimension)null));
      
      mySymDirsPanel = new JPanel();
      mySymDirsPanel.setLayout(new BorderLayout(0, 0));
      myPanel.add(mySymDirsPanel, new GridConstraints(2, 0, 1, 1, 0, 3, 3, 2, (Dimension)null, (Dimension)null, (Dimension)null));
      
      JPanel workdirPanel = new JPanel();
      workdirPanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1, false, false));
      myPanel.add(workdirPanel, new GridConstraints(3, 0, 1, 1, 1, 1, 3, 3, (Dimension)null, (Dimension)null, (Dimension)null)); 
      JLabel workdirLabel = new JLabel();
      workdirLabel.setText("Host working directory:");
      workdirPanel.add(workdirLabel, new GridConstraints(0, 0, 1, 1, 8, 0, 0, 0, (Dimension)null, (Dimension)null, (Dimension)null));
      workdirPanel.add(new Spacer(), new GridConstraints(1, 0, 1, 3, 0, 2, 1, 6, (Dimension)null, (Dimension)null, (Dimension)null));
      myWorkingDirField = new TextFieldWithBrowseButton();
      workdirPanel.add(myWorkingDirField, new GridConstraints(0, 1, 1, 1, 0, 1, 6, 0, (Dimension)null, (Dimension)null, (Dimension)null));
      
      JPanel logPanel = new JPanel();
      logPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1, false, false));
      myPanel.add(logPanel, new GridConstraints(4, 0, 1, 1, 0, 3, 3, 7, (Dimension)null, (Dimension)null, (Dimension)null));
      logPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Logging", 0, 0, (Font)null, (Color)null));
      JBLabel logLabel = new JBLabel();
      logLabel.setText("Target channels:");
      logPanel.add(logLabel, new GridConstraints(0, 0, 1, 1, 0, 0, 0, 0, (Dimension)null, (Dimension)null, (Dimension)null));
      logPanel.add(new Spacer(), new GridConstraints(1, 0, 1, 1, 0, 2, 1, 6, (Dimension)null, (Dimension)null, (Dimension)null));
      myLoggingTargetChannelsField = new JTextField();
      logPanel.add(myLoggingTargetChannelsField, new GridConstraints(0, 1, 1, 1, 8, 1, 6, 0, (Dimension)null, new Dimension(150, -1), (Dimension)null));
      
      myHybridDebugCheckBox = new JCheckBox();
      myHybridDebugCheckBox.setSelected(false);
      myHybridDebugCheckBox.setText("Hybrid debug mode (Some Java stacks might not unwind)");
      myPanel.add(myHybridDebugCheckBox, new GridConstraints(0, 0, 1, 1, 8, 0, 3, 0, (Dimension)null, (Dimension)null, (Dimension)null));
      
      /*M*///add packagenames to select 
      myClientsPanel = new JPanel();
      myClientsPanel.setLayout(new BorderLayout(0, 0));
      myPanel.add(myClientsPanel, new GridConstraints(5, 0, 1, 1, 0, 3, 3, 2, (Dimension)null, (Dimension)null, (Dimension)null));

   }

   // $FF: synthetic method
   public JComponent getRootComponent() {
      return this.myPanel;
   }
   
   /*M*/  //return packackge process selected by user
   public static Client getSelectedClient(){
	   return mySelClient;
   }
   
   /*M*/  //used to enumerate package processes
   public String[] getClients(){
	   myClients.clear();
	   mySelClient = null;
	   AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(myProject);
	   IDevice[] devices = debugBridge.getDevices();
	   for (IDevice device : devices) {
	      for (Client client : device.getClients()) {
	        final String clientDescription = client.getClientData().getClientDescription();
	        if (clientDescription != null) {
	        	myClients.add(client);
	        }
	      }
	   }
	   String[] result = new String[myClients.size()];
	   for(int i=0;i<myClients.size();i++){
		   result[i] = myClients.get(i).getDevice().getName() + " " + myClients.get(i).getClientData().getClientDescription();
	   }
	   return result;
   }
}
