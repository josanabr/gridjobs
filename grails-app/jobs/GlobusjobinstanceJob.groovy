import org.joda.time.DateTime
import org.joda.time.Period
import org.quartz.CronExpression
import org.quartz.Trigger
import org.quartz.CronTrigger

class GlobusjobinstanceJob{
   static triggers =  { }
   def quartzScheduler
   def group = config.Config.jobgroup
   def globushome = config.Config.globushome
   
   //def globusjobrun = "globus-job-run"
   def globusjobrun = config.Config.globusjobrun
   def globusjobsubmit = config.Config.globusjobsubmit

   def computenodefield = config.Config.computenodefield
   def elapsedtimefield = config.Config.elapsedtimefield
   def outputremoteexecution = config.Config.outputremoteexecution

    def execute(context) {
       def report 
       def elapsedtime = "No time"
       def computenode = "Non identified"
       // cdt stands for Current Date Time
       def cdt = new DateTime()
       // mjdm stands for Merged Job Data Map
       def mjdm = context.getMergedJobDataMap()
       // We got the input data
       def server = mjdm.server
       def jobmanager = mjdm.jobmanager
       def parameters = mjdm.parameters
       def function = mjdm.function
       def crnexpr = mjdm.crnexpr
       def lock = mjdm.lock
       def sync = mjdm.sync

       if (lock) { 
          if (util.Util.lockexists(server)) { 
             def minimumgap = util.Util.st2millis(util.Util.getproperty(config.Config.timeforremovinglock))
             if (util.Util.lockfilehasexpired(server,minimumgap)) {
                def booleantmp = util.Util.touchlock(server)
                println "[${new DateTime().toLocalTime()}/${server}] Was the lock file modified? ${booleantmp}"
                // This line has sense when only job at the time.
                util.jsch.Util.execremotecommand(config.Config.username, server, config.Config.cleanqstat)
             } else { 
                println "[${new DateTime().toLocalTime()}/${server}] Failed! ${server}/${cdt.toLocalDate()}/${function}-${cdt.getMillis()}.log.err" 
                report = 
"""Failure | Lock hasn't expired
   Time: ${cdt.toLocalTime()}
   Server: ${server}
   Function: ${function}
   Parameters: ${parameters}
"""
                util.Util.writelog(report,"${server}/${cdt.toLocalDate()}/${function}-${cdt.getMillis()}.log.err")
                return
             }
          } else {
            util.Util.createlock(server)
          }
       }

       if (!sync) { // I need to run globus-job-submit (Asynchronous call)
         // seconds variable indicates the frequency which the job status will be queried.
         def seconds = util.grid.Util.dryrunaveragetime(server,jobmanager,config.Config.iterations)/1000
         if (seconds == 0) {
            cdt = new DateTime().getMillis()
            report += "FAILED | Dry run average time equals ZERO\n"
            report += "Time: ${cdt.toLocalTime()}\n"
            report += "Server: ${server}\n"
            report += "Function: ${function}\n"
            report += "Parameters: ${parameters}\n"
            util.Util.writelog(report,"${server}/${cdt.toLocalDate()}/${function}-${cdt.getMillis()}.log.err")
         }
         seconds = util.Util.maximum(config.Config.minimumthreshold, (int)(seconds + 1))
         def cronexpression = new CronExpression(util.quartz.Util.createcronexpression("NOW+${seconds}s"))
         cdt = new DateTime().getMillis()
         def trigger = new CronTrigger("${config.Config.jobnameprefix}_${cdt}","${config.Config.jobgroupprefix}_${cdt}")
         // Passing "all" parameters from the invoker trigger 
         trigger.setJobName(config.Config.jobstatusname)
         trigger.setJobGroup(config.Config.jobgroup)
         trigger.jobDataMap.server = server
         trigger.jobDataMap.jobmanager = jobmanager
         trigger.jobDataMap.function = function
         trigger.jobDataMap.parameters = parameters
         trigger.jobDataMap.crnexpr = crnexpr
         trigger.jobDataMap.lock = lock
         trigger.jobDataMap.sync = sync
         trigger.jobDataMap.cronexpression = cronexpression
         trigger.jobDataMap."${config.Config.UNSUBMITTED}" = cdt
         trigger.jobDataMap.status = config.Config.UNSUBMITTED
         trigger.jobDataMap.minimumthreshold = seconds
         if (mjdm.estimates != null) {
            println "[GlobusjobinstanceJob - ${server}/${cdt.toLocalTime()}] Passing estimated values"
            trigger.jobDataMap.estimates = mjdm.estimates
         }
         def output = util.Util.executegetoutput("${globushome}/bin/${globusjobsubmit} ${server}/jobmanager-${jobmanager} ${parameters}")
         trigger.jobDataMap.url = output
         println "[${new DateTime().toLocalTime()}/${server}] [${crnexpr} - ${cronexpression}] ${seconds} ${output}"
         if (!CronExpression.isValidExpression(crnexpr)) {
            println "[${new DateTime().toLocalTime()}] =>Trigger removed [${server}] (SYNC)"
            util.quartz.Util.removetrigger(context)
         }
         trigger.setCronExpression( new CronExpression(util.quartz.Util.createcronexpression("NOW+${seconds}s")))
         quartzScheduler.scheduleJob(trigger)
         return 
       } // End block of asynchronous call
       // Synchronous call
       println "[${new DateTime().toLocalTime()}/${server}] ${globushome}/bin/${globusjobrun} ${server}/jobmanager-${jobmanager} ${parameters}"
       def start = new DateTime()
       def output = util.Util.executegetoutput("${globushome}/bin/${globusjobrun} ${server}/jobmanager-${jobmanager} ${parameters}",true)
       def stop = new DateTime()
       def period = util.joda.Util.getseconds(start,stop)
       def resultline = util.Util.getlinestartingwith(output,"\n",outputremoteexecution)
       computenode = resultline.split(" ")[computenodefield]
       elapsedtime = resultline.split(" ")[elapsedtimefield]

       cdt = new DateTime()
       report = """DONE
Date: ${cdt}
Server: ${server}
Jobmanager: ${jobmanager}
Parameters: ${parameters}
Elapsed Time in compute node ${computenode}: ${elapsedtime}
Elapsed Time Local: ${period}
"""
      util.Util.writelog(report,"${server}/${cdt.toLocalDate()}/${function}-${cdt.getMillis()}.log")

      if (lock) util.Util.deletelock(server)
      if (CronExpression.isValidExpression(crnexpr)) 
          println "[${new DateTime().toLocalTime()}/${server}] => It will be launched again: ${context.trigger.nextFireTime}"
      else {
         util.quartz.Util.removetrigger(context)
         println "[${new DateTime().toLocalTime()}/${server}] Trigger unscheduled!"
      }
    }
}
