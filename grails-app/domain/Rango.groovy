/**
This table is populated during application bootstrapping.
*/

class Rango {
   String stvalue // This string could contain something like this:
                  // stvalue := '< estimatedtime' 
                  //            | 'estimatedtime <= x < 2*estimatedtime'
                  //            | '2*estimatedtime <= x < 3*estimatedtime'
                  // Then, it contains a boolean expression with variables
                  // 'estimatedtime' and 'x'.
                  // 'x' is obtained from:
                  //           'currenttime - taskestimationtime.starttime'
   int value // This information is getting from configuration file
/*
A tentative configuration file for 'Rango' could be:
<range>
<item stvalue='x &lt; estimatedtime' value='0'/>
<item stvalue='estimatedtime &lt;= x &amp;&amp; x &lt; 2*estimatedtime' value='1'/>
<item stvalue='2*estimatedtime &lt;= x &amp;&amp; x &lt; 3*estimatedtime' value='2'/>
<!-- and so on -->
</range>

This information will populate the table range during application bootstrapping.

WARN The names 'estimatedtime' and 'x' must be preserved. The 'estimatedtime'
~~~~ is getting from 'taskestimationtime' table and 'x' is the difference 
     between the 'currenttime' and 'starttime' (the latter variable is also
     obtained from 'taskestimationtime' table)
*/
}
