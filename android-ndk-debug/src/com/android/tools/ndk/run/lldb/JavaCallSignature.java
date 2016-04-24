package com.android.tools.ndk.run.lldb;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaCallSignature {
   private static final Pattern JAVA_METHOD_SIGNATURE = Pattern.compile("^(.+?)\\s+([^\\((]+)\\.([^\\((]+)\\((.*)\\)$", 2);
   private final String myReturnType;
   private final String myClassName;
   private final String myMethodName;
   private final List<String> myParameterList;

   private JavaCallSignature(String returnType, String className, String methodName, List<String> parameterList) {
      this.myReturnType = returnType;
      this.myClassName = className;
      this.myMethodName = methodName;
      this.myParameterList = parameterList;
   }

   public static JavaCallSignature Parse(String callSignature) {
      Matcher matcher = JAVA_METHOD_SIGNATURE.matcher(callSignature);
      if(matcher.matches() && matcher.groupCount() == 4) {
         List<String> params = StringUtil.split(matcher.group(4), ",");
         ArrayList<String> trimmedParams = Lists.newArrayListWithExpectedSize(params.size());
         Iterator<String> iter = params.iterator();

         while(iter.hasNext()) {
            trimmedParams.add(iter.next().trim());
         }

         return new JavaCallSignature(matcher.group(1), matcher.group(2), matcher.group(3), trimmedParams);
      } else {
         return null;
      }
   }

   public String getReturnType() {
      return this.myReturnType;
   }

   public String getClassName() {
      return this.myClassName;
   }

   public String getMethodName() {
      return this.myMethodName;
   }

   public List<String> getParameterList() {
      return this.myParameterList;
   }
}
