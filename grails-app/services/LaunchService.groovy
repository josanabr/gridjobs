import org.quartz.Trigger
import org.quartz.CronTrigger
import org.quartz.CronExpression
import org.joda.time.DateTime

class LaunchService 
implements remote.Globusjob {
    boolean transactional = false
    static expose = ['hessian']
    def quartzScheduler
    def resourcemanagerService

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
      println "[LaunchService - execute ${util.joda.Util.datetime()}] ${server} ${jobmanager} ${parameters} SYNC? ${sync}"
      def cronexpression = util.quartz.Util.createcronexpression(when)


      Trigger trigger = new CronTrigger()
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

      def cdt = new DateTime().getMillis()
      trigger.jobDataMap.submittedtime = cdt

      // Save the state of the task in the domain
      def gr = Gridresource.findByName(server)
      def task = new Task(submittedtime: cdt, gridresource: gr, 
                          parameters: trigger.jobDataMap.parameters,
                          function: trigger.jobDataMap.function)
      if (task.save() == null) {
         println "[LaunchService - ] Problems saving a 'Task' instance (gridresource: ${gr}, parameters: ${trigger.jobDataMap.parameters}, function: ${trigger.jobDataMap.function})"
      }
      def ar = new Accountingresource(gridresource: gr, initialtime: cdt, status: 0)
      if (ar.save() == null) {
         println "[LaunchService - execute] Problems saving a 'Accountingresource' instance (gridresource: ${gr}, initialtime: ${cdt})"
         ar.errors.allErrors.each {
            println "[LaunchService - execute] \t ${it}"
         }
      }


      trigger.setName("${jobnameprefix}_${server}-${cdt}")
      trigger.setGroup("${jobgroupprefix}_${server}-${cdt}")
      trigger.setCronExpression(cronexpression)

      println "[LaunchService - execute] Scheduling trigger ${trigger.name}/${trigger.group}"
      quartzScheduler.scheduleJob(trigger)
      resourcemanagerService.allocatenode(server)
      println "[LaunchService - execute ${util.joda.Util.datetime()}] trigger ${trigger.name}/${trigger.group}"

      return "${cdt}"
   }

   String execute(String server, String appname, String parameters, String executionname) {
      println "[LaunchService - execute ${util.joda.Util.datetime()}] ${server} ${appname} ${parameters} ${executionname}"
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
      println "[LaunchService - execute] params ${newparameters} ${server} ${batchscheduler} ${executionname}"
      def returnedstring = execute(server, batchscheduler, newparameters, when, lock, sync)
      println "[LaunchService - execute ${util.joda.Util.datetime()}] ${server} ${appname} ${parameters} ${executionname} DONE!"
      return returnedstring
   }

   // Function on-hold

   String execute(String server, String jobmanager, String parameters, String when, boolean lock, boolean sync, Map estimates) {
      println "[LaunchService - execute ${util.joda.Util.datetime()}] ${server} ${jobmanager} ${parameters} SYNC? ${sync} with Map"
      def cronexpression = util.quartz.Util.createcronexpression(when)

      //Trigger trigger = new CronTrigger("${jobnameprefix}_${server}-${cdt}","${jobgroupprefix}_${server}-${cdt}",cronexpression)
      Trigger trigger = new CronTrigger()
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

      def cdt = new DateTime().getMillis()
      trigger.setName("${jobnameprefix}_${server}-${cdt}")
      trigger.setGroup("${jobgroupprefix}_${server}-${cdt}")
      trigger.setCronExpression(cronexpression)

      println "[LaunchService - execute] Scheduling trigger ${trigger.name}/${trigger.group} with Map"
      quartzScheduler.scheduleJob(trigger)
      println "[LaunchService - execute ${util.joda.Util.datetime()}] trigger ${trigger.name}/${trigger.group} with Map DONE"
   }
}
