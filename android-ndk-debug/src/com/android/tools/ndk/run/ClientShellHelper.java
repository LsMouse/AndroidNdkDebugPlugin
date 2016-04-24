package com.android.tools.ndk.run;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import java.io.IOException;

public class ClientShellHelper {
   private static final String RUNAS = "run-as";
   private static final String USER_ARG = "--user";
   private final String myPackageName;
   private final ClientData myClientData;

   public ClientShellHelper(Client client, String packageName) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
      this.myClientData = client.getClientData();
      this.myPackageName = packageName;
      if(this.isRestrictedUser()) {
         CollectingOutputReceiver receiver = new CollectingOutputReceiver();
         client.getDevice().executeShellCommand(RUNAS, receiver);
         if(!receiver.getOutput().contains(USER_ARG)) {
            throw new IllegalStateException("Native debugging under restricted user is not supported yet.");
         }
      }

   }

   public String getPackageFolder() {
      return this.isRestrictedUser()?String.format("/data/user/%d/%s", new Object[]{Integer.valueOf(this.myClientData.getUserId()), this.myPackageName}):"/data/data/" + this.myPackageName;
   }

   public String runAs(String command) {
      String prefix = String.format("%s %s", new Object[]{"run-as", this.myPackageName});
      if(this.isRestrictedUser()) {
         prefix = String.format("%s %s %d", new Object[]{prefix, "--user", Integer.valueOf(this.myClientData.getUserId())});
      }

      return prefix + " " + command;
   }

   private boolean isRestrictedUser() {
      return this.myClientData.isValidUserId() && this.myClientData.getUserId() > 0;
   }
}
