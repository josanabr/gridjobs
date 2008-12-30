import org.joda.time.DateTime

class RrschedService 
implements remote.Scheduler
{

    boolean transactional = true
    static expose = ['hessian']
    def launchService
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
       def ss = Schedulerstatus.get(1) // ss: scheduler status
       def start = new DateTime()
       def cr = nextgridresource(ss)     // cr: current resource 
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
       def gs = Gridresource.get(cr)   // gs: gridresource
       def dt = new DateTime()
       println "[RrschedService - executeTask] Task (appname: ${appname} execname: ${execname} parameters: ${params}) send to ${gs.headnode}"
       //returnvalue = launchService.execute(gs.headnode, gs.batchscheduler, parameters, when, lock, sync)
       returnvalue = launchService.execute(gs.headnode, appname, params, execname)
       gs = Gridresource.list().size()
       ss.currentresource = (cr % gs) + 1
       ss.save()
       println "[RrschedService - executeTask ${util.joda.Util.datetime()}] ${parameters} ${when} SYNC? ${sync} DONE"

       return returnvalue
    }

    int nextgridresource(Schedulerstatus ss) {
       def cr = ss.currentresource
       def _cr = cr // '_cr' contains the next resource 
                    // in charge of attend the request
       def resourcelistsize = Gridresource.list().size()
       while (true) {
          def rc = Resourcecharacteristics.findByGridresource(cr)
          if (rc.available() > 0)
             break
          else {
             cr = (cr % resourcelistsize) + 1
             if (cr == _cr) { 
                // All the resources were considered 
                // and none was available
                cr = -1                
                break
             }
          }
       }

       return cr
    }
}
