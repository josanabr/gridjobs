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
         return returnedvalue
      }
      def xmlreader
      try { 
         xmlreader = new XmlParser().parse(new File(xmlfilename))
      } catch (Exception e) {
         println "[MonitorService - monitorgridresource] \t Exception reading file ${xmlfilename}"
         println "[MonitorService - monitorgridresource] \t ${e}"
         returnedvalue++ 
         return returnedvalue
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
         println "[MonitorService - monitorgridresource] \t Exception reading XML attribute from file ${xmlfilename}"
         println "[MonitorService - monitorgridresource] \t ${e}"
         returnedvalue++ 
         return returnedvalue
      }

      def rc = Resourcecharacteristics.findByGridresource(gr)

      if (rc == null) {
         rc = new Resourcecharacteristics(Gridresource:gr, numnodes: numnodes, cpupernode: cpupernode, cpuspeed: cpuspeed, lastmodified: currenttime.toDate())
      } else {
         rc.numnodes = numnodes
         rc.cpupernode = cpupernode
         rc.cpuspeed = cpuspeed
         lastmodified = currenttime.toDate()
      }

      if (rc.save() == null) {
         println "[MonitorService - monitorgridresource] Saving failed! the data from ${gr.name} grid resource"
         println "[MonitorService - monitorgridresource] \t cpupernode: ${cpupernode} numnodes: ${numnodes} cpuspeed: ${cpuspeed}"
         rc.errors.allErrors.each {
            println it.defaultMessage
         }
      } else {
         println "[MonitorService - monitorgridresource] Saved data of ${gr.name} grid resource"
      }

      /*
      The lines below intend to keep a track about the resource status.
      Due to difficulties found with GORM, this functionality is abolished.
      def criteria = Resourcecharacteristics.createCriteria()
      def results = criteria.scroll {
         // http://grails.org/Hibernate+Criteria+Builder
         eqProperty("Gridresource","gr")
         order("lastmodified","asc")
      }
      def results = Resourcecharacteristics.findAll("from resourcecharacteristics where gridresource_id = ? order by lastmodified asc",[gr.id])
      if (results == null) {
         println "[MonitorService - monitorgridresource] Records not found"
         return -1
      }
      println results.get(1).numnodes
      // results has the resources in ascending order
      // I got the most recent
      def rc = results.get(1)

     

      if ( (rc.numnodes != numnodes) ||  (rc.cpupernode != cpupernode) || (rc.cpuspeed != cpuspeed) ) {
         rc.numnodes = numnodes
         rc.cpupernode = cpupernode 
         rc.cpuspeed = cpuspeed
         rc.lastmodified = new DateTime().toDate()
         rc.save()
      }
      */


      util.Util.deleteFile(xmlfilename)

      return returnedvalue
    }

    int monitorgridresources() {
       def returnedvalue = 0
       println "[MonitorService - monitorgridresources ${util.joda.Util.datetime()}]" 
       def grlist = Gridresource.list()
       grlist.each { gr -> 
         returnedvalue += monitorgridresource(gr)
       }

       return returnedvalue
    }
}
