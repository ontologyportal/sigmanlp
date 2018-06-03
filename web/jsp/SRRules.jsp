<%@ include    file="PreludeNLP.jsp" %>
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

    String propString =  "tokenize, ssplit, pos, lemma, parse, depparse, ner, wsd, wnmw, tsumo, sentiment";
    Pipeline p = new Pipeline(true,propString);
    out.println("<html>");
    out.println("  <head>");
    out.println("    <title>Sigma Knowledge Engineering Environment - NLP</title>");
    out.println("  </head>");
    out.println("  <body bgcolor=\"#FFFFFF\">");

    String pageName = "SRRules";
    pageString = "Semantic Rewriting Rules";
%>
<%@include file="CommonHeader.jsp" %>
<%
    String theText = request.getParameter("textContent");
    if (StringUtil.emptyString(theText))
        theText = "Robert kicks the cart.";
    KB kb = KBmanager.getMgr().getKB("SUMO");
    String kbHref = HTMLformatter.createKBHref("SUMO","EnglishLanguage");
    String wnHref = kbHref.replace("Browse.jsp","WordNet.jsp");
    out.println("<pre>");
    for (String s : Interpreter.firedRules) {
        out.println(s);
    }
    out.println("</pre>");
%>
<%@ include file="Postlude.jsp" %>
</BODY>
</HTML>


