import org.joda.time.DateTime
import org.joda.time.Period
import org.quartz.CronExpression
import org.quartz.Trigger
import org.quartz.CronTrigger

class GlobusjobinstanceJob{
   static triggers =  { }
   def quartzScheduler
   def resourcemanagerService
   def group = config.Config.jobgroup
   def globushome = config.Config.globushome
   
   //def globusjobrun = "globus-job-run"
   def globusjobrun = config.Config.globusjobrun
   def globusjobsubmit = config.Config.globusjobsubmit

   def computenodefield = config.Config.computenodefield
   def elapsedtimefield = config.Config.elapsedtimefield
   def outputremoteexecution = config.Config.outputremoteexecution

    def execute(context) {
       println "[GlobusjobinstanceJob - execute ${util.joda.Util.datetime()}]"
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
       def submittedtime = mjdm.submittedtime

       // ---> TASK !!!
       def task = Task.findBySubmittedtime(submittedtime)

       println "[GlobusjobinstanceJob - execute]\t ${server} ${jobmanager} ${function} [<${parameters}>]"

       if (lock) { 
          println "[GlobusjobinstanceJob - execute]\t Lock required"
          if (util.Util.lockexists(server)) { 
             println "[GlobusjobinstanceJob - execute]\t Lock exists"
             def minimumgap = util.Util.st2millis(util.Util.getproperty(config.Config.timeforremovinglock))
             if (util.Util.lockfilehasexpired(server,minimumgap)) {
                def booleantmp = util.Util.touchlock(server)
                //println "[${new DateTime().toLocalTime()}/${server}] Was the lock file modified? ${booleantmp}"
                println "[GlobusjobinstanceJob - execute]\t [${server}/${parameters}] Was the lock file modified? ${booleantmp}"
                // This line has sense when only job at the time.
                util.jsch.Util.execremotecommand(config.Config.username, server, config.Config.cleanqstat)
             } else { 
                println "[GlobusjobinstanceJob - execute]\t [${server}/${parameters}] Failed! ${server}/${cdt.toLocalDate()}/${function}-${cdt.getMillis()}.log.err" 
                report = 
"""Failure | Lock hasn't expired
   Time: ${cdt.toLocalTime()}
   Server: ${server}
   Function: ${function}
   Parameters: ${parameters}
"""
                util.Util.writelog(report,"${server}/${cdt.toLocalDate()}/${function}-${cdt.getMillis()}.log.err")
                //print "[GlobusjobinstaceJob - execute]\t [${server}/${parameters}] Trigger removed?"
                //def _flag = util.quartz.Util.removetrigger(context)
                //println " ${_flag}"
                println "[GlobusjobinstanceJob - execute ${util.joda.Util.datetime()}] [${server}/${parameters}] Exiting with error"
                return
             }
          } else {
            println "[GlobusjobinstanceJob - execute] [${server}/${parameters}] Creating lock"
            util.Util.createlock(server)
          }
       }

       if (!sync) { // I need to run globus-job-submit (Asynchronous call)
         def status = config.Config.UNSUBMITTED
         println "[GlobusjobinstanceJob - execute]\t [${server}/${parameters}] Asynchronous execution"
         // seconds variable indicates the frequency which the job status will be queried.
         // Most of the time, the value obtained is approximately two seconds. :-S
         def seconds = util.grid.Util.dryrunaveragetime(server,jobmanager,config.Config.iterations)/1000
         if (seconds == 0) {
            println "[GlobusjobinstanceJob - execute]\t [${server}/${parameters}] Seconds equals 0. No connection"
            cdt = new DateTime().getMillis()
            report += "FAILED | Dry run average time equals ZERO\n"
            report += "Time: ${cdt.toLocalTime()}\n"
            report += "Server: ${server}\n"
            report += "Function: ${function}\n"
            report += "Parameters: ${parameters}\n"
            util.Util.writelog(report,"${server}/${cdt.toLocalDate()}/${function}-${cdt.getMillis()}.log.err")
            println "[GlobusjobinstanceJob - execute ${util.joda.Util.datetime()}] [${server}/${parameters}] Exit on error"
            // Record the error in the database 
            task.state = 'FAILED'
            task.exitstatus = 'FAILED'

            def ar = Accountingresource.findByInitialtime(submittedtime)
            ar.status = 1
            ar.endtime = new DateTime().toDate()

            resourcemanagerService.releasenode(server)

            task.save()
            ar.save()

            if (!CronExpression.isValidExpression(crnexpr)) {
               println "[${new DateTime().toLocalTime()}] =>Trigger removed [${server}] (SYNC)"
               util.quartz.Util.removetrigger(context)
            }

            return
         }
         //
         // ----------------> PENDING! <----------------
         // Change '1' by a value retrieved from a config file.
         // For example, application.properties
         // Check 'GlobusjobstatusJob'
         //
         def threshold = util.Util.st2millis(util.Util.getproperty(config.Config."${status}" + config.Config."${config.Config.THRESHOLD}")) / 1000
         seconds = util.Util.maximum(config.Config.minimumthreshold, (int) threshold )
         println "[GlobusjobinstanceJob - execute]\t [${server}/${parameters}] Suggested monitor frequency in seconds ${seconds}"
         def cronexpression = new CronExpression(util.quartz.Util.createcronexpression("NOW+${seconds}s"))
         cdt = new DateTime().getMillis()
         //def trigger = new CronTrigger("${config.Config.jobnameprefix}_${cdt}","${config.Config.jobgroupprefix}_${cdt}")
         def trigger = new CronTrigger()
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
         trigger.jobDataMap.submittedtime = submittedtime
         //
         // Updating information to 'task' table
         //
         task.unsubmitted = cdt
         task.lastvisit = new DateTime(cdt).toDate()
         task.state = config.Config.UNSUBMITTED

         trigger.jobDataMap.status = config.Config.UNSUBMITTED
         trigger.jobDataMap.minimumthreshold = seconds
         if (mjdm.estimates != null) {
            println "[GlobusjobinstanceJob - execute]\t [${server}/${parameters}] Passing estimated values"
            trigger.jobDataMap.estimates = mjdm.estimates
         }
         def output = util.Util.executegetoutput("${globushome}/bin/${globusjobsubmit} ${server}/jobmanager-${jobmanager} ${parameters}")
         trigger.jobDataMap.url = output
         println "[${new DateTime().toLocalTime()}/${server}] [${crnexpr} - ${cronexpression}] ${seconds} ${output}"
         if (!CronExpression.isValidExpression(crnexpr)) {
            println "[${new DateTime().toLocalTime()}] =>Trigger removed [${server}] (SYNC)"
            util.quartz.Util.removetrigger(context)
         }
         cdt = new DateTime().getMillis()
         trigger.name = "${config.Config.jobnameprefix}_${cdt}" 
         trigger.group = "${config.Config.jobgroupprefix}_${cdt}"
         trigger.setCronExpression( new CronExpression(util.quartz.Util.createcronexpression("NOW+${seconds}s")))
         try {
            def _date = quartzScheduler.scheduleJob(trigger)
            println "[GlobusjobinstanceJob - execute]\t[${server}/${parameters}] The ${trigger.name}/${trigger.group} will be launched on ${_date}"
            task.nextvisit = _date
            print "[GlobusjobinstanceJob - execute]\t [${server}/${parameters}] Saving data to DB... "
            if (task.save() != null) {
               println "saved"
            } else {
               println "doesn't saved"
            }
         } catch (org.quartz.SchedulerException e) {
            println "[GlobusjobinstanceJob - execute]\t[${server}/${parameters}]Exception scheduling the trigger ${trigger.name}/${trigger.group}"
            task.state = 'FAILED'
            task.exitstatus = 'FAILED'
            print "[GlobusjobinstanceJob - execute]\t [${server}/${parameters}] Saving data exception to DB... "
            if (task.save() != null) {
               println "saved"
            } else {
               println "doesn't saved"
            }
            // it's necessary to release the resource because there won't be any monitoring over this task.
            // However, it's highly probable that the task keep running on the remote server.
            // Think of it as an orphaned 'meta-process'
            resourcemanagerService.releasenode(server)
            // Though, the local scheduler fails during the process to schedule the task monitor instance
            // nothing can be said about how the task will be performed in the remote resource.
            // Then, the accouting resource instance can not be modified.
         }
         println "[GlobusjobinstanceJob - execute ${util.joda.Util.datetime()}] [${server}/${parameters}] DONE"
         return 
       } // End block of asynchronous call
       // Synchronous call
       println "[GlobusjobinstanceJob - execute]\t ${globushome}/bin/${globusjobrun} ${server}/jobmanager-${jobmanager} ${parameters}"
       def start = new DateTime()
       def output = util.Util.executegetoutput("${globushome}/bin/${globusjobrun} ${server}/jobmanager-${jobmanager} ${parameters}",true)
       def stop = new DateTime()
       println "[GlobusjobinstanceJob - execute]\t ${globushome}/bin/${globusjobrun} ${server}/jobmanager-${jobmanager} ${parameters} -> DONE"
       def period = util.joda.Util.getseconds(start,stop)
       def resultline = util.Util.getlinestartingwith(output,"\n",outputremoteexecution)
       // The following lines are especific of the Cesar's application
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

      if (lock) {
         util.Util.deletelock(server)
         println "[GlobusjobinstanceJob - execute]\t[${server}/${parameters}] Deleting lock file"
      }
      if (CronExpression.isValidExpression(crnexpr)) {
          println "[GlobusjobinstanceJob - execute]\t[${server}/${parameters}] => It will be launched again: ${context.trigger.nextFireTime}"
      } else {
         util.quartz.Util.removetrigger(context)
         println "[GlobusjobinstanceJob - execute]\t[${server}/${parameters}] Trigger unscheduled!"
      }
      println "[GlobusjobinstanceJob - execute ${util.joda.Util.datetime()}] [${server}/${parameters}] DONE!"
    }
}
