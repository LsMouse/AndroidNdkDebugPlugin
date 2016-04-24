package com.android.tools.ndk;

import com.android.ddmlib.IDevice;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.ndk.GradleWorkspace;
import com.google.common.collect.ImmutableMap.Builder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.cidr.ArchitectureType;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NdkHelper {
   private static final Map<Abi,String> ourToolchainPrefixes;

   public static Pair<File,String> getNdkGdb(File ndkRoot, Abi abi) {
      if(!ndkRoot.isDirectory()) {
         return Pair.create(null, String.format("%1$s does not point to a valid folder", new Object[]{ndkRoot.getPath()}));
      } else {
         final String toolChainPrefix = (String)ourToolchainPrefixes.get(abi);
         if(toolChainPrefix == null) {
            return Pair.create(null, "Unable to determine toolchain prefix for ABI: " + abi.getDisplayName());
         } else {
            File toolchains = new File(ndkRoot, "toolchains");
            File[] toolchainsForAbi = toolchains.listFiles(new FilenameFilter() {
               public boolean accept(File dir, String name) {
                  return name.startsWith(toolChainPrefix);
               }
            });
            if(toolchainsForAbi != null && toolchainsForAbi.length != 0) {
               File bestToolchain = null;
               FullRevision best = null;
               File[] prebuilt = toolchainsForAbi;
               int prebuilts = toolchainsForAbi.length;

               for(int gdbPrefix = 0; gdbPrefix < prebuilts; ++gdbPrefix) {
                  File gdbSuffix = prebuilt[gdbPrefix];
                  String gdb = gdbSuffix.getName();
                  String version = gdb.substring(toolChainPrefix.length() + 1);

                  try {
                     FullRevision e = FullRevision.parseRevision(version);
                     if(best == null || e.compareTo(best) > 0) {
                        best = e;
                        bestToolchain = gdbSuffix;
                     }
                  } catch (NumberFormatException var14) {
                     if(best == null) {
                        best = FullRevision.NOT_SPECIFIED;
                        bestToolchain = gdbSuffix;
                     }
                  }
               }

               File var15 = new File(bestToolchain, "prebuilt");
               File[] var16 = var15.listFiles();
               if(var16 != null && var16.length != 0) {
                  String prefix = getToolchainBinaryPrefix(abi, toolChainPrefix);
                  String suffix = SystemInfo.isWindows?".exe":"";
                  File gdbfile = new File(var16[0], FileUtil.join(new String[]{"bin", prefix + "-gdb" + suffix}));
                  return ((!gdbfile.exists())?Pair.create((File)null, "No gdb at " + gdbfile.getAbsolutePath()):Pair.create(gdbfile, (String)null));
               } else {
                  return Pair.create(null, "No platforms inside prebuilts folder: " + var15.getPath());
               }
            } else {
               return Pair.create(null, "No toolchains found in the NDK toolchains folder for ABI with prefix: " + toolChainPrefix);
            }
         }
      }
   }

   private static String getToolchainBinaryPrefix(Abi abi, String toolChainPrefix) {
      String gdbPrefix;
      if(abi == Abi.X86) {
         gdbPrefix = "i686-linux-android";
      } else if(abi == Abi.X86_64) {
         gdbPrefix = "x86_64-linux-android";
      } else {
         gdbPrefix = toolChainPrefix;
      }

      return gdbPrefix;
   }

   public static Abi getAbi(ArchitectureType architectureType) {
      switch(architectureType) {
      case I386:
         return Abi.X86;
      case X86_64:
         return Abi.X86_64;
      case ARM:
         return Abi.ARMEABI_V7A;
      case PPC:
      case UNKNOWN:
      default:
         throw new IllegalArgumentException("Unknown architecture: " + architectureType.name());
      }
   }

   public static String getArchitectureId(Abi abi) {
      switch(abi) {
      case ARMEABI:
      case ARMEABI_V7A:
      case ARM64_V8A:
         return ArchitectureType.ARM.name().toLowerCase();
      case X86_64:
         return ArchitectureType.X86_64.name().toLowerCase();
      case X86:
         return ArchitectureType.I386.name().toLowerCase();
      default:
         return ArchitectureType.UNKNOWN.name().toLowerCase();
      }
   }

   public static Abi getAbi(IDevice targetDevice) {
      List<String> abis = targetDevice.getAbis();
      Iterator<String> iter = abis.iterator();

      while(iter.hasNext()){
    	  Abi abi = Abi.getEnum(iter.next());
    	  if(abi != null)
    		  return abi;
      }
      return Abi.ARMEABI_V7A;
   }

   private static String getHostPlatformString() {
      String platformString;
      if(SystemInfo.isLinux) {
         platformString = "linux";
      } else if(SystemInfo.isWindows) {
         platformString = "windows";
      } else {
         if(!SystemInfo.isMac) {
            return "UNKNOWN";
         }

         platformString = "darwin";
      }

      platformString = platformString + "-";
      if(SystemInfo.is64Bit) {
         platformString = platformString + "x86_64";
      } else {
         if(!SystemInfo.is32Bit) {
            return "UNKNOWN";
         }

         platformString = platformString + "x86";
      }

      return platformString;
   }

   public static boolean isNdkProject(Project project) {
      return !GradleWorkspace.getInstance(project).getConfigurations().isEmpty();
   }

   public static File getLibStdCxxPrintersPath(File ndkRoot, String gccVersion) {
      File prebuilt = new File(ndkRoot, "prebuilt");
      File hostPlatform = new File(prebuilt, getHostPlatformString());
      File share = new File(hostPlatform, "share");
      File prettyPrinters = new File(share, "pretty-printers");
      File libStdCxx = new File(prettyPrinters, "libstdcxx");
      File fullPath = new File(libStdCxx, "gcc-" + gccVersion);
      return fullPath;
   }

   static {
      ourToolchainPrefixes = (new Builder<Abi, String>())
    		  .put(Abi.ARMEABI, "arm-linux-androideabi")
    		  .put(Abi.ARM64_V8A, "aarch64-linux-android")
    		  .put(Abi.ARMEABI_V7A, "arm-linux-androideabi")
    		  .put(Abi.X86, "x86").put(Abi.X86_64, "x86_64")
    		  .put(Abi.MIPS, "mipsel-linux-android")
    		  .put(Abi.MIPS64, "mips64el-linux-android").build();
   }
}
