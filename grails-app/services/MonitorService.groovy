import org.joda.time.DateTime

/**

This class offers basic methods for querying the resource status through its methods
- monitorgridresource
- monitorgridresources

*/

class MonitorService {

    boolean transactional = true 
    def tmpdir = "/tmp"

    int monitorgridresource(Gridresource gr) {
      def returnedvalue = 0
      println "[MonitorService - monitorgridresource ${util.joda.Util.datetime()}]" 
      def xmlfilename
      def currenttime = new DateTime()
      try { 
         xmlfilename = util.grid.Util.retrieveandsaveresourcestatus(gr.name,currenttime,tmpdir)
      } catch (Exception e) {
         println "[MonitorService - monitorgridresource] \t Exception retrieving data from ${gr.name}"
         println "[MonitorService - monitorgridresource] \t ${e}"
         returnedvalue++ 
         return
      }
      def xmlreader
      try { 
         xmlreader = new XmlParser().parse(new File(xmlfilename))
      } catch (Exception e) {
         println "[MonitorService - monitorgridresource] \t Exception reading file ${xmlfilename}"
         println "[MonitorService - monitorgridresource] \t ${e}"
         returnedvalue++ 
         return
      }
      def numnodes
      def cpupernode
      def cpuspeed
      try {
         numnodes = xmlreader.cluster[0].host_hn.size()
         cpupernode 
         cpuspeed
         xmlreader.cluster[0].host_hn[0].hardware.each { hw ->
            if (hw.'@name' == "CpuInfo") {
               cpupernode = hw.'@number'
               cpuspeed = hw.'@speeds'
            }
         }
      } catch (Exception e) {
         println "[MonitorService - monitorgridresources] \t Exception reading XML attribute from file ${xmlfilename}"
         println "[MonitorService - monitorgridresources] \t ${e}"
         returnedvalue++ 
         return
      }

      def criteria = Resourcecharacteristics.createCriteria()
      def results = criteria.scroll {
         // http://grails.org/Hibernate+Criteria+Builder
         eqProperty("gridresource","gr")
         order("lastmodified","asc")
      }
      // results has the resources in ascending order
      // I got the most recent
      def rc = results.first()

      if (rc.numnodes != numnodes ||  rc.cpupernode != cpupernode || rc.cpuspeed != cpuspeed) {
         rc.numnodes = numnodes
         rc.cpupernode = cpupernode 
         rc.cpuspeed = cpuspeed
         rc.lastmodified = new DateTime().toDate()
         rc.save()
      }


      util.Util.deleteFile(xmlfilename)

      return returnedvalue
    }

    int monitorgridresources() {
       def returnedvalue = 0
       println "[MonitorService - monitorgridresources ${util.joda.Util.datetime()}]" 
       Gridresource grlist = Gridresource.list()
       grlist.each { gr -> 
         returnedvalue += monitorgridresource(gr)
       }

       return returnedvalue
    }
}
