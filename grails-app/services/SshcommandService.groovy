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
       def cdt = new DateTime()
       def cronexpression = util.quartz.Util.createcronexpression(when)
       def triggername = "${config.Config.jobnameprefix}_${server}-${cdt}"
       def triggergroup = "${config.Config.jobgroupprefix}_${server}-${cdt}"
       def jobname = config.Config.sshjobname
       def jobgroup = config.Config.sshjobgroup

       Trigger trigger = new CronTrigger(triggername,triggergroup,cronexpression)
       trigger.setJobName(jobname)
       trigger.setJobGroup(jobgroup)
       trigger.jobDataMap.user = user
       trigger.jobDataMap.server = server
       trigger.jobDataMap.crnexpr = when
       trigger.jobDataMap.command = command

       quartzScheduler.scheduleJob(trigger)
    }
}
