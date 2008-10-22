import org.quartz.JobDataMap
import org.joda.time.DateTime
import org.quartz.CronExpression

class SshcommandJob {
   static triggers = {}
   def quartzScheduler 
   def group = config.Config.sshjobgroup

    def execute(context) {
       def mjdm = context.getMergedJobDataMap()
       def server = mjdm.server
       def user = mjdm.user
       def command = mjdm.command
       def crnexpr = mjdm.crnexpr 

      def output  = ""
      if (System.getProperty('user.name') == user  && server == InetAddress.getLocalHost().getHostName()) {
         output = util.Util.executegetoutput(command)
         println "[ ${new DateTime().toLocalTime()}/${server} ] Local ${command} -> \n\t ${output} \nEnd of Output"
      } else  { 
         output = util.jsch.Util.execremotecommand(user,server,command) 
         println "[ ${new DateTime().toLocalTime()}/${server} ] ${command} -> \n\t ${output} \nEnd of Output"
      }

      if (CronExpression.isValidExpression(crnexpr)) 
          println "[${new DateTime().toLocalTime()}/${server}] => It will be launched again: ${context.trigger.nextFireTime}"
      else {
         util.quartz.Util.removetrigger(context)
         println "[${new DateTime().toLocalTime()}/${server}] Trigger unscheduled!"
      }
    }
}
