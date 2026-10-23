package crml.compiler.omc;

import java.util.ArrayList;
import java.util.List;

import crml.compiler.omc.OMCUtil.OMCFilesLog;

public class OMCmsg {
        public OMCFilesLog files;
        public String msg;
  
        public OMCmsg(OMCFilesLog files, String msg){
          this.files=files;
          this.msg=msg;
        }

        /** Variables compared against the reference result, or null if no comparison was made */
        public List<String> comparedVars(){
          return markedBlock("CRML-COMPARED-VARS");
        }

        /** Variables that differ from the reference result, or null if no comparison was made */
        public List<String> diffFailedVars(){
          return markedBlock("CRML-DIFF-FAILED-VARS");
        }

        private List<String> markedBlock(String marker){
          if (msg == null)
            return null;
          String begin = "[" + marker + "-BEGIN]";
          int start = msg.indexOf(begin);
          if (start < 0)
            return null;
          start += begin.length();
          // OMC may have aborted before printing the end marker
          int end = msg.indexOf("[" + marker + "-END]", start);
          String block = end < 0 ? msg.substring(start) : msg.substring(start, end);
          List<String> vars = new ArrayList<>();
          for (String line : block.split("\\R")) {
            String v = line.trim();
            if (!v.isEmpty())
              vars.add(v);
          }
          return vars;
        }
}
