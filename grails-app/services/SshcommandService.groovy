import org.joda.time.DateTime
import org.quartz.CronTrigger
import org.quartz.Trigger

class SshcommandService 
implements remote.Sshjob
{
    boolean transactional = false
    static expose = ['hessian']
    def quartzScheduler

    public String sshcommand(String user, String server, String command, String when) {
       println "[SshcommandService - sshcommand ${util.joda.Util.datetime()}] ${user}@${server} ${command}"
       def jobname = config.Config.sshjobname
       def jobgroup = config.Config.sshjobgroup

       //Trigger trigger = new CronTrigger(triggername,triggergroup,cronexpression)
       Trigger trigger = new CronTrigger()
       trigger.setJobName(jobname)
       trigger.setJobGroup(jobgroup)
       trigger.jobDataMap.user = user
       trigger.jobDataMap.server = server
       trigger.jobDataMap.crnexpr = when
       trigger.jobDataMap.command = command

       def cdt = new DateTime()
       def triggername = "${config.Config.jobnameprefix}_${server}-${cdt}"
       def triggergroup = "${config.Config.jobgroupprefix}_${server}-${cdt}"
       trigger.name = triggername
       trigger.group = triggergroup
       trigger.cronExpression =  util.quartz.Util.createcronexpression(when)

       println "[SshcommandService - sshcommand] Scheduling trigger ${trigger.name}/${trigger.group}"
       quartzScheduler.scheduleJob(trigger)
       println "[SshcommandService - sshcommand ${util.joda.Util.datetime()}] ${user}@${server} ${command} DONE"
    }
}
