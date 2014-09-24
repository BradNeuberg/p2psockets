<html>
<head>
<title>Example Mail Sending Form</title>
</head>
<body bgcolor="white">

<p><font color=red>This demos have been borrowed from the jakarta project as
a demonstration of webapplication portability.  Only cosmetic changes (such as
this one) or references to non-standard configuration have been changed. 
The mail demo will not work in standard Jetty, only in JettyPlus.
</font>


<p>This page will send an electronic mail message via the
<code>javax.mail.Session</code> resource factory that is configured into
the JNDI context for this web application.  Before it can be used
successfully, <font color=red>the JettyPlus configuration file in 
extra/etc/jettyplus.xml must be edited and your SMTP mail details
added to the MailServer entry.</font>
All of the fields below are required.

<form method="POST" action="../../SendMailServlet">
<table>

  <tr>
    <th align="center" colspan="2">
      Enter The Email Message To Be Sent
    </th>
  </tr>

  <tr>
    <th align="right">From:</th>
    <td align="left">
      <input type="text" name="mailfrom" size="60">
    </td>
  </tr>

  <tr>
    <th align="right">To:</th>
    <td align="left">
      <input type="text" name="mailto" size="60">
    </td>
  </tr>

  <tr>
    <th align="right">Subject:</th>
    <td align="left">
      <input type="text" name="mailsubject" size="60">
    </td>
  </tr>

  <tr>
    <td colspan="2">
      <textarea name="mailcontent" rows="10" cols="80">
      </textarea>
    </td> 
  </tr>

  <tr>
    <td align="right">
      <input type="submit" value="Send">
    </td>
    <td align="left">
      <input type="reset" value="Reset">
    </td>
  </tr>

</table>
</form>

</body>
</html>
