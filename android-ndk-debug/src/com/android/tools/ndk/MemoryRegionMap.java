package com.android.tools.ndk;

import com.android.utils.Pair;
import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemoryRegionMap {
   private static final Pattern MAPS_RECORD_PATTERN = Pattern.compile("^(\\p{XDigit}+)-(\\p{XDigit}+)\\s+(r|-)(w|-)(x|-)(p|s)\\s+(\\p{XDigit}+)\\s+(\\p{XDigit}+):(\\p{XDigit}+)\\s+(\\d+)\\s+(.*)$", 2);
   private final NavigableMap<Long,MemoryRegionMap.Region> myAddress2Region = new TreeMap<Long,MemoryRegionMap.Region>();
   private final List<Pattern> myFileNamePatterns;

   public MemoryRegionMap(List<String> fileNameFilters) {
      this.myFileNamePatterns = new ArrayList<Pattern>(fileNameFilters.size());
      Iterator<String> iter = fileNameFilters.iterator();

      while(iter.hasNext()) {
         String fileNameFilter = iter.next();
         this.myFileNamePatterns.add(Pattern.compile(fileNameFilter));
      }
   }

   public void addMapEntries(String[] maps) {
      for(int i = 0; i < maps.length; ++i) {
         String mapRegion = maps[i];
         this.processSingleRegion(mapRegion);
      }
   }

   public void clear() {
      this.myAddress2Region.clear();
   }

   public MemoryRegionMap.Region getRegionByAddress(long address) {
      Entry<Long,MemoryRegionMap.Region> entry = this.myAddress2Region.floorEntry(Long.valueOf(address));
      if(entry == null) {
         return null;
      } else {
         MemoryRegionMap.Region region = entry.getValue();
         return region.getAddressMapRange().upperEndpoint() <= address?null:region;
      }
   }

   private void processSingleRegion(String mapRegion) {
      Matcher matcher = MAPS_RECORD_PATTERN.matcher(mapRegion);
      if(matcher.matches() && matcher.groupCount() == 11) {
         Range<Long> addressRange = Range.closedOpen(Long.parseLong(matcher.group(1), 16), Long.parseLong(matcher.group(2), 16));
         String fileName = matcher.group(11);
         boolean matchFound = this.myFileNamePatterns.isEmpty();
         Iterator<Pattern> region = this.myFileNamePatterns.iterator();

         while(region.hasNext()) {
            if(region.next().matcher(fileName).matches()) {
               matchFound = true;
               break;
            }
         }

         if(matchFound) {
            MemoryRegionMap.Region region1 = new MemoryRegionMap.Region();
            region1.setAddressMapRange(addressRange);
            region1.setReadable(matcher.group(3).equals("r"));
            region1.setWritable(matcher.group(4).equals("w"));
            region1.setExecutable(matcher.group(5).equals("x"));
            region1.setShared(matcher.group(6).equals("s"));
            region1.setFileOffset(Long.parseLong(matcher.group(7), 16));
            region1.setDevice(Pair.of(Long.parseLong(matcher.group(8), 16), Long.parseLong(matcher.group(9), 16)));
            region1.setInode(Long.parseLong(matcher.group(10)));
            region1.setFileName(fileName);
            this.myAddress2Region.put(addressRange.lowerEndpoint(), region1);
         }
      }
   }

   public static class Region {
      private String myFileName;
      private Range<Long> myAddressMapRange;
      private boolean myReadable = false;
      private boolean myWritable = false;
      private boolean myExecutable = false;
      private boolean myShared = false;
      private long myFileOffset = 0L;
      private Pair<Long,Long> myDevice;
      private long myInode = 0L;

      public String getFileName() {
         return this.myFileName;
      }

      public void setFileName(String fileName) {
         this.myFileName = fileName;
      }

      public Range<Long> getAddressMapRange() {
         return this.myAddressMapRange;
      }

      public void setAddressMapRange(Range<Long> addressMapRange) {
         this.myAddressMapRange = addressMapRange;
      }

      public boolean isReadable() {
         return this.myReadable;
      }

      public void setReadable(boolean readable) {
         this.myReadable = readable;
      }

      public boolean isWritable() {
         return this.myWritable;
      }

      public void setWritable(boolean writable) {
         this.myWritable = writable;
      }

      public boolean isExecutable() {
         return this.myExecutable;
      }

      public void setExecutable(boolean executable) {
         this.myExecutable = executable;
      }

      public boolean isShared() {
         return this.myShared;
      }

      public void setShared(boolean shared) {
         this.myShared = shared;
      }

      public long getFileOffset() {
         return this.myFileOffset;
      }

      public void setFileOffset(long fileOffset) {
         this.myFileOffset = fileOffset;
      }

      public Pair<Long,Long> getDevice() {
         return this.myDevice;
      }

      public void setDevice(Pair<Long,Long> device) {
         this.myDevice = device;
      }

      public long getInode() {
         return this.myInode;
      }

      public void setInode(long inode) {
         this.myInode = inode;
      }
   }
}
