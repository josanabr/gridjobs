/**
This module analyzes the collected data from the Task Monitor.
It is based from the code provided by the code located in the
${HOME}/src/analyzer-input directory.
*/

import org.joda.time.DateTime

class StatisticsJob{
    def cronExpression = "0 0 0/1 * * ?"
    def sessionRequired = false
    def quartzScheduler

    def execute(context) {
       def starttime = new DateTime()
       def currenttime = null
       def ststarttime = util.joda.DateTime(starttime)
       println "[StatisticsJob - ${ststarttime}] Starting analysis process"
       // Removing the current trigger
       if (util.quartz.Util.removetrigger(context)) {
          println "[StatisticsJob - ${ststarttime}] Trigger removed"
       } else {
          println "[StatisticsJob - ${ststarttime}] Trigger did not remove"
       }

       def statisticsfirst = null
       try { 
          statisticsfirst = Statistics.get(1)
       } catch (Exception e) {
          println "[StatisticsJob - ${ststarttime}] Exception getting the first record from Statistics table"
          println "[StatisticsJob - ${ststarttime}] ${e}"
          e.printStackTrace()
       }
       def nexttime = null
       if (statisticsfirst != null)  { 
          nexttime = statisticsfirst.nextanalysis
       } else {
          //This event happens the very first time that the application runs
       }

       def flag = false 
       def trigger = new CronTrigger()
       currenttime = new DateTime()

       /*
               currenttime       nexttime
       |            ^                ^
       |------------|----------------|--------------->
       |

          ----> No analysis is required

       */
       if (currenttime < nexttime) { // No statistical analysis to be done!
          trigger.setJobName(config.Config.statisticaljobname)
          trigger.setJobGroup(config.Config.statisticaljobgroup)
          flag = true
       }

       def totalseconds = 0
       {
          def days
          def hours
          def minutes
          def seconds
          try {
             def reader = new XmlParser().parse("config.xml")
             days = new Integer(reader.statistics[0].threshold[0].days[0].'@value')
             hours = new Integer(reader.statistics[0].threshold[0].hours[0].'@value')
             minutes = new Integer(reader.statistics[0].threshold[0].minutes[0].'@value')
             seconds = new Integer(reader.statistics[0].threshold[0].seconds[0].'@value')
          } catch(Exception e) {
             println "[StatisticsJob - ${sttarttime}] Exception reading the config.xml file"
             println e
             e.printStackTrace()
             return
          }

          totalseconds = days*3600*24 + hours*3600 + minutes*60 + seconds
       }
       if (flag) {
          trigger.setCronExpression( new CronExpression(util.quartz.Util.createcronexpression("NOW+${totalseconds}s")) )
          def _date = quartzScheduler.scheduleJob(trigger)
          println "[StatisticsJob - ${ststarttime}] This trigger will be launched on ${_date}"
          return
       }

       // Statistical Analysis is required

       /*
              lastmodification  lastanalysis
       |            ^                ^
       |------------|----------------|--------------->
       |

           ----> Nothing has happened.

       */

       def lastmodification = statisticsfirst.lastmodification.time
       def lastanalysis = starttime.getMillis() - totalseconds * 1000
       if (lastmodification < lastanalysis) {
          println "[StatisticsJob - ${ststarttime}] No new modifications since last statistical analysis"
          return
       }

    }
}
