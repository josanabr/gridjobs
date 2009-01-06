import org.joda.time.DateTime

/**

Periodic monitoring over the resources is carry out due to possible suddenly changes over the resource status. 

*/

class MonitorJob {
    //def timeout = 300000l // execute job once in one hour
    def cronExpression = "0 2/5 * * * ?"
    def monitorService


    def execute() {
       println "[MonitorJob - execute ${util.joda.Util.datetime()}]" 
       monitorService.monitorgridresources()
    }
}
