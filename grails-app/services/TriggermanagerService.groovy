import org.quartz.Trigger
import org.quartz.JobExecutionContext
import org.quartz.JobDetail
import org.quartz.Job

class TriggermanagerService implements remote.TriggerManager {

    boolean transactional = false
    static expose = ['hessian']
    def quartzScheduler

    String triggerls() {
       println "[TriggermanagerService - triggerls ${util.joda.Util.datetime()}]"
       def triggergroupnames = quartzScheduler.triggerGroupNames
       def result = ""
       triggergroupnames.each { _triggergn ->
          result += "\nGroup ${_triggergn}"
          def triggernames = quartzScheduler.getTriggerNames(_triggergn)
          triggernames.each { _triggern ->
             result += "\n\tName: ${_triggern}"
          }
       }
       println "[TriggermanagerService - triggerls ${util.joda.Util.datetime()}]"
       return result + "\n"
    }
    boolean triggerrm(String name, String group) {
       quartzScheduler.unscheduleJob(name,group)
    }
    String triggerstate(String name,String group) {
       println "[TriggermanagerService - triggerstate ${util.joda.Util.datetime()}] ${name}/${group}"
       switch (quartzScheduler.getTriggerState(name,group)) {
          case Trigger.STATE_NORMAL: return "NORMAL"
          case Trigger.STATE_PAUSED: return "PAUSED"
          case Trigger.STATE_COMPLETE: return "COMPLETE"
          case Trigger.STATE_ERROR: return "ERROR"
          case Trigger.STATE_BLOCKED: return "BLOCKED"
          case Trigger.STATE_NONE: return "NONE"
       }
       println "[TriggermanagerService - triggerstate ${util.joda.Util.datetime()}] ${name}/${group} DONE"
       return "UNKNOWN STATE"
    }

    String jobls(String options) {
       println "[TriggermanagerService - jobls ${util.joda.Util.datetime()}] Options: ${options}"
       def arrayoptions = []
       if (options != "") 
          arrayoptions = options.split(",")
       def result = ""
       // jeclst stands for Job Execution Context List
       def jeclst = quartzScheduler.getCurrentlyExecutingJobs()
       println "[TriggermanagerService - jobls]\t Number of contexts ${jeclst.size()}"
       jeclst.each { jec ->
         def _jd = jec.getJobDetail()
         def _t = jec.getTrigger()
         result += "\nJob ${_jd.name}/${_jd.group}"
         def _jdm = jec.jobDataMap 
         def keys = _jdm.getKeys()
         result += "\nJobDataMap"
         keys.each { _key ->
            if (arrayoptions.size() != 0) {
               if ( (arrayoptions as List).contains(_key)) {
                  result += "\n\t[${_key}] -> ${_jdm[_key]}" 
               }
            } else  {
               result += "\n\t[${_key}] -> ${_jdm[_key]}" 
            }
         }
         // RT stands for Running Time
         result += "\nRT ${jec.jobRunTime}"
         result += "\nTrigger ${_t.name}/${_t.group}"
         // ST stands for Start Time
         // NFT stands for Next Fire Time
         result += "\n\tST: ${_t.startTime} NFT: ${_t.nextFireTime}"
         result += "\n\tState ${triggerstate(_t.name,_t.group)}"
         result += "\n"
       }

       println "[TriggermanagerService - jobls ${util.joda.Util.datetime()}] Options: ${options} DONE"
       return result + "\n"
    }

   // I assume that the parameters have the following layout
   // hola:mundo, demo:tierra
    String jobrm(String name,String group, String parameters) {
       println "[TriggermanagerService - jobrm ${util.joda.Util.datetime()}] ${name}/${group} ${parameters}"
       def params = []
       if (parameters != "") 
          params = parameters.split(",") 
       // Now params has:
       // params[0] = "hola:mundo"
       // params[1] = "demo:tierra"
       def hashparams = [:]
       // Now, a hashmap is created from the data in the
       // params array
       params.each { _param ->
         def _tmparray = _param.split(":")
         if (_tmparray.size() == 2) 
            hashparams[_tmparray[0]] = _tmparray[1]
       }
       // Then hasparams will contain:
       // hashparams[hola] = "mundo"
       // hashparams[demo] = "tierra"
       def result = ""
       def jeclst = quartzScheduler.getCurrentlyExecutingJobs()
       jeclst.each { jec ->
         def _jdm = jec.jobDataMap
         // The job keys
         def keys = _jdm.getKeys() 
         // The hashparams keys
         def hpkeys = hashparams.keySet()
         // I assume that this job must be deleted
         def flag = true
         hpkeys.each { _hpk ->
            if (!(keys as List).contains(_hpk)) {
               // If _jdm has not the _hpk
               // this job would not be deleted
               flag = false
            } else {
               // The key is found
               if (hashparams[_hpk] != _jdm[_hpk]) {
                  // But the value  doesn't correspond
                  flag = false
               }
            }
         }
         if (flag) 
            jec.job.interrupt()
       }
       println "[TriggermanagerService - jobrm ${util.joda.Util.datetime()}] ${name}/${group} ${parameters} DONE"
    }
}
