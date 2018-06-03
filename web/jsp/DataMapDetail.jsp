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
    String reload = request.getParameter("return");
    if (reload != null) {
        response.sendRedirect("/sigmanlp/DataMap.jsp");
        return;
    }
    String choice = request.getParameter("choice");
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
    pageString = "Data Mapping Detail";
%>
<%@include file="CommonHeader.jsp" %>
<%
    String column = request.getParameter("col");
    int colnum = 0;
    if (!StringUtil.emptyString(column))
        colnum = Integer.parseInt(column);

    String fname = System.getenv("CORPORA") + File.separator + "UICincome" + File.separator + "adult.data-AP.txt.csv";
    ArrayList<ArrayList<String>> cells = DB.readSpreadsheet(fname,null,false,',');
    ArrayList<String> header = cells.get(0);
    ArrayList<String> docs = cells.get(1);
    String name = header.get(colnum);
    String doc = docs.get(colnum);
    String accept = request.getParameter("accept");
    if (!StringUtil.emptyString(accept)) {
        System.out.println("DataMapDetail.jsp: swapping in " + choice);
        DB2KIF.column2Rel.put(name,choice);
        DataMapper.swapMatches(colnum,choice);
    }
    out.println("Table header name: <b>" + name + "</b><br>\n");
    out.println("Table header description: " + doc + "<br>\n");
    out.println("column: " + colnum + "<br>\n");
    out.println("<hr>");
    if (DataMapper.matches.get(colnum).size() > colnum) {
        ArrayList<String> candidates = DataMapper.matches.get(colnum);
        out.println("<form name=\"interp\" id=\"interp\" action=\"DataMapDetail.jsp\" method=\"GET\">");
        for (String sumo : candidates) {
            int colon = sumo.indexOf(":");
            if (colon < 3)
                continue;
            String sumoterm = sumo.substring(0,colon-1);
            String sumodoc = sumo.substring(colon+2);
            String SUMOlink = "<a href=\"" + kbHref + "&term=" + sumoterm + "\">" + sumoterm + "</a>";
            out.println(SUMOlink + "<br>\n");
            out.println(sumodoc);
            String checked = "";
            if (choice != null && choice.equals(sumoterm))
                checked = "checked";
            out.println("<input type=\"radio\" name=\"choice\" value=\"" + sumoterm + "\" " + checked + "><p>\n");
        }
        %>
        <input type="submit" name="accept" value="accept">
        <input type="submit" name="return" value="return">
        <input type="hidden" name="col" value=<%=column %>>
        </form>
        <%
    }
    out.println("<P>");
%>
<%@ include file="Postlude.jsp" %>
</BODY>
</HTML>