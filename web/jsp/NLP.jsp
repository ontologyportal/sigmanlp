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
/** This code is copyright Articulate Software (c) 2017. Infosys 2017-
This software is released under the GNU Public License <http://www.gnu.org/copyleft/gpl.html>.
Users of this code also consent, by use of this code, to credit Articulate Software
in any writings, briefings, publications, presentations, or
other representations of any software which incorporates, builds on, or uses this
code.  Please cite the following article in any publication with references:

Pease, A., (2003). The Sigma Ontology Development Environment,
in Working Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems,
August 9, Acapulco, Mexico.  See also http://github.com/ontologyportal

Authors:
	Adam Pease
	Infosys LTD.
*/
    if (KBmanager.getMgr() == null) {
        response.sendRedirect("/sigma/login.html");
        return;
    }
    KBmanager.getMgr().initializeOnce();
    TimeBank.init();
    Interpreter interp = new Interpreter();
    interp.initialize();

    //String filename = System.getProperty("user.home") + "/workspace/sumo/WordNetMappings" + File.separator + "Relations.txt";
    RelExtract.initOnce();

    String propString =  "tokenize, ssplit, pos, lemma, parse, depparse, ner, wsd, wnmw, tsumo, sentiment";
    Pipeline p = new Pipeline(true,propString);
    out.println("<html>");
    out.println("  <head>");
    out.println("    <title>Sigma Knowledge Engineering Environment - NLP</title>");
    out.println("    <link rel=\"stylesheet\" type=\"text/css\" href=\"brat/style-vis.css\" />");
    out.println("    <script type=\"text/javascript\" src=\"brat/client/lib/head.load.min.js\"></script>");
    out.println("  </head>");
    out.println("  <body bgcolor=\"#FFFFFF\">");

    String theText = request.getParameter("textContent");
    String fired = request.getParameter("firedRules");
    if (fired != null) {
        response.sendRedirect("SRRules.jsp");
        return;
    }
    if (StringUtil.emptyString(theText))
        theText = "Robert kicks the cart.";
    KB kb = KBmanager.getMgr().getKB("SUMO");
    String kbHref = HTMLformatter.createKBHref("SUMO","EnglishLanguage");
    String wnHref = kbHref.replace("Browse.jsp","WordNet.jsp");

    String reload = request.getParameter("reload");
    if (reload != null) {
        interp.loadRules();
        RelExtract.initOnce();
    }

    String pageName = "NLP";
    pageString = "NLP Interface";

    System.out.println("NLP.jsp: Running language to logic");
    List<String> forms = interp.interpret(theText);
    List<CNF> inputs = new ArrayList<>();
    //System.out.println("NLP.jsp: Running relation extraction");
    //ArrayList<RHS> kifClauses = RelExtract.sentenceExtract(theText);
%>
<%@include file="CommonHeader.jsp" %>

<table ALIGN="LEFT" WIDTH=80%><tr><TD BGCOLOR='#AAAAAA'>
<IMG SRC='pixmaps/1pixel.gif' width=1 height=1 border=0></TD></tr></table><BR>

    <form name="timeTest" id="timeTest" action="NLP.jsp" method="GET">
        <b>Process a sentence: </b>&nbsp;
        <input type="text" name="textContent" size="60" value="<%=theText %>">
        <input type="submit" name="submit" value="Submit">
    </form><p>

</ul>
<p>

