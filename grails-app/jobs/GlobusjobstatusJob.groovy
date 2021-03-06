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
   def resourcemanagerService
   def mailService

   def execute(context) {
      def _flag 
      def mjdm = context.getMergedJobDataMap()
      def oldtrigger = context.getTrigger()
      def previousstatus = mjdm.status
      def url = mjdm.url
      def server = mjdm.server
      def submittedtime = mjdm.submittedtime
      //println "[GlobusjobstatusJob - execute ${util.joda.Util.datetime()}] [${server}/${mjdm.parameters}]"

      def report = ""
      def output = ""

      //Task task = Task.findBySubmittedtime(submittedtime)
      def task = Task.findBySubmittedtime(submittedtime)
      def ar = Accountingresource.findByInitialtime(submittedtime)

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
         println "[GlobusjobstatusJob - execute]\t[${server} - ${submittedtime}] Status ERROR = ${status}"
         _flag = util.quartz.Util.removetrigger(context)
         println "[GlobusjobstatusJob - execute]\t[${server} - ${submittedtime}] Trigger ${context.trigger.name} removed? ${_flag}"
         // 
         // Saving the event info into the DB
         //
         task.exitstatus = config.Config.FAILED
         task.output = "Error getting the task status"
         task.lastvisit = cdt.toDate()

         ar.endtime = task.lastvisit
         if (status == "" || status == null) 
            ar.status = 1
         else {
            ar.status = -1 
            def rc = Resourcecharacteristics.findByGridresource(ar.gridresource)
            ar.tasksassigned = rc.inuse
         }

         // Making persistent the new data
         print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Updating 'Task' record with id: ${task.submittedtime}... "
         if (task.save() != null) {
            println "saved"
         } else {
            println "DIDN'T save"
         }
         print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Updating 'Accoutingresource' record with id: ${ar.initialtime}... "
         if (ar.save() != null) {
            println "saved"
         } else {
            println "DIDN'T save"
         }
         println "[GlobusjobstatusJob - execute - ${submittedtime}] Releasing the resource on server ${server}"
         resourcemanagerService.releasenode(server)
         cancelclean(context)
         // notification message
         println "[GlobusjobstatusJob - execute ${util.joda.Util.datetime()}] [${server} - ${submittedtime}] Exiting by error on 'status'"
         return 
      }
      if (previousstatus == status) { // The status hasn't changed
         if (mjdm.estimates != null) {
            //println "[GlobusjobstatusJob - ${server}/${cdt.toLocalTime()}] status -> ${status}"
            if (mjdm.estimates["${status.toUpperCase()}"] + mjdm."${status}" < new DateTime().getMillis()) {
               println "[GlobusjobstatusJob - execute]\t[${server} - ${submittedtime}] Prsched-ESTIMATED failed"
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
            println "[GlobusjobstatusJob - execute]\t[${server} - ${submittedtime}] Exceeded time"
            _flag = util.quartz.Util.removetrigger(context)
            println "[GlobusjobstatusJob - execute]\t[${server} - ${submittedtime}] Trigger ${context.trigger.name} removed? ${_flag}"
            // Report the estimated failed!
            // 
            // Saving the event info into the DB
            //
            task.exitstatus = config.Config.FAILED
            task.output = "Time exhausted ${status}"
            task.lastvisit = cdt.toDate()
            print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Updating 'Task' record with id: ${task.unsubmitted}... "
            if (task.save() != null) {
               println "saved"
            } else {
               println "DIDN'T save"
            }

            ar.endtime = cdt.toDate()
            ar.status = -1  
            def rc = Resourcecharacteristics.findByGridresource(ar.gridresource)
            ar.tasksassigned = rc.inuse
            print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Updating 'Accountingresource' record with id: ${task.unsubmitted}... "
            if (ar.save() != null) {
               println "saved"
            } else {
               println "DIDN'T save"
            }

            println "[GlobusjobstatusJob - execute - ${submittedtime}] Releasing the resource on server ${server}"
            resourcemanagerService.releasenode(server)
            cancelclean(context)
            println "[GlobusjobstatusJob - execute] [${server} - ${submittedtime}] Exiting due to exhausted time"
            return 
         }
         // A new trigger is created!
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
         trigger.jobDataMap.submittedtime = mjdm.submittedtime

         task.lastvisit = cdt.toDate()
         if (task.save() == null) {
            println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] ERROR updating 'Task' record with id: ${task.submittedtime}... "
         }
         if (status == config.Config.PENDING) {
            trigger.jobDataMap."${config.Config.UNSUBMITTED}" = mjdm."${config.Config.UNSUBMITTED}"
         } else if (status == config.Config.ACTIVE) {
            trigger.jobDataMap."${config.Config.UNSUBMITTED}" = mjdm."${config.Config.UNSUBMITTED}"
            trigger.jobDataMap."${config.Config.PENDING}" = mjdm."${config.Config.PENDING}"
         }
         trigger.jobDataMap.url = mjdm.url
         if (mjdm.estimates != null) {
            println "[GlobusjobstatusJob - execute] [${server} - ${submittedtime}] transfering estimates"
            trigger.jobDataMap.estimates = mjdm.estimates
         }
         if (!scheduleJobWithOldTriggerName(context,trigger)) { // Scheduling failed
            task.exitstatus = config.Config.FAILED
            task.output = "Failing scheduling a new quartz trigger"
            task.lastvisit = cdt.toDate()
            print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Updating 'Task' record with id: ${task.submittedtime}... "
            if (task.save() != null) {
               println "saved"
            } else {
               println "DIDN'T save"
            }
            println "[GlobusjobstatusJob - execute - ${submittedtime}] Releasing the resource on server ${server}"
            resourcemanagerService.releasenode(server)
            cancelclean(context)
            println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Scheduling failed "
            println "[GlobusjobstatusJob - execute ${util.joda.Util.datetime()}]\t[${server}/${mjdm.parameters} - ${submittedtime}] Exit with ERROR"
            return
         }
         return // The status did not change
      } 
      // Status changed!
      def threshold = util.Util.st2millis(util.Util.getproperty(config.Config."${status}" + config.Config."${config.Config.THRESHOLD}")) / 1000
      def seconds = util.Util.maximum(config.Config.minimumthreshold, (int) threshold )
      println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Status changed ${previousstatus} -> ${status} (${seconds}s)"
      mjdm."${status}" = cdt.getMillis()
      mjdm.status = status
      //
      // The status changed, then the DB info must be
      // updated
      //
      task.state = status
      task."${status.toLowerCase()}" = cdt.getMillis()
      task.lastvisit = cdt.toDate()
      if (status == config.Config.DONE || status == config.Config.FAILED) {
         //def ar = Accountingresource.findByInitialtime(submittedtime)
         ar.endtime = cdt.toDate()
         // Updating record values
         task.exitstatus = status
         println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Task finalized"
         _flag = util.quartz.Util.removetrigger(context)
         println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Trigger ${context.trigger.name} removed? ${_flag} "
         if (mjdm.lock) {
            println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Removing lock file"
            util.Util.deletelock(mjdm.server)
         }
         def keys = ["server","jobmanager","function","parameters"]
        
         // ---------------------<><><><><><><><><><><><><><>---------------------
         if (status == config.Config.DONE) { // Task successfully finishes
            def start = new DateTime()
            ar.status = 0
            try { 
               output = util.Util.executegetoutput("${globushome}/bin/${config.Config.globusjobgetoutput} ${url}",true,util.Util.st2millis(util.Util.getproperty(config.Config.globusjobgetoutputMAXTIME)))
               // Updating record values
               task.output = output.substring(0,255)
            } catch(Exception e) {
               output = "Time exhausted - No Output (${status})"
            }
            def stop = new DateTime()
            mjdm.getoutputtime = stop.getMillis()
            keys += [config.Config.UNSUBMITTED, config.Config.PENDING, config.Config.ACTIVE, "getoutputtime"]
         } else { // status == config.Config.FAILED
               keys += util.grid.Util.previousstates(previousstatus)
               keys += previousstatus
               ar.status = -1
               def rc = Resourcecharacteristics.findByGridresource(ar.gridresource)
               ar.tasksassigned = rc.inuse
         }
         keys += status
         report = "${status} \n${util.Util.createreport(mjdm as HashMap,keys)} \n${output}"
         util.Util.writelog(report,"${mjdm.server}/${cdt.toLocalDate()}/${mjdm.function}-${cdt.getMillis()}.log")

         println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Sending notification via e-mail"
         def contacts = Contactinfo.list() 
         contacts.each { contact ->
            mailService.sendMail {
               from "gridjobs@gmail.com"
               to contact.email
               subject "Report from gridjobs framework - ${submittedtime}"
               body report
            }
         }
         print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Updating 'Task' record with id ${task.submittedtime}... "
         if (task.save() != null) {
            println "saved"
         } else {
            println " DIDN'T save"
         }
         print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Updating 'Accountingresource' record with id ${ar.initialtime}... "
         if (ar.save() != null) {
            println "saved"
         } else {
            println " DIDN'T save"
         }
         // Report about how good the estimation was
         println "[GlobusjobstatusJob - execute - ${submittedtime}] Releasing the resource on server ${server}"
         resourcemanagerService.releasenode(server)
         cancelclean(context)
         println "[GlobusjobstatusJob - execute ${util.joda.Util.datetime()}][${server}/${mjdm.parameters} - ${submittedtime}] Task DONE"
         return 
      } else { // The job still runs
         println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Creating a new trigger"
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
         trigger.jobDataMap.minimumthreshold = seconds
         trigger.jobDataMap."${status}" = mjdm."${status}" 
         trigger.jobDataMap.submittedtime = submittedtime
         if (status == config.Config.PENDING) {
            trigger.jobDataMap."${config.Config.UNSUBMITTED}" = mjdm."${config.Config.UNSUBMITTED}"
         } else if (status == config.Config.ACTIVE) {
            trigger.jobDataMap."${config.Config.UNSUBMITTED}" = mjdm."${config.Config.UNSUBMITTED}"
            trigger.jobDataMap."${config.Config.PENDING}" = mjdm."${config.Config.PENDING}"
         }
         trigger.jobDataMap.url = mjdm.url

         if (mjdm.estimates != null) {
            println "[GlobusjobstatusJob - ${server}/${cdt.toLocalTime()} - ${submittedtime}] transfering estimates"
            trigger.jobDataMap.estimates = mjdm.estimates
         }

         if (!scheduleJobWithOldTriggerName(context,trigger)) { // Scheduling failed
            task.exitstatus = config.Config.FAILED
            task.output = "Failing scheduling a new quartz trigger"
            print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Updating 'Task' record on failure with id: ${task.submittedtime}... "
            if (task.save() != null) {
               println "saved"
            } else {
               println "DIDN'T save"
            }
            println "[GlobusjobstatusJob - execute - ${submittedtime}] Releasing the resource on server ${server}"
            resourcemanagerService.releasenode(server)
            cancelclean(context)
            println "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Scheduling failed "
            println "[GlobusjobstatusJob - execute ${util.joda.Util.datetime()}]\t[${server}/${mjdm.parameters} - ${submittedtime}] Exit with ERROR"
            return
         }
         //
         // Saving the record from data defined above
         //
         task.nextvisit = trigger.getNextFireTime()
         print "[GlobusjobstatusJob - execute]\t[${server}/${mjdm.parameters} - ${submittedtime}] Updating 'Task' record with id: ${task.submittedtime}... "
         if (task.save() != null) {
            println "saved"
         } else {
            println " DIDN'T save"
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

   void cancelclean(JobExecutionContext context) {
      def mjdm = context.getMergedJobDataMap()
      def server = mjdm.server
      def url = mjdm.url
      def output = ""

      util.quartz.Util.removetrigger(context)
      output = "Cancel "
      output += util.Util.executegetoutput("${globushome}/bin/${config.Config.globusjobcancel} -q ${url}",true)
      output += "\nClean "
      output += util.Util.executegetoutput("${globushome}/bin/${config.Config.globusjobclean} -q ${url}",true)

      println "[GlobusjobstatusJob - cancelclean - ${mjdm.submittedtime}] Output: ${output}"
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
      def minimumthreshold = trigger.jobDataMap.minimumthreshold 

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
