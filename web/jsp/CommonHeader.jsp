<%
/** This code is copyright Teknowledge (c) 2003, Articulate Software (c) 2003-2017,
    Infosys (c) 2017-present.

    This software is released under the GNU Public License
    <http://www.gnu.org/copyleft/gpl.html>.

    Please cite the following article in any publication with references:

    Pease A., and Benzmüller C. (2013). Sigma: An Integrated Development Environment
    for Logical Theories. AI Communications 26, pp79-97.  See also
    http://github.com/ontologyportal

    This page is designed to be included in others and must have the following variables set:
    pageName, pageString, role

    welcomeString should be derived from PreludeNLP.jsp
*/
%>

<TABLE width="95%" cellspacing="0" cellpadding="0">
  <TR>
      <TD align="left" valign="top"><img src="pixmaps/sigmaSymbol-gray.gif"></TD>
      <TD align="left" valign="top"><img src="pixmaps/logoText-gray.gif"><br><B><%=pageString %></B><%=welcomeString%></TD>
      <TD valign="bottom"></TD>
      <TD>
        <font FACE="Arial, Helvetica" SIZE=-1><b>[&nbsp;
        <%
            if (pageName == null || !pageName.equals("NLP"))
                out.println("<A href=\"NLP.jsp\"><b>NLP</b></A>&nbsp;|&nbsp");
            if (pageName == null || !pageName.equals("Concordancer"))
                out.println("<A href=\"semconcor.jsp\"><B>Concordancer</B></A>&nbsp;|&nbsp");
            if (pageName == null || !pageName.equals("Unify"))
                out.println("<A href=\"unify.jsp\"><B>Unify</B></A>&nbsp;|&nbsp");
        %>
        ]&nbsp;</b>
        <%
        if (pageName.equals("semconcor")) {
            out.println(" <form name=\"timeTest\" id=\"timeTest\" action=\"semconcor.jsp\" method=\"GET\">");
            out.println("<b>Corpus:&nbsp;" + HTMLformatter.createMenu("corpus",corpus,CorpusReader.findCorporaDBs()));
        }
        %></b>
      <BR>
      </TD>
  </TR>
</TABLE><br>