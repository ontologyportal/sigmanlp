<%@ include    file="PreludeNLP.jsp" %>
<%@page import="com.articulate.nlp.brat.BratAnnotationUtil"%>
<%@ page
   language="java"
   import="com.google.common.collect.ImmutableList"
   pageEncoding="UTF-8"
   contentType="text/html;charset=UTF-8"
%>
<%@ page import="com.articulate.nlp.*,com.articulate.nlp.corpora.*,com.articulate.nlp.pipeline.*,com.articulate.nlp.semRewrite.*,com.articulate.nlp.semconcor.*" %>
<%@ page import="edu.stanford.nlp.ling.CoreLabel,edu.stanford.nlp.time.*,edu.stanford.nlp.pipeline.*,edu.stanford.nlp.sentiment.SentimentCoreAnnotations,edu.stanford.nlp.util.CoreMap,edu.stanford.nlp.ling.CoreAnnotations" %>

<!DOCTYPE html
   PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
   "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
<html xmlns="http://www.w3.org/1999/xhtml" lang="en-US" xml:lang="en-US">

<%
/** This code is copyright Articulate Software (c) 2017.
This software is released under the GNU Public License <http://www.gnu.org/copyleft/gpl.html>.
Users of this code also consent, by use of this code, to credit Articulate Software
in any writings, briefings, publications, presentations, or
other representations of any software which incorporates, builds on, or uses this
code.  Please cite the following article in any publication with references:

Pease, A., (2003). The Sigma Ontology Development Environment,
in Working Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems,
August 9, Acapulco, Mexico.  See also http://github.com/ontologyportal
*/
    if (KBmanager.getMgr() == null) {
        response.sendRedirect("/sigma/login.html");
        return;
    }
    KBmanager.getMgr().initializeOnce();
    Indexer.UserName = KBmanager.getMgr().getPref("dbUser");
    Interpreter interp = new Interpreter();
    interp.initialize();

    out.println("<html>");
    out.println("  <head>");
    out.println("    <title>Sigma Knowledge Engineering Environment - Semantic Concordancer</title>");
    out.println("    <link rel=\"stylesheet\" type=\"text/css\" href=\"brat/style-vis.css\" />");
    out.println("    <script type=\"text/javascript\" src=\"brat/client/lib/head.load.min.js\"></script>");
    out.println("  </head>");
    out.println("  <body bgcolor=\"#FFFFFF\">");

    String theText = request.getParameter("textContent");
    if (theText == null || theText.equals("null"))
        theText = "";
    String dep = request.getParameter("dep");
    if (dep == null || dep.equals("null"))
        dep = "";
    if (StringUtil.emptyString(theText) && StringUtil.emptyString(dep))
        theText = "on the boat";
    KB kb = KBmanager.getMgr().getKB("SUMO");
    String kbHref = HTMLformatter.createKBHref("SUMO","EnglishLanguage");
    String wnHref = kbHref.replace("Browse.jsp","WordNet.jsp");
    String depGraph = request.getParameter("depGraph");
%>
<table width="95%" cellspacing="0" cellpadding="0">
    <tr>
        <td valign="top">
            <table cellspacing="0" cellpadding="0">
                <tr>
                    <td align="left" valign="top"><img src="pixmaps/sigmaSymbol.gif"></td>
                    <td>&nbsp;&nbsp;</td>
                    <td align="left" valign="top"><img src="pixmaps/logoText.gif"><BR>
                        <b>Semantic Concordancer</b></td>
                </tr>

            </table>
        </td>
        <td>
        <span class="navlinks">
          <b>[&nbsp;<a href="Properties.jsp">Preferences</a>&nbsp;|&nbsp;<a href="NLP.jsp">NLP</a>&nbsp;|&nbsp;<a href="unify.jsp">Unify</a>&nbsp;]</b>
        </span>
        </td>
    </tr>
</table>
<br><table ALIGN="LEFT" WIDTH=80%><tr><TD BGCOLOR='#AAAAAA'>
<IMG SRC='pixmaps/1pixel.gif' width=1 height=1 border=0></TD></tr></table><BR>

    <form name="timeTest" id="timeTest" action="semconcor.jsp" method="GET">
        <b>Search for a word or phrase: </b>&nbsp;
        <input type="text" name="textContent" size="60" value="<%=theText %>"><br>
        <b>or a dependency pattern: </b>&nbsp;
        <input type="text" name="dep" size="60" value="<%=dep %>"><br>
        <b>Show dependency graph: </b>
        <input type="checkbox" name="depGraph" value="checked" <%=depGraph %>><br>
        <input type="submit" name="submit" value="Submit">
    </form><p>

</ul>
<p>

<%
    if (!StringUtil.emptyString(theText) || !StringUtil.emptyString(dep)) {
        ArrayList<String> sentences = new ArrayList<>();
        ArrayList<String> dependencies = new ArrayList<>();
        Searcher.search(theText,dep,sentences,dependencies);
        HashSet<String> printed = new HashSet<>();

        out.println("<P>");
        for (int i = 0; i < dependencies.size(); i++) {
            String s = sentences.get(i);
            if (!printed.contains(s)) {
                String highlightSent = Searcher.highlightSent(s,theText);
                out.println(highlightSent + "<p>");
                String d = dependencies.get(i);
                d = Searcher.highlightDep(dep,d); // dep is the pattern, d is the full dependency
                out.println("<font size=-1 style=bold face=courier>");
                out.println(d);
                out.println("</font><P>");
                if (depGraph != null && depGraph.equals("checked")) {
                    out.println("<div id=\"bratVizDiv\" style=\"\"></div><P>\n");
                    //Get data in brat format
                    BratAnnotationUtil bratAnnotationUtil = new BratAnnotationUtil();
                    out.println("<script type=\"text/javascript\">");
                    ArrayList<String> literals = Literal.stringToLiteralList(dependencies.get(i));
                    out.println("var docData=" + bratAnnotationUtil.getBratAnnotations(literals) + ";</script>");
                    //Brat integration script
                    out.println("<script type=\"text/javascript\" src=\"js/sigmanlpViz.js\"></script>");
                }
                out.println("<hr>\n");
                printed.add(s);
            }
        }

        %>
            <form name="interp" id="interp" action="NLP.jsp" method="GET">
                <input type="hidden" name="textContent" size="60" value="<%=theText %>">
                <input type="submit" name="dep" value="dep">
                <input type="submit" name="reload" value="reload">
            </form><p>
        <%
    }
    else
        out.println("Empty input<P>\n");
%>

</BODY>
</HTML>

