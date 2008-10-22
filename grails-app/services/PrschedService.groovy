import org.joda.time.DateTime
import org.joda.time.Period

class PrschedService 
implements remote.Scheduler
{

    boolean transactional = true
    static expose = ['hessian']
    def launchService 

    Object executeTask(String parameters, String when, boolean lock, boolean sync) {
       def totaltimest = "totaltime"
       def configfile = "config.xml"
       def scriptsuffix = "-gen.R"
       def gsl = Gridresource.list()
       def xmlreader = new XmlParser().parse(new File(configfile))
       def states = []
       def _keyset = null
       def scripthome = xmlreader.scripthome[0].'@value'
       def hnselected = ""
       def minvalue = Long.MAX_VALUE
       def startmilli = new DateTime()

       xmlreader.states[0].value.each { state ->
          states << state.text().toLowerCase()
       }
       def estimateperhn = [:] // this variable will contain a hashmap whose key is a headnode
                          // and its value is a hashmap containing estimates for unsubmitted
                          // pending and active stages.
       gsl.each { gs ->
         def hn = gs.headnode // estimating time for every headnode
         if (hn == "komolongma.ece.uprm.edu") {
            return // this is temporary, because there were changes on komolongma application
                   // then it has a faster application version
         }
         def estimateperst = [:]
         states.each { state -> // finding the time given the headnode and state
            def _rscriptname = "${scripthome}/${hn}.${state}${scriptsuffix}"
            def output = ""
            try {
               output = util.Util.executegetoutput(_rscriptname).split(" ")[1]
            } catch (Exception e) {
               println "[PrschedService] Problems accessing output of script ${_rscriptname}"
               return
            }
            estimateperst[state] = (long)(Double.parseDouble(output))
         }
         // Calculating the totaltime
         def totaltime = 0
         _keyset = estimateperst.keySet() 
         _keyset.each {
            totaltime += estimateperst[it]
         }
         estimateperst[totaltimest] = totaltime
         // Assigning to every head node its corresponding map list of estimated times 
         estimateperhn[hn] = estimateperst
       }
       // Choosing the resource which has lowest estimated execution time
       _keyset = estimateperhn.keySet() 
       minvalue = Long.MAX_VALUE
       hnselected 
       _keyset.each { _hn ->
         def _estimateperst = estimateperhn[_hn]
         if (minvalue > _estimateperst[totaltimest]) {
            hnselected = _hn
            minvalue =  _estimateperst[totaltimest]
         }
       }

       println "[PrschedService] Selected: ${hnselected} estimated time: ${minvalue}"
       def endmilli = new DateTime()
       def period  = new Period(endmilli.millis - startmilli.millis)
       println "[PrschedService] Scheduler elapsed time: ${period.hours} h ${period.minutes} m ${period.seconds} s ${period.millis} ms"
    }
}
