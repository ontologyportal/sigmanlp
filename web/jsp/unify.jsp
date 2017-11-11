<%@page import="com.articulate.nlp.brat.BratAnnotationUtil"%>
<%@ page
   language="java"
   import="com.google.common.collect.ImmutableList,com.articulate.sigma.*,java.util.*,java.io.*"
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
/** This code is copyright Articulate Software (c) 2017. Infosys 2017

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
    out.println("    <title>Sigma Knowledge Engineering Environment - Unifier</title>");
    out.println("    <link rel=\"stylesheet\" type=\"text/css\" href=\"brat/style-vis.css\" />");
    out.println("    <script type=\"text/javascript\" src=\"brat/client/lib/head.load.min.js\"></script>");
    out.println("  </head>");
    out.println("  <body bgcolor=\"#FFFFFF\">");

    String theText = request.getParameter("textContent");
    if (theText == null || theText.equals("null"))
        theText = "John kicks the cart.\nSusan pushes the wagon.";
    String dep = request.getParameter("dep");
    KB kb = KBmanager.getMgr().getKB("SUMO");
    String kbHref = HTMLformatter.createKBHref("SUMO","EnglishLanguage");
    String wnHref = kbHref.replace("Browse.jsp","WordNet.jsp");
    String file = request.getParameter("file");
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
          <b>[&nbsp;<a href="Properties.jsp">Preferences</a>&nbsp;]</b>
        </span>
        </td>
    </tr>
</table>
<br><table ALIGN="LEFT" WIDTH=80%><tr><TD BGCOLOR='#AAAAAA'>
<IMG SRC='pixmaps/1pixel.gif' width=1 height=1 border=0></TD></tr></table><BR>

    <form name="unify" id="unify" action="unify.jsp" method="GET">
        <b>Enter one line per sentence to find common pattern: </b><P>
        <textarea name="textContent" cols="100" rows="5">
<%=theText %>
        </textarea><P>
        <input type="submit" name="submit" value="Submit">
    </form><p>

</ul>
<p>

<%
    String cnfForm = "";
    if (!StringUtil.emptyString(theText) || !StringUtil.emptyString(dep)) {
        CommonCNFUtil ccu = new CommonCNFUtil();
        ccu.kb = KBmanager.getMgr().getKB("SUMO");
        cnfForm = ccu.findCommonCNF(theText).toString();
        out.println(cnfForm);
    }
    else
        out.println("Empty input<P>\n");
%>
    <hr>
    <form name="submitForm" id="submitForm" action="semconcor.jsp" method="GET">
        <b>Search for form in concordancer </b><P>
        <input type ="hidden" name="dep" value="<%=cnfForm %>">
        <input type="submit" name="submit" value="Submit">
    </form><p>

</BODY>
</HTML>

