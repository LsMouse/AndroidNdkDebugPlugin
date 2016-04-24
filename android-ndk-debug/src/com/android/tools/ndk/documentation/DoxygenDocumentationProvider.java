package com.android.tools.ndk.documentation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableMap.Builder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.cidr.lang.documentation.CidrDocumentationProvider;
import com.jetbrains.cidr.lang.parser.OCTokenTypes;
import com.jetbrains.cidr.lang.psi.OCDeclarator;
import com.jetbrains.cidr.lang.psi.OCDefineDirective;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.psi.OCProperty;
import com.jetbrains.cidr.lang.psi.OCStruct;
import com.jetbrains.cidr.lang.psi.OCSymbolDeclarator;
import com.jetbrains.cidr.lang.psi.OCTypeElement;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.util.OCElementUtil;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DoxygenDocumentationProvider extends CidrDocumentationProvider {
   static final String HTML_INDENT = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
   private static final Pattern structuralTagsPattern = Pattern.compile("(\\\\|@)(attention|authors?|bug|copyright|date|deprecated|exception|invariant|param|pre|post|remarks?|result|returns?|retval|sa|see|since|throws?|todo|version|warning)");
   private static final Map<String,String> tagDescriptions;
   private static final Map<String,String> sameMeaningTags;
   private static final Map<String,String> visualCommands;

   @Override
   protected void addMacroDoc(PsiElement originalElement, StringBuilder answer, OCDefineDirective define) {
      super.addMacroDoc(originalElement, answer, define);
      if(originalElement != null) {
         PsiElement macroDefinition = originalElement.getParent();
         if(macroDefinition != null) {
            answer.append("<b>Documentation:</b>");
            this.addCommentDoc(macroDefinition, answer);
         }
      }

   }

   @Override
   protected void addCommentDoc(PsiElement element, StringBuilder answer) {
      PsiComment comment = findCommentFor(element);
      PsiElement associatedElement;
      if(comment == null && element instanceof OCStruct) {
         OCStruct briefComment = (OCStruct)element;
         associatedElement = briefComment.getParent();
         if(associatedElement != null && associatedElement instanceof OCTypeElement) {
            comment = findCommentFor(associatedElement);
         }
      }

      if(comment == null && element instanceof OCSymbolDeclarator) {
         OCSymbol<?> briefComment2 = ((OCSymbolDeclarator<?>)element).getSymbol();
         if(briefComment2 != null) {
            briefComment2 = briefComment2.getAssociatedSymbol();
         }

         if(briefComment2 != null) {
            associatedElement = briefComment2.locateDefinition();
            if(associatedElement != null) {
               comment = findCommentFor(associatedElement);
            }
         }
      }

      if(comment != null) {
         comment = getFirstComment(comment);
         PsiComment briefComment1 = getBriefComment(comment);
         if(briefComment1 != null) {
            addAllComments(briefComment1, answer);
         }
         addAllComments(comment, answer);
      }
   }


   private static PsiComment findCommentFor(PsiElement elt){
      if(elt instanceof OCDeclarator) {
         elt = elt.getParent();
      }

      if(elt == null) {
         return null;
      } else {
         if(elt.getParent() instanceof OCProperty) {
            elt = elt.getParent();
         }

         if(elt.getContainingFile() == null) {
            return null;
         } else {
            PsiElement child = elt.getFirstChild();
            if(child instanceof PsiComment) {
               return (PsiComment)child;
            } else {
               for(elt = elt.getPrevSibling(); elt != null; elt = elt.getPrevSibling()) {
                  if(elt instanceof PsiComment) {
                     PsiElement type = elt.getPrevSibling();
                     if(type == null || type instanceof PsiComment || type instanceof PsiWhiteSpace && type.getText().contains("\n")) {
                        return (PsiComment)elt;
                     }
                  }

                  if(elt.getTextLength() > 0) {
                     IElementType type1 = OCElementUtil.getObjCKeywordElementType(elt.getNode());
                     if(!(elt instanceof PsiWhiteSpace) && type1 != OCTokenTypes.OPTIONAL_KEYWORD && type1 != OCTokenTypes.REQUIRED_KEYWORD) {
                        return null;
                     }
                  }
               }

               return null;
            }
         }
      }
   }

   private static PsiComment getFirstComment(PsiComment comment) {
      while(true) {
         PsiComment prev = getPreviousComment(comment);
         if(prev == null) {
            return comment;
         }

         comment = prev;
      }
   }

   private static PsiComment getPreviousComment(PsiElement element) {
      PsiElement prev = element.getPrevSibling();
      if(prev instanceof PsiComment) {
         return (PsiComment)prev;
      } else {
         if(prev instanceof PsiWhiteSpace) {
            PsiWhiteSpace w = (PsiWhiteSpace)prev;
            PsiElement wPrev = w.getPrevSibling();
            if(wPrev instanceof PsiComment) {
               PsiComment prevComment = (PsiComment)wPrev;
               PsiElement prevCommentPrecedingWS = prevComment.getPrevSibling();
               PsiElement prevCommentParent = prevComment.getParent();
               if(prevCommentPrecedingWS instanceof PsiWhiteSpace && prevCommentPrecedingWS.getText().contains("\n") || !(prevCommentParent instanceof OCFile) || prevCommentPrecedingWS != null) {
                  return prevComment;
               }
            }
         }

         return null;
      }
   }

   private static PsiComment getBriefComment(PsiComment comment) {
      PsiElement parent = comment.getParent();
      PsiComment prev = getPreviousComment(parent);
      return prev == null?null:getFirstComment(prev);
   }

   private static void addAllComments(PsiComment comment, StringBuilder answer) {
      boolean startWithDashStar = false;
      boolean startWithDashDash = false;
      boolean newParagraph = true;
      boolean newTagGroup = false;
      String tagUnderGrouping = null;

      while(true) {
         while(true) {
            String text = comment.getText();
            if(text.startsWith("/*")) {
               text = text.substring(2);
               if(text.startsWith("*") || text.startsWith("!")) {
                  text = text.substring(1);
               }

               if(text.endsWith("*/")) {
                  text = text.substring(0, text.length() - 2);
               }

               startWithDashStar = true;
            } else if(text.startsWith("//")) {
               text = text.substring(2);
               if(text.startsWith("/") || text.startsWith("!")) {
                  text = text.substring(1);
               }

               startWithDashDash = true;
            }

            text = text.trim();
            text = text.replaceAll("(\\\\|@)brief", "\n\n");
            text = text.replaceAll("(\\\\|@)short", "\n\n");
            text = text.replaceAll("(\\\\|@)details", "\n\n");
            text = text.trim();

            String nextComment;
            String nextCommentAfterWS;
            String matcher;
            for(Iterator<Entry<String, String>> next = visualCommands.entrySet().iterator(); next.hasNext(); text = text.replaceAll(nextCommentAfterWS, matcher)) {
               Entry<String, String>  w = next.next();
               String wNext = w.getKey();
               nextComment = w.getValue();
               nextCommentAfterWS = "(\\s)(\\\\|@)(" + wNext + ")(\\s)(\\S+)(\\s)";
               matcher = "$1<" + nextComment + ">$5</" + nextComment + ">$6";
            }

            text = text.replaceAll("(\\\\|@)n", "<br>");
            text = text.trim();
            String[] var22 = StringUtil.splitByLines(text, false);
            int var24 = var22.length;

            for(int var26 = 0; var26 < var24; ++var26) {
               nextComment = var22[var26];
               nextComment = nextComment.trim();
               if(startWithDashStar && nextComment.startsWith("*")) {
                  nextComment = nextComment.substring(1);
               }

               nextComment = nextComment.trim();
               int var28 = nextComment.length();
               if(var28 == 0 || startWithDashStar && StringUtil.countChars(nextComment, '*') == var28 || startWithDashDash && StringUtil.countChars(nextComment, '/') == var28) {
                  newParagraph = true;
               } else {
                  Matcher var30 = structuralTagsPattern.matcher(nextComment);
                  HashSet<String> lineTags = Sets.newHashSet();

                  while(var30.find()) {
                     String arr$ = var30.group(2);
                     if(lineTags.add("@" + arr$)) {
                        nextComment = nextComment.replaceAll("(\\\\|@)" + arr$, "\n@" + arr$);
                     }
                  }

                  nextComment = nextComment.trim();
                  String[] var32 = StringUtil.splitByLines(nextComment, false);
                  int len$ = var32.length;

                  for(int i$ = 0; i$ < len$; ++i$) {
                     String subLine = var32[i$];
                     String lineTag = null;
                     Iterator<String> i$1 = lineTags.iterator();

                     while(i$1.hasNext()) {
                        String tag = (String)i$1.next();
                        if(subLine.startsWith(tag)) {
                           lineTag = tag;
                           subLine = subLine.replace(tag, "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
                           if(sameMeaningTags.containsKey(tag)) {
                              lineTag = sameMeaningTags.get(tag);
                           }
                           break;
                        }
                     }

                     if(lineTag != null) {
                        if(!lineTag.equals(tagUnderGrouping)) {
                           tagUnderGrouping = lineTag;
                           newTagGroup = true;
                        }
                     } else if(newParagraph) {
                        tagUnderGrouping = null;
                     }

                     if(answer.length() > 0) {
                        if(newTagGroup) {
                           answer.append("<br><br><b>").append(tagDescriptions.get(tagUnderGrouping)).append("</b><br><br>");
                        } else if(newParagraph && tagUnderGrouping == null) {
                           answer.append("<br><br>");
                        } else if(lineTag != null) {
                           answer.append("<br>");
                        } else {
                           answer.append(' ');
                        }
                     }

                     answer.append(subLine);
                     newParagraph = false;
                     newTagGroup = false;
                  }
               }
            }

            PsiElement var23 = comment.getNextSibling();
            if(!(var23 instanceof PsiComment)) {
               if(!(var23 instanceof PsiWhiteSpace)) {
                  return;
               }

               PsiWhiteSpace var25 = (PsiWhiteSpace)var23;
               PsiElement var27 = var25.getNextSibling();
               if(!(var27 instanceof PsiComment)) {
                  return;
               }

               PsiComment var31 = (PsiComment)var27;
               PsiElement var29 = var31.getNextSibling();
               if(!(var29 instanceof PsiWhiteSpace) || !var29.getText().contains("\n")) {
                  return;
               }

               comment = var31;
            } else {
               comment = (PsiComment)var23;
            }
         }
      }
   }

   static {
      Builder<String,String> tagDescriptionsBuilder = ImmutableMap.builder();
      tagDescriptions = tagDescriptionsBuilder.put("@attention", "Attention").put("@authors", "Authors").put("@bug", "Bug").put("@copyright", "Copyright").put("@date", "Date").put("@deprecated", "Deprecated").put("@exception", "Exceptions").put("@invariant", "Invariant").put("@param", "Parameters").put("@pre", "Precondition").put("@post", "Postcondition").put("@remarks", "Remarks").put("@returns", "Returns").put("@retval", "Return values").put("@return", "Returns").put("@sa", "See also").put("@since", "Since").put("@todo", "Todo").put("@version", "Version").put("@warning", "Warning").build();
      Builder<String, String> sameMeaningTagsBuilder = ImmutableMap.builder();
      sameMeaningTags = sameMeaningTagsBuilder.put("@author", "@authors").put("@remark", "@remarks").put("@result", "@returns").put("@return", "@returns").put("@see", "@sa").put("@throw", "@exception").put("@throws", "@exception").build();
      Builder<String, String> visualCommandsBuilder = ImmutableMap.builder();
      visualCommands = visualCommandsBuilder.put("a", "em").put("b", "b").put("c", "tt").put("e", "em").put("em", "em").put("p", "tt").build();
   }
}
