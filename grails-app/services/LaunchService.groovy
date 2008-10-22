import org.quartz.Trigger
import org.quartz.CronTrigger
import org.quartz.CronExpression
import org.joda.time.DateTime

class LaunchService 
implements remote.Globusjob {
    boolean transactional = false
    static expose = ['hessian']
    def quartzScheduler

    //def jobname = "GlobusjobinstanceJob"
    def jobname = config.Config.jobname
    //def jobnameprefix = "my-id"
    def jobnameprefix = config.Config.jobnameprefix
    //def jobgroup = "Globusjobinstancegroup"
    def jobgroup = config.Config.jobgroup
    //def jobgroupprefix = "my-group"
    def jobgroupprefix = config.Config.jobgroupprefix
    //def remotecommand = "remotecommand"
    def remotecommand = config.Config.remotecommand
    //def function = "function"
    def function = config.Config.function

   String execute(String server, String jobmanager, String parameters, String when, boolean lock, boolean sync) {
      def cronexpression = util.quartz.Util.createcronexpression(when)
      def cdt = new DateTime().getMillis()

      Trigger trigger = new CronTrigger("${jobnameprefix}_${server}-${cdt}","${jobgroupprefix}_${server}-${cdt}",cronexpression)
      trigger.setJobName(jobname)
      trigger.setJobGroup(jobgroup)
      trigger.jobDataMap.server = server
      trigger.jobDataMap.jobmanager = jobmanager
      trigger.jobDataMap.function = util.Util.getvalue(function,parameters)
      //println util.Util.getvalue(function,parameters)
      //println trigger.jobDataMap.function
      trigger.jobDataMap.parameters = util.Util.getvalue(remotecommand,parameters)
      //println trigger.jobDataMap.parameters
      trigger.jobDataMap.crnexpr = when
      trigger.jobDataMap.cronexpression = cronexpression
      trigger.jobDataMap.lock = lock
      trigger.jobDataMap.sync = sync

      quartzScheduler.scheduleJob(trigger)
   }

   String execute(String server, String appname, String parameters, String executionname) {
      // Before to invoke the quartz job it's necessary collect some data from the DB
      def gr = Gridresource.findByName(server)
      def batchscheduler = gr.batchscheduler
      def userhome = gr.userhome

      def criteria = Application.createCriteria()
      def results = criteria.list {
         and {
            like('name',appname)
            gridresource {
                  like('name',server)
            }
         }
      }
      def installationpath = results.get(0).installationpath
      def when = "NOW+2s"
      def lock = false
      def sync = false
      def newparameters = "remotecommand:${userhome}/${installationpath}/${appname} ${parameters},function:${executionname}"
      println "params ${newparameters} ${server} ${batchscheduler} ${executionname}"
      execute(server, batchscheduler, newparameters, when, lock, sync)
   }

   String execute(String server, String jobmanager, String parameters, String when, boolean lock, boolean sync, Map estimates) {
      def cronexpression = util.quartz.Util.createcronexpression(when)
      def cdt = new DateTime().getMillis()

      Trigger trigger = new CronTrigger("${jobnameprefix}_${server}-${cdt}","${jobgroupprefix}_${server}-${cdt}",cronexpression)
      trigger.setJobName(jobname)
      trigger.setJobGroup(jobgroup)
      trigger.jobDataMap.server = server
      trigger.jobDataMap.jobmanager = jobmanager
      trigger.jobDataMap.function = util.Util.getvalue(function,parameters)
      trigger.jobDataMap.parameters = util.Util.getvalue(remotecommand,parameters)
      trigger.jobDataMap.crnexpr = when
      trigger.jobDataMap.cronexpression = cronexpression
      trigger.jobDataMap.lock = lock
      trigger.jobDataMap.sync = sync
      trigger.jobDataMap.estimates = estimates

      quartzScheduler.scheduleJob(trigger)
   }


}
