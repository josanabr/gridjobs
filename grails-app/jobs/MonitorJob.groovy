import org.joda.time.DateTime

/**

Periodic monitoring over the resources is carry out due to possible suddenly changes over the resource status. 

*/

class MonitorJob {
    def timeout = 3600000l // execute job once in one hour
    def monitorService


    def execute() {
       println "[MonitorJob - execute ${util.joda.Util.datetime()}]" 
       monitorService.monitorgridresources()
    }
}
