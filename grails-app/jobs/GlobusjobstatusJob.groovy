import org.joda.time.DateTime
import org.quartz.CronTrigger
import org.quartz.Trigger
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobExecutionContext



class GlobusjobstatusJob
{
   static triggers = { }
   def group = config.Config.jobgroup
   def globusjobsubmit = config.Config.globusjobsubmit
   def globushome = config.Config.globushome
   def quartzScheduler
   def mailService

   def execute(context) {
      def _flag 
      def mjdm = context.getMergedJobDataMap()
      def oldtrigger = context.getTrigger()
      def previousstatus = mjdm.status
      def url = mjdm.url
      def server = mjdm.server
      //println "[GlobusjobstatusJob - execute ${util.joda.Util.datetime()}] [${server}/${mjdm.parameters}]"

      def report = ""
      def output = ""

      Task _task

      def cdt = new DateTime()
      //println "[GlobusjobstatusJob - execute]\t[${server}] Executing globusjobstatus to [${url}]"
      def status = util.Util.executegetoutput("${globushome}/bin/${config.Config.globusjobstatus} ${url}")
      status = status.split("\n")[0]

      if (status == "" || status == null || status == config.Config.ERROR) { // Error getting the status
         output = totalclean(context,"[ ERROR - ${new DateTime().toLocalTime()}/${server}] Abnormal status -> ${status}")
         report = "FAILED | No output for checking status\n"
         report += "Current status: ${mjdm.status}\n"
         report += "Time: ${cdt.toLocalTime()}\n"
         report += "Server: ${server}\n"
         report += "Function: ${mjdm.function}\n"
         report += "Parameters: ${mjdm.parameters}\n"
         report += "Command output (${config.Config.globusjobcancel}): ${output}\n"
         util.Util.writelog(report,"${mjdm.server}/${cdt.toLocalDate()}/${mjdm.function}-${cdt.getMillis()}.log.err")
         println "[GlobusjobstatusJob - execute]\t[${server}] Status ERROR = ${status}"
         _flag = util.quartz.Util.removetrigger(context)
         println "[GlobusjobstatusJob - execute]\t[${server}] Trigger ${context.trigger.name} removed? ${_flag}"
         // 
         // Saving the event info into the DB
         //
         _task = Task.findByUnsubmitted(mjdm.UNSUBMITTED)
         _task.exitstatus = config.Config.FAILED
         _task.output = "Error getting the task status"
         print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Updating record with id: ${_task.unsubmitted}... "
         if (_task.save() != null) {
            println "saved"
         } else {
            println "DIDN'T save"
         }
         println "[GlobusjobstatusJob - execute ${util.joda.Util.datetime()}] [${server}] Exiting by error on 'status'"
         return 
      }
      if (previousstatus == status) { // The status hasn't changed
         if (mjdm.estimates != null) {
            //println "[GlobusjobstatusJob - ${server}/${cdt.toLocalTime()}] status -> ${status}"
            if (mjdm.estimates["${status.toUpperCase()}"] + mjdm."${status}" < new DateTime().getMillis()) {
               println "[GlobusjobstatusJob - execute]\t[${server}] Prsched-ESTIMATED failed"
            }
         }
         def maxwaitingtime = util.Util.st2millis(util.Util.getproperty(config.Config."${status}${config.Config.MAXTIME}"))
         if ( (maxwaitingtime + mjdm."${status}") < new DateTime().getMillis() ) { // The waiting time was exceeded
            output = totalclean(context,"[ ERROR - ${new DateTime().toLocalTime()}/${server}] job with ${mjdm.parameters} exceeded its deadline")
            report = "FAILED | status ${status} exceeded its deadline\n"
            report += "Current status: ${mjdm.status}\n"
            report += "Time: ${cdt.toLocalTime()}\n"
            report += "Server: ${server}\n"
            report += "Function: ${mjdm.function}\n"
            report += "Parameters: ${mjdm.parameters}\n"
            report += "Command output (${config.Config.globusjobcancel}): ${output}\n"
            util.Util.writelog(report,"${mjdm.server}/${cdt.toLocalDate()}/${mjdm.function}-${cdt.getMillis()}.log.err")
            println "[GlobusjobstatusJob - execute]\t[${server}] Exceeded time"
            _flag = util.quartz.Util.removetrigger(context)
            println "[GlobusjobstatusJob - execute]\t[${server}] Trigger ${context.trigger.name} removed? ${_flag}"
            // Report the estimated failed!
            // 
            // Saving the event info into the DB
            //
            _task = Task.findByUnsubmitted(mjdm.UNSUBMITTED)
            _task.exitstatus = config.Config.FAILED
            _task.output = "Time exhausted"
            print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Updating record with id: ${_task.unsubmitted}... "
            if (_task.save() != null) {
               println "saved"
            } else {
               println "DIDN'T save"
            }
            println "[GlobusjobstatusJob - execute] [${server}] Exiting due to exhausted time"
            return 
         }
         // A new trigger is created!
         //def trigger = new CronTrigger("${config.Config.jobnameprefix}_${server}-${cdt.getMillis()}","${config.Config.jobgroupprefix}_${server}-${cdt.getMillis()}")
         def trigger = new CronTrigger()
         // Populate the trigger
         trigger.setJobName(config.Config.jobstatusname)
         trigger.setJobGroup(config.Config.jobgroup)
         trigger.jobDataMap.server = mjdm.server
         trigger.jobDataMap.jobmanager = mjdm.jobmanager
         trigger.jobDataMap.function = mjdm.function
         trigger.jobDataMap.parameters = mjdm.parameters
         trigger.jobDataMap.lock = mjdm.lock
         trigger.jobDataMap.status = status
         trigger.jobDataMap."${status}" = mjdm."${status}" 
         trigger.jobDataMap.minimumthreshold = mjdm.minimumthreshold
         if (status == config.Config.PENDING) {
            trigger.jobDataMap."${config.Config.UNSUBMITTED}" = mjdm."${config.Config.UNSUBMITTED}"
         } else if (status == config.Config.ACTIVE) {
            trigger.jobDataMap."${config.Config.UNSUBMITTED}" = mjdm."${config.Config.UNSUBMITTED}"
            trigger.jobDataMap."${config.Config.PENDING}" = mjdm."${config.Config.PENDING}"
         }
         trigger.jobDataMap.url = mjdm.url
         if (mjdm.estimates != null) {
            println "[GlobusjobstatusJob - execute] [${server}] transfering estimates"
            trigger.jobDataMap.estimates = mjdm.estimates
         }
         if (!scheduleJobWithOldTriggerName(context,trigger)) { // Scheduling failed
            _task = Task.findByUnsubmitted(mjdm.UNSUBMITTED)
            _task.exitstatus = config.Config.FAILED
            _task.output = "Failing scheduling a new quartz trigger"
            print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Updating record with id: ${_task.unsubmitted}... "
            if (_task.save() != null) {
               println "saved"
            } else {
               println "DIDN'T save"
            }
            println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Scheduling failed "
            println "[GlobusjobstatusJob - execute ${util.joda.Util.datetime()}]\t[${server}/${mjdm.parameters}] Exit with ERROR"
            return
         }
         return // The status did not change
      } 
      // Status changed!
      //println "[${new DateTime().toLocalTime()}/${server}] [${oldtrigger.name}-${context.jobDetail.fullName}] Status change ${previousstatus} -> ${status}"
      println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Status changed ${previousstatus} -> ${status}"
      mjdm."${status}" = cdt.getMillis()
      mjdm.status = status
      //
      // The status changed, then the DB info must be
      // updated
      //
      _task = Task.findByUnsubmitted(mjdm.UNSUBMITTED)
      _task.state = status
      _task."${status.toLowerCase()}" = cdt.getMillis()
      if (status == config.Config.DONE || status == config.Config.FAILED) {
         // Updating record values
         _task.exitstatus = status
         println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Task finalized"
         _flag = util.quartz.Util.removetrigger(context)
         println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Trigger ${context.trigger.name} removed? ${_flag} "
         if (mjdm.lock) {
            println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Removing lock file"
            util.Util.deletelock(mjdm.server)
         }
         def keys = ["server","jobmanager","function","parameters"]
         if (status == config.Config.DONE) {
            def start = new DateTime()
            try { 
               output = util.Util.executegetoutput("${globushome}/bin/${config.Config.globusjobgetoutput} ${url}",true,util.Util.st2millis(util.Util.getproperty(config.Config.globusjobgetoutputMAXTIME)))
               // Updating record values
               _task.output = output.substring(0,255)
            } catch(Exception e) {
               output = "Time exhausted - No Output"
            }
            def stop = new DateTime()
            mjdm.getoutputtime = stop.getMillis()
            keys += [config.Config.UNSUBMITTED, config.Config.PENDING, config.Config.ACTIVE, "getoutputtime"]
         } else { // status == config.Config.FAILED
               keys += util.grid.Util.previousstates(previousstatus)
               keys += previousstatus
         }
         keys += status
         report = "${status} \n${util.Util.createreport(mjdm as HashMap,keys)} \n${output}"
         util.Util.writelog(report,"${mjdm.server}/${cdt.toLocalDate()}/${mjdm.function}-${cdt.getMillis()}.log")

         println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Sending notification via e-mail"
         def contacts = Contactinfo.list() 
         contacts.each { contact ->
            mailService.sendMail {
               from "gridjobs@gmail.com"
               to contact.email
               subject 'Report from gridjobs framework'
               body report
            }
         }
         print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Updating record with id ${_task.unsubmitted}... "
         if (_task.save() != null) {
            println "saved"
         } else {
            println " DIDN'T save"
         }
         // Report about how good the estimation was
         println "[GlobusjobstatusJob - execute ${util.joda.Util.datetime()}][${server}/${mjdm.parameters}] Task DONE"
         return 
      } else { // The job still runs
         //
         // Saving the record from data defined above
         //
         print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Updating record with id: ${_task.unsubmitted}... "
         if (_task.save() != null) {
            println "saved"
         } else {
            println " DIDN'T save"
         }
         println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Creating a new trigger"
         def trigger = new CronTrigger()
         // Passing "all" parameters from the invoker trigger 
         trigger.setJobName(config.Config.jobstatusname)
         trigger.setJobGroup(config.Config.jobgroup)
         trigger.jobDataMap.server = mjdm.server
         trigger.jobDataMap.jobmanager = mjdm.jobmanager
         trigger.jobDataMap.function = mjdm.function
         trigger.jobDataMap.parameters = mjdm.parameters
         trigger.jobDataMap.lock = mjdm.lock
         trigger.jobDataMap.status = status
         trigger.jobDataMap.minimumthreshold = mjdm.minimumthreshold
         trigger.jobDataMap."${status}" = mjdm."${status}" 
         if (status == config.Config.PENDING) {
            trigger.jobDataMap."${config.Config.UNSUBMITTED}" = mjdm."${config.Config.UNSUBMITTED}"
         } else if (status == config.Config.ACTIVE) {
            trigger.jobDataMap."${config.Config.UNSUBMITTED}" = mjdm."${config.Config.UNSUBMITTED}"
            trigger.jobDataMap."${config.Config.PENDING}" = mjdm."${config.Config.PENDING}"
         }
         trigger.jobDataMap.url = mjdm.url

         if (mjdm.estimates != null) {
            println "[GlobusjobstatusJob - ${server}/${cdt.toLocalTime()}] transfering estimates"
            trigger.jobDataMap.estimates = mjdm.estimates
         }

         if (!scheduleJobWithOldTriggerName(context,trigger)) { // Scheduling failed
            _task = Task.findByUnsubmitted(mjdm.UNSUBMITTED)
            _task.exitstatus = config.Config.FAILED
            _task.output = "Failing scheduling a new quartz trigger"
            print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Updating record with id: ${_task.unsubmitted}... "
            if (_task.save() != null) {
               println "saved"
            } else {
               println "DIDN'T save"
            }
            println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters}] Scheduling failed "
            println "[GlobusjobstatusJob - execute ${util.joda.Util.datetime()}]\t[${server}/${mjdm.parameters}] Exit with ERROR"
            return
         }
         return
      }
   }

   /**
   -=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-
   */
   String totalclean(JobExecutionContext context,String message) {
      def mjdm = context.getMergedJobDataMap()
      def output = ""
      def server = mjdm.server
      def url = mjdm.url

      println message
      util.quartz.Util.removetrigger(context)
      output = "Cancel\n"
      output += util.Util.executegetoutput("${globushome}/bin/${config.Config.globusjobcancel} -q ${url}",true)
      output += "\nClean\n"
      output += util.Util.executegetoutput("${globushome}/bin/${config.Config.globusjobclean} -q ${url}",true)
      try {
         output = output.substring(0,output.size() - 1)
      } catch (java.lang.StringIndexOutOfBoundsException e) {
         println "\t[ ERROR - ${new DateTime().toLocalTime()}/${server}] StringIndexException because output -> |${output}|"
      }
      if (mjdm.lock) {
         util.Util.deletelock(mjdm.server)
         try { 
            util.jsch.Util.execremotecommand(config.Config.username, server, config.Config.cleanqstat)
         } catch (Exception e1) {
            println "\t[ ERROR - ${new DateTime().toLocalTime()}/${server}] JSch exception with ${server} and ${config.Config.username} executing ${config.Config.cleanqstat}"
         }
      }

      return output
   }

   /**
   -=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-=*=-
   */
   boolean scheduleJobWithTriggerNewName(JobExecutionContext context,CronTrigger trigger) {
      def cdt
      def output = ""
      def report = ""
      def mjdm = context.getMergedJobDataMap()
      def server = mjdm.server
      def MAXTRIES = 100
      def minimumthreshold = mjdm.minimumthreshold

      trigger.setJobName(config.Config.jobstatusname)
      trigger.setJobGroup(config.Config.jobgroup)
      def flag = true 
      def counter = 0
      def cronexpression 
      //println "[GlobusjobstatusJob - scheduleJobWithTriggerNewName]\tTrying to schedule a new trigger"
      while (flag) {
         try { 
            cdt = new DateTime() 
            trigger.setName("${config.Config.jobnameprefix}_${server}_${util.Util.uniqueint()}-${cdt.getMillis()}")
            trigger.setGroup("${config.Config.jobgroupprefix}_${server}_${util.Util.uniqueint()}-${cdt.getMillis()}")
            cronexpression = util.quartz.Util.createcronexpression("NOW+${minimumthreshold}s")
            trigger.setCronExpression(cronexpression)
            quartzScheduler.scheduleJob(trigger)
            flag = false
         } catch (Exception e) {
            println "[GlobusjobstatusJob - scheduleJobWithTriggerNewName]\tSchedule try, failed! (${counter}/${MAXTRIES})"
            counter++
            if (counter >= MAXTRIES) break
         }
      }
      if (counter >= MAXTRIES) {
         println "[GlobusjobstatusJob - scheduleJobWithTriggerNewName]\t ${MAXTRIES} tries and it wasn't possible to create a unique trigger identifier"
         output = totalclean(context,"[${new DateTime().toLocalTime()}/${server}] Quartz Scheduler exception")
         report = "FAILED | Exception\n"
         report += "Cause: ${e.getCause()}\n"
         report += "Localized Message: ${e.getLocalizedMessage()}\n"
         report += "Message: ${e.getMessage()}\n"
         report += "Stack Trace: ${e.getStackTrace()}\n"
         report += "Command output (globus-job-clean): ${output}\n"
         util.Util.writelog(report,"${mjdm.server}/${cdt.toLocalDate()}/${mjdm.function}-${cdt.getMillis()}.log.err")
         return false
      }
      return true
   }

   boolean scheduleJobWithOldTriggerName(JobExecutionContext context,CronTrigger trigger) {
      def cdt
      def output = ""
      def report = ""
      def mjdm = context.getMergedJobDataMap()
      def server = mjdm.server
      def minimumthreshold = mjdm.minimumthreshold

      trigger.setJobName(config.Config.jobstatusname)
      trigger.setJobGroup(config.Config.jobgroup)
      trigger.setName(context.trigger.name)
      trigger.setGroup(context.trigger.group)
      def cronexpression 
      //println "[GlobusjobstatusJob - scheduleJobWithOldTriggerName]\tTrying to schedule a new trigger"
      if (util.quartz.Util.removetrigger(context)) {
         //println "[GlobusjobstatusJob - scheduleJobWithOldTriggerName]\tTrigger ${trigger.name} removed!"
      } else {
         println "[GlobusjobstatusJob - scheduleJobWithOldTriggerName]\tTrigger ${trigger.name} DOESN'T removed!"
      }
      cronexpression = util.quartz.Util.createcronexpression("NOW+${minimumthreshold}s")
      trigger.setCronExpression(cronexpression)
      try { 
         quartzScheduler.scheduleJob(trigger)
         //println "[GlobusjobstatusJob - scheduleJobWithOldTriggerName]\tTrigger ${trigger.name} created!"
      } catch (Exception e) {
         cdt = new DateTime() 
         output = totalclean(context,"[${new DateTime().toLocalTime()}/${server}] Quartz Scheduler exception")
         report = "FAILED | Exception\n"
         report += "Cause: ${e.getCause()}\n"
         report += "Localized Message: ${e.getLocalizedMessage()}\n"
         report += "Message: ${e.getMessage()}\n"
         report += "Stack Trace: ${e.getStackTrace()}\n"
         report += "Command output (globus-job-clean): ${output}\n"
         util.Util.writelog(report,"${mjdm.server}/${cdt.toLocalDate()}/${mjdm.function}-${cdt.getMillis()}.log.err")
         return false
      }
      return true
   }
}