<%
    if (!StringUtil.emptyString(theText)) {
        Annotation wholeDocument = new Annotation(theText);
        wholeDocument.set(CoreAnnotations.DocDateAnnotation.class, "2017-05-08");
        p.pipeline.annotate(wholeDocument);
        List<CoreMap> timexAnnsAll = wholeDocument.get(TimeAnnotations.TimexAnnotations.class);
        if (timexAnnsAll != null && timexAnnsAll.size() > 0) {
            out.println("<h2>Time</h2>\n"); // -----------------------------------------------------------
            for (CoreMap token : timexAnnsAll) {
                Timex time = token.get(TimeAnnotations.TimexAnnotation.class);
                out.println("time token and value: <pre>" + token + ":" + time.value() + "</pre>\n");
                String tsumo = token.get(TimeSUMOAnnotator.TimeSUMOAnnotation.class);
                Formula tf = new Formula(tsumo);
                out.println(tf.htmlFormat(kb,HTMLformatter.createHrefStart()) + "<P>\n");
            }
        }

        out.println("<P>\n");
        out.println("<h2>Tokens</h2>\n"); // -------------------------------------------------------------
        List<String> senses = new ArrayList<String>();
        List<CoreMap> sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
        List<CoreLabel> tokens;
        String orig, lemma, pos, poslink, sense, sumo, multi, keylink, SUMOlink;
        for (CoreMap sentence : sentences) {
            tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            for (CoreLabel token : tokens) {
                orig = token.originalText();
                lemma = token.lemma();
                pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                poslink = "<a href=\"https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html\">" +
                    pos + "</a>";
                sense = token.get(WSDAnnotator.WSDAnnotation.class);
                if (!StringUtil.emptyString(sense))
                    senses.add(sense);
                sumo = token.get(WSDAnnotator.SUMOAnnotation.class);
                multi = token.get(WNMultiWordAnnotator.WNMultiWordAnnotation.class);
                out.print(orig);
                if (!StringUtil.emptyString(lemma))
                    out.print("/" + lemma);
                if (!StringUtil.emptyString(pos))
                    out.print("/" + poslink);
                if (!StringUtil.emptyString(sense)) {
                    keylink = "<a href=\"" + wnHref + "&synset=" + sense + "\">" + sense + "</a>";
                    out.print("/" + keylink);
                }
                if (!StringUtil.emptyString(sumo)) {
                    SUMOlink = "<a href=\"" + kbHref + "&term=" + sumo + "\">" + sumo + "</a>";
                    out.print("/" + SUMOlink);
                }
                if (!StringUtil.emptyString(multi))
                    out.print("/" + multi);
                out.println("&nbsp;&nbsp; ");
            }
            out.println("<P>\n");
        }

        Iterator<String> it = senses.iterator();
        out.println("<table>");
        String key, SUMO, wordstr;
        List<String> words;
        while (it.hasNext()) {
            key = it.next();
            keylink = "<a href=\"" + wnHref + "&synset=" + key + "\">" + key + "</a>";
            SUMO = WordNetUtilities.getBareSUMOTerm(WordNet.wn.getSUMOMapping(key));
            SUMOlink = "<a href=\"" + kbHref + "&term=" + SUMO + "\">" + SUMO + "</a>";
            words = WordNet.wn.synsetsToWords.get(key);
            wordstr = "";
            if (words != null)
                wordstr = words.toString();
            out.println("<tr><td>" + keylink + "</td><td>" +
                SUMOlink + "</td><td>" + wordstr + "</td></tr><P>\n");
        }
        out.println("</table><P>");

        out.println("<h2>Visualization</h2>\n"); // ------------------------------------------------------
        out.println("<div id=\"bratVizDiv\" style=\"\"></div><P>\n");

        out.println("<h2>Dependencies</h2>\n"); // -------------------------------------------------------
        out.println("<table><tr><th>original</th><th>augmented</th><th>substitutors</th></tr><tr><td>\n");
        List<Literal> dependencies;
        for (CoreMap sentence : sentences) {
            out.println("<pre>");
            dependencies = SentenceUtil.toDependenciesList(ImmutableList.of(sentence));
            for (Literal l : dependencies)
                out.println(l);
            out.println("</pre><P>");
        }
        out.println("</td>\n");
        out.println("<td><pre>\n");
        for (Literal l : interp.augmentedClauses)
            out.println(l);
        out.println("</pre>");

        out.println("</td>");
        out.println("<td><pre>\n");
        //out.println(interp.substitutor.toString());
        out.println("</pre>");
        out.println("</td>");
        out.println("</tr></table>\n");
        out.println("<P>");

        //out.println("<h2>Relations</h2>\n"); // ----------------------------------------------------------
        //out.println("<pre>");
        //out.println(kifClauses);
        //out.println("</pre>");
        //out.println("<P>");

        out.println("<h2>Interpretation</h2>\n"); // -----------------------------------------------------
        %>
            <form name="interp" id="interp" action="NLP.jsp" method="GET">
                <input type="hidden" name="textContent" size="60" value="<%=theText %>">
                <input type="submit" name="firedRules" value="firedRules">
                <input type="submit" name="reload" value="reload">
            </form><p>
        <%
        if (forms != null) {
            Formula theForm;
            for (String s : forms) {
                theForm = new Formula(s);
                out.println(theForm.htmlFormat(kb,HTMLformatter.createHrefStart()) + "<P>");
            }
        }

        out.println("<h2>Sentiment</h2>\n"); // ----------------------------------------------------------
        out.println("<table>");
        String sentiment;
        for (CoreMap sentence : sentences) {
            sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            for (CoreLabel token : tokens) {
                sumo = token.get(WSDAnnotator.SUMOAnnotation.class);
                if (!StringUtil.emptyString(sumo)) {
                    SUMOlink = "<a href=\"" + kbHref + "&term=" + sumo + "\">" + sumo + "</a>";
                    out.println("<tr><td>" + SUMOlink + "</td><td>" + sentiment + "</td></tr><P>\n");
                }
            }
        }
        out.println("</table><P>\n");

        out.println("<b>Sigma sentiment score:</b> " + DB.computeSentiment(theText) + "</P>\n");
        BratAnnotationUtil bratAnnotationUtil = new BratAnnotationUtil();
        out.println("<script type=\"text/javascript\">");
        out.println("var docData=" + bratAnnotationUtil.getBratAnnotations(theText,wholeDocument) + ";</script>");
        out.println("<script type=\"text/javascript\" src=\"js/sigmanlpViz.js\"></script>");
    }
    else
        out.println("Empty input<P>\n");
%>

</BODY>
</HTML>
