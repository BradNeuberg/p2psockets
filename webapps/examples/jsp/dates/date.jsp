<html>
<!--
  Copyright (c) 1999 The Apache Software Foundation.  All rights 
  reserved.
-->

<%@ page session="false"%>

<body bgcolor="white">
<jsp:useBean id='clock' scope='page' class='dates.JspCalendar' type="dates.JspCalendar" />

<font size=4>
<ul>
<li>	Day of month: is  <jsp:getProperty name="clock" property="dayOfMonth"/>
<li>	Year: is  <jsp:getProperty name="clock" property="year"/>
<li>	Month: is  <jsp:getProperty name="clock" property="month"/>
<li>	Time: is  <jsp:getProperty name="clock" property="time"/>
<li>	Date: is  <jsp:getProperty name="clock" property="date"/>
<li>	Day: is  <jsp:getProperty name="clock" property="day"/>
<li>	Day Of Year: is  <jsp:getProperty name="clock" property="dayOfYear"/>
<li>	Week Of Year: is  <jsp:getProperty name="clock" property="weekOfYear"/>
<li>	era: is  <jsp:getProperty name="clock" property="era"/>
<li>	DST Offset: is  <jsp:getProperty name="clock" property="DSTOffset"/>
<li>	Zone Offset: is  <jsp:getProperty name="clock" property="zoneOffset"/>
</ul>
</font>

</body>
</html>
