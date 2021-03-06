import org.joda.time.DateTime

class RrschedService 
implements remote.Scheduler
{

    boolean transactional = true
    static expose = ['hessian']
    def launchService
    def resourcemanagerService 
    String schedulername = "round-robin"

    /**
    WATCH OUT! 'parameters' is a String divided by '|' and it contains three fields 'x|y|z'
    The field 'x' corresponds to the application name.
    The field 'y' is the execution name.
    The field 'z' is a String that contains the application parameters.
    */
    Object executeTask(String parameters, String when, boolean lock, boolean sync) {
       def returnvalue
       def appname = ""
       def execname = ""
       def params = ""
       println "[RrschedService - executeTask ${util.joda.Util.datetime()}] ${parameters} ${when} SYNC? ${sync}"
       try { 
          appname = parameters.split("[|]")[0]
          execname = parameters.split("[|]")[1]
          params = parameters.split("[|]")[2]
       } catch (Exception e) {
          println "[RrschedService - executeTask] Error parsing the 'parameters' String."
          println e
          e.printStackTrace()
          return ""
       }
       // ss contains a pointer to the current resource to be available.
       /* 
       def ss = Schedulerstatus.list().get(0) // ss: scheduler status
       if (ss == null) {
          println "[RrschedService - executeTask] NULL!!!!!!"
          return ""
       } 
       */
       def start = new DateTime()
       // def cr = nextgridresource(ss)     // cr: current resource 
       def cr = nextgridresource()     // cr: current resource 
       def stop = new DateTime()
       if (cr == -1) {
          println "[RrschedService = executeTask] No resources available for executing ${parameters}"
          def schedstatistics = new Schedulerstatistics(schedulername: schedulername, 
                                datetimems: new DateTime().millis, elapsedtimems: stop.millis - start.millis,
                                parameters: parameters, succeeded: false)
          schedstatistics.save()
          return "-1"
       }
       def schedstatistics = new Schedulerstatistics(schedulername: schedulername, 
                             datetimems: new DateTime().millis, elapsedtimems: stop.millis - start.millis,
                             parameters: parameters, succeeded: true)
       schedstatistics.save()
       // gs contains the next resource willing to execute the application 
       def gs = Gridresource.findBySequence(cr)   // gs: gridresource
       def dt = new DateTime()
       println "[RrschedService - executeTask] Task (appname: ${appname} execname: ${execname} parameters: ${params}) send to ${gs.headnode}"
       //returnvalue = launchService.execute(gs.headnode, gs.batchscheduler, parameters, when, lock, sync)
       returnvalue = launchService.execute(gs.headnode, appname, params, execname)
       /*
       gs = Gridresource.list().size()
       ss.currentresource = (cr % gs) + 1
       ss.save()
       */
       println "[RrschedService - executeTask ${util.joda.Util.datetime()}] ${parameters} ${when} SYNC? ${sync} DONE"

       return returnvalue
    }

    // int nextgridresource(Schedulerstatus ss) {
    int nextgridresource() {
       def locksuffix = "rrsched"
       def dirname = "."
       def maxtries = 10
       def counter = 1
       def low = 500
       def high = 1000
       //def ss = Schedulerstatus.list().get(0) // ss: scheduler status
       while (util.Util.lockexists(dirname,locksuffix)) {
          if (counter > maxtries) {
             return -1
          }
          counter++
          def waitingtime = util.Util.random(low,high)
          println "[RrschedService - nextgridresource] Try ${counter} waiting for ${waitingtime} ms"
          Thread.sleep(waitingtime)
       }
       util.Util.createlock(dirname,locksuffix)
       def ss = Schedulerstatus.findBySequence(1,[lock: true])
       if (ss == null) {
          println "[RrschedService - nextgridresource] NULL!!!!!!"
          util.Util.deletelock(dirname,locksuffix)
          return -1
       } 
       def cr = ss.currentresource
       def _cr = cr // '_cr' contains the next resource 
                    // in charge of attend the request
       def resourcelistsize = Gridresource.list().size()
       while (true) {
          def rc = Gridresource.findBySequence(cr)
          def availablenodes = resourcemanagerService.availablenodes(rc)
          println "[RrschedService -  nextgridresource] \t number of available nodes equals ${availablenodes}"
          if (availablenodes > 0)
             break
          else {
             cr = (cr % resourcelistsize) + 1
             if (cr == _cr) { 
                // All the resources were considered 
                // and none was available
                //cr = -1                
                ss.save()
                util.Util.deletelock(dirname,locksuffix)
                return cr
             }
          }
       }

       ss.currentresource = (cr % Gridresource.list().size()) + 1
       ss.save()

       util.Util.deletelock(dirname,locksuffix)
       return cr
    }
}
