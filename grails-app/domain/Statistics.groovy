/**
This class provides control information to the StatisticsJob job.
When the framework is executed for the first time, it retrieves the value found
in the 'lastmodification' attribute.
If the value is null the current date is used.
*/
class Statistics {
   Date lastmodification
   Date nextanalysis
   int  times_

   static constraints = {
      nextanalysis(nullable: false)
   }
}
