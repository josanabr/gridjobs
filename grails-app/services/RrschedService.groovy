import org.joda.time.DateTime

class RrschedService 
implements remote.Scheduler
{

    boolean transactional = true
    static expose = ['hessian']
    def launchService

    Object executeTask(String parameters, String when, boolean lock, boolean sync) {
       println "[RrschedService - executeTask ${util.joda.Util.datetime()}] ${parameters} ${when} SYNC? ${sync}"
       // ss contains a pointer to the current resource to be available.
       def ss = Schedulerstatus.get(1) // ss: scheduler status
       def cr = ss.currentresource     // cr: current resource 
       // gs contains the next resource willing to execute the application 
       def gs = Gridresource.get(cr)   // gs: gridresource
       def dt = new DateTime()
       println "[RrschedService - executeTask] Task (params: ${parameters}) send to ${gs.headnode}"
       launchService.execute(gs.headnode, gs.batchscheduler, parameters, when, lock, sync)
       gs = Gridresource.list().size()
       ss.currentresource = (cr % gs) + 1
       ss.save()
       println "[RrschedService - executeTask ${util.joda.Util.datetime()}] ${parameters} ${when} SYNC? ${sync} DONE"

       return true
    }
}
