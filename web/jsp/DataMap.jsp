<%@ include file="PreludeNLP.jsp" %>
<%@page import="com.articulate.nlp.brat.BratAnnotationUtil"%>
<%@ page
   language="java"
   import="com.google.common.collect.ImmutableList"
   pageEncoding="UTF-8"
   contentType="text/html;charset=UTF-8"
%>
<%@ page import="com.articulate.nlp.*,com.articulate.nlp.corpora.*,com.articulate.nlp.pipeline.*,com.articulate.nlp.semRewrite.*" %>
<%@ page import="edu.stanford.nlp.ling.CoreLabel,edu.stanford.nlp.time.*,edu.stanford.nlp.pipeline.*,edu.stanford.nlp.sentiment.SentimentCoreAnnotations,edu.stanford.nlp.util.CoreMap,edu.stanford.nlp.ling.CoreAnnotations" %>

<!DOCTYPE html
   PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
   "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
<html xmlns="http://www.w3.org/1999/xhtml" lang="en-US" xml:lang="en-US">

<%
/*
copyright 2018- Infosys

contact Adam Pease adam.pease@infosys.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program ; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston,
MA  02111-1307 USA
 */
    if (KBmanager.getMgr() == null) {
        response.sendRedirect("/sigma/login.html");
        return;
    }

    KBmanager.getMgr().initializeOnce();
    out.println("<html>");
    out.println("  <head>");
    out.println("    <title>Sigma Knowledge Engineering Environment - Data Cleaning</title>");
    out.println("    <link rel=\"stylesheet\" type=\"text/css\" href=\"brat/style-vis.css\" />");
    out.println("    <script type=\"text/javascript\" src=\"brat/client/lib/head.load.min.js\"></script>");
    out.println("  </head>");
    out.println("  <body bgcolor=\"#FFFFFF\">");
    KB kb = KBmanager.getMgr().getKB("SUMO");
    String kbHref = HTMLformatter.createKBHref("SUMO","EnglishLanguage");
    String pageName = "DataMap";
    pageString = "Data Mapping Interface";
%>
<%@include file="CommonHeader.jsp" %>
<%
    DataMapper dm = new DataMapper();
    String accept = request.getParameter("accept");
    String edit = request.getParameter("edit");
    String create = request.getParameter("create");
    if (accept != null) {
        DataMapper.save();
        response.sendRedirect("DataMapper.jsp");
        return;
    }
    else {
        dm.cb = TFIDFUtil.indexDocumentation(true); // index only relations
        System.out.println("DataMap.jsp: doc size: " + dm.cb.lines.size());
        String fname = System.getenv("CORPORA") + File.separator + "UICincome" + File.separator + "adult.data-AP.txt.csv";
        DataMapper.cells = DB.readSpreadsheet(fname,null,false,',');
        if (create != null && create.equals("yes"))
            dm.match();
        System.out.println("DataMap.jsp: doc columns: " + DataMapper.matches.size());
    }
    ArrayList<String> header = dm.cells.get(0);
    ArrayList<String> docs = dm.cells.get(1);
    out.println("<form name=\"interp\" id=\"interp\" action=\"DataMap.jsp\" method=\"GET\">");
    for (int i = 0; i < header.size(); i++) {
        String name = header.get(i);
        if (StringUtil.emptyString(name))
            continue;
        String doc = docs.get(i);
        %>
        <table ALIGN='LEFT' WIDTH='50%'><tr><TD BGCOLOR='#B8CADF'>
            <IMG SRC='pixmaps/1pixel.gif' width=1 height=1 border=0></TD></tr>
        </table><BR>
        <%
        out.println("header: " + name + "<br>\n");
        out.println("description: " + doc + "<br>\n");
        if (DataMapper.matches.size() > i) {
            ArrayList<String> sumoList = DataMapper.matches.get(i);
            //System.out.println("DataMap.jsp: sumo list: " + sumoList);
            if (sumoList != null && sumoList.size() > 0) {
                String sumo = sumoList.get(0);
                if (!StringUtil.emptyString(name))
                    DB2KIF.column2Rel.put(name,sumo);
                int colon = sumo.indexOf(":");
                if (colon < 3)
                    continue;
                String sumoterm = sumo.substring(0,colon-1);
                String sumodoc = sumo.substring(colon+2);
                String SUMOlink = "<a href=\"" + kbHref + "&term=" + sumoterm + "\">" + sumoterm + "</a>";
                out.println("SUMO term: " + SUMOlink + "<br>\n");
                if (sumoList.size() > 1)
                    out.println("SUMO documentation: " + sumodoc + " | <a href=\"DataMapDetail.jsp?col=" + i + "\">more matches</a>");
                out.println("<p>\n");
            }
        }
        else
            out.println("DataMap.jsp: column index " + i + " greater than column size of matches: " + DataMapper.matches.size());

        out.println("<P>");
    }
    out.println("<input type=\"submit\" name=\"accept\" value=\"accept\">");
    out.println("</form>");
%>
<%@ include file="Postlude.jsp" %>
</BODY>
</HTML>