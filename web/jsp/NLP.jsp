<%@ page
   language="java"
   import="com.articulate.sigma.*,java.util.*,java.io.*"
   pageEncoding="UTF-8"
   contentType="text/html;charset=UTF-8"
%>
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
August 9, Acapulco, Mexico.  See also http://sigmakee.sourceforge.net
*/
    out.println("<html>");
    out.println("  <head>");
    out.println("    <title>Sigma Knowledge Engineering Environment - NLP</title>");
    out.println("  </head>");
    out.println("  <body bgcolor=\"#FFFFFF\">");

    String theText = request.getParameter("text");
    out.println(theText);

%>
<table width="95%" cellspacing="0" cellpadding="0">
    <tr>
        <td valign="top">
            <table cellspacing="0" cellpadding="0">
                <tr>
                    <td align="left" valign="top"><img src="pixmaps/sigmaSymbol.gif"></td>
                    <td>&nbsp;&nbsp;</td>
                    <td align="left" valign="top"><img src="pixmaps/logoText.gif"><BR>
                        <b><%=greeting%></b></td>
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

    <b>test</b>
    <form name="timeTest" id="timeTest" action="NLP.jsp" method="POST" enctype="multipart/form-data">
  <table>
    <tr>
      <td>
        <b>KB Name:</b>&nbsp;
      </td>
      <td>
        <input type="text" name="test" size="60">
      </td>
    </tr>
  </table>
        <input type="submit" name="submit" value="Submit">
    </form><p>

</ul>
<p>

</BODY>
</HTML>

 
