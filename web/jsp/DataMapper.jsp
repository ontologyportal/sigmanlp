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
    String kbName = "SUMO";
    out.println("<html>");
    out.println("  <head>");
    out.println("    <title>Sigma Knowledge Engineering Environment - Data Cleaning</title>");
    out.println("    <link rel=\"stylesheet\" type=\"text/css\" href=\"brat/style-vis.css\" />");
    out.println("    <script type=\"text/javascript\" src=\"brat/client/lib/head.load.min.js\"></script>");
    out.println("  </head>");
    out.println("  <body bgcolor=\"#FFFFFF\">");
    KB kb = KBmanager.getMgr().getKB("SUMO");
    String kbHref = HTMLformatter.createKBHref("SUMO","EnglishLanguage");
    String pageName = "DataMapper";
    pageString = "Data Mapping Interface Home";
%>
<%@include file="CommonHeader.jsp" %>
<%
    DataMapper dm = new DataMapper();
    String load = request.getParameter("load");
    if (!StringUtil.emptyString(load) && load.equals("map")) {
        DataMapper.loadMatches();
    }
    String clean = request.getParameter("clean");
    if (!StringUtil.emptyString(clean) && clean.equals("clean")) {
        out.println(DataMapper.clean());
    }
    String save = request.getParameter("save");
    if (!StringUtil.emptyString(save) && save.equals("save")) {
        DataMapper.save();
    }
    String create = request.getParameter("create");
    if (!StringUtil.emptyString(create)) {
        response.sendRedirect("DataMap.jsp?create=yes");
        return;
    }
    String edit = request.getParameter("edit");
    if (!StringUtil.emptyString(edit)) {
        response.sendRedirect("DataMap.jsp?edit=yes");
        return;
    }
    String dataFile = request.getParameter("dataFile");
    if (StringUtil.emptyString(dataFile))
        dataFile = DataMapper.inputFilename;
    else
        DataMapper.inputFilename = dataFile;
    String mapFile = request.getParameter("mapFile");
    if (StringUtil.emptyString(mapFile))
        mapFile = DataMapper.matchFilename;
    else
        DataMapper.matchFilename = mapFile;
    System.out.println("DataMapper.jsp: dataFile: " + dataFile);
    System.out.println("DataMapper.jsp: mapFile: " + mapFile);
%>
    <form name=interp id=interp action=DataMapper.jsp method="GET" enctype="multipart/form-data">
        <input type="hidden" name="kb" value=<%=kbName%>><br>
        Data File: <input type="file" name="dataFile" onchange="form.submit()" /> : <input type="submit" name="load" value="data"><br>
        <% if (!StringUtil.emptyString(DataMapper.inputFilename)) {
            out.println("Data file: " + DataMapper.inputFilename);
        %>
            <input type="submit" name="create" value="create mapping"><br>
            Mapping File: <input type="file" name="mapFile" onchange="form.submit()" /> : <input type="submit" name="load" value="map"><br>
        <% } %>
        <% if (!StringUtil.emptyString(mapFile)) {
            out.println("Map file: " + mapFile);
        %>
            <input type="submit" name="edit" value="edit mapping"><br>
        <% } %>
        <% if (!StringUtil.emptyString(dataFile) && !StringUtil.emptyString(mapFile)) { %>
            <input type="submit" name="apply" value="apply mapping"><br>
            <input type="submit" name="clean" value="clean"><br>
        <% } %>
        <% if (DataMapper.matches.size() > 0) { %>
            <input type="submit" name="save" value="save"><br>
        <% } %>
    </form>
<%@ include file="Postlude.jsp" %>
</BODY>
</HTML>